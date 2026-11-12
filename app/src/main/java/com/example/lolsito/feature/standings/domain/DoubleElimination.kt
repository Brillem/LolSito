package com.example.lolsito.feature.standings.domain

/** A double-elimination section: the upper and lower brackets, each a list of rounds, and the grand final. */
data class DoubleEliminationBracket(
    val upper: List<BracketRound>,
    val lower: List<BracketRound>,
    val grandFinal: BracketRound,
)

/**
 * A section as a standard double-elimination bracket of 4 teams (6 matches) or 8 teams (14 matches), or null
 * when its matches do not fit one. A grand-final rematch (bracket reset) is not supported.
 *
 * The API sends no links between matches and its list order is not play order, so every match is placed from the
 * results alone: upper round 1 holds every team once; each later upper round, the winners of the previous one;
 * lower round 1, the losers of upper round 1; then each lower round alternates between the previous lower winners
 * meeting the losers of the next upper round, and those winners meeting each other; the grand final is the upper
 * winner against the lower winner. A match fits a round only when its known teams are teams that round receives,
 * so a team is out after its second loss. Matches whose teams are not known yet (TBD) fill the rounds still open,
 * and a team the API has not filled in yet may wait as TBD. Upper round 1 must be fully known, and some lower
 * match must name both teams (so the section is known to be double elimination). When more than one placement
 * fits the results (a rematch with the same winner), the one that puts earlier match ids in earlier rounds wins.
 */
fun buildDoubleElimination(section: StandingsSection): DoubleEliminationBracket? {
    if (!section.isMatchOnly) return null
    val teamCount = DOUBLE_ELIMINATION_TEAMS[section.matches.size] ?: return null
    val seeds = section.matches.flatMap { it.realTeams }.map { it.identity }.distinct()
    if (seeds.size != teamCount) return null
    val specs = roundSpecs(teamCount)
    val pool = section.matches.sortedWith(compareBy<SectionMatch> { it.realTeams.isEmpty() }.then(MATCH_ID_ORDER))
    val rounds = Placement(specs, pool, seeds).run() ?: return null
    val lowerRounds = specs.indices.filter { specs[it].side == Side.LOWER }
    if (lowerRounds.none { r -> rounds[r].any { it.hasBothRealTeams() } }) return null

    val upperRounds = specs.indices.filter { specs[it].side == Side.UPPER }
    val upper = orderForDrawing(upperRounds.map(rounds::get))
    val lower = orderForDrawing(lowerRounds.map(rounds::get))
    return DoubleEliminationBracket(
        upper = upper.map { (matches, linked) -> BracketRound(upperRoundName(matches.size), matches, linked) },
        lower = lower.mapIndexed { i, (matches, linked) ->
            BracketRound(if (i == lower.lastIndex) "Lower Final" else "Lower Round ${i + 1}", matches, linked)
        },
        grandFinal = BracketRound("Grand Final", rounds.last(), linkedToNext = false),
    )
}

/** Total matches of a double-elimination bracket mapped to its number of teams. */
private val DOUBLE_ELIMINATION_TEAMS = mapOf(6 to 4, 14 to 8)

/** Ids are numeric and grow with creation; ordering by length first keeps that true for any string. */
private val MATCH_ID_ORDER = compareBy<SectionMatch>({ it.match.id.length }, { it.match.id })

private fun upperRoundName(matches: Int): String = when (matches) {
    4 -> "Upper Quarterfinals"
    2 -> "Upper Semifinals"
    else -> "Upper Final"
}

private enum class Side { UPPER, LOWER, FINAL }

/** Teams a round receives: the winners or the losers of an earlier round (an index into the round specs). */
private data class Feed(val round: Int, val winners: Boolean)

/**
 * One round of the bracket. No feed: the seeds (upper round 1). One feed: its teams meet each other. Two feeds:
 * every match pairs a team of the first with a team of the second.
 */
private data class RoundSpec(val side: Side, val feeds: List<Feed>)

/** Rounds in play order: the upper bracket, then the lower bracket, then the grand final. */
private fun roundSpecs(teams: Int): List<RoundSpec> {
    val upperRounds = Integer.numberOfTrailingZeros(teams)
    val specs = mutableListOf<RoundSpec>()
    repeat(upperRounds) { j -> specs += RoundSpec(Side.UPPER, if (j == 0) emptyList() else listOf(Feed(j - 1, winners = true))) }
    specs += RoundSpec(Side.LOWER, listOf(Feed(0, winners = false)))
    for (j in 1 until upperRounds) {
        specs += RoundSpec(Side.LOWER, listOf(Feed(specs.lastIndex, winners = true), Feed(j, winners = false)))
        if (j < upperRounds - 1) specs += RoundSpec(Side.LOWER, listOf(Feed(specs.lastIndex, winners = true)))
    }
    specs += RoundSpec(Side.FINAL, listOf(Feed(upperRounds - 1, winners = true), Feed(specs.lastIndex, winners = true)))
    return specs
}

/**
 * Backtracking search that places every match of [pool] (known matches first, then by id) into the rounds of
 * [specs]. Within a round, known matches are tried in pool order and preferred over placeholders; placeholders
 * are interchangeable, so only the lowest unused ones are taken.
 */
private class Placement(
    private val specs: List<RoundSpec>,
    private val pool: List<SectionMatch>,
    private val seeds: List<String>,
) {
    private val used = BooleanArray(pool.size)
    private val rounds = mutableListOf<List<SectionMatch>>()

    fun run(): List<List<SectionMatch>>? = if (placeRound(0)) rounds.toList() else null

    private fun placeRound(index: Int): Boolean {
        if (index == specs.size) return true
        val spec = specs[index]
        val groups: List<Set<String>> =
            if (spec.feeds.isEmpty()) listOf(seeds.toSet()) else spec.feeds.map { entrants(it).filterNotNull().toSet() }
        val size = if (spec.feeds.isEmpty()) seeds.size / 2 else if (groups.size == 2) entrants(spec.feeds[0]).size else entrants(spec.feeds[0]).size / 2
        if (groups.size == 2 && entrants(spec.feeds[1]).size != size) return false
        val candidates = pool.indices.filter { !used[it] && pool[it].realTeams.isNotEmpty() && fits(pool[it], groups, seedRound = spec.feeds.isEmpty()) }
        val placeholders = pool.indices.filter { !used[it] && pool[it].realTeams.isEmpty() }
        return choose(index, candidates, 0, mutableListOf(), mutableSetOf(), size, if (spec.feeds.isEmpty()) emptyList() else placeholders)
    }

    /** Depth-first choice of [size] matches for round [index]: known ones from [candidates] first, then placeholders. */
    private fun choose(
        index: Int,
        candidates: List<Int>,
        from: Int,
        chosen: MutableList<Int>,
        teams: MutableSet<String>,
        size: Int,
        placeholders: List<Int>,
    ): Boolean {
        val missing = size - chosen.size
        if (missing == 0 || from == candidates.size) {
            if (missing > placeholders.size) return false
            val picked = chosen + placeholders.take(missing)
            picked.forEach { used[it] = true }
            rounds += picked.map(pool::get)
            if (placeRound(index + 1)) return true
            rounds.removeAt(rounds.lastIndex)
            picked.forEach { used[it] = false }
            return false
        }
        val candidate = candidates[from]
        val ids = pool[candidate].realTeams.map { it.identity }
        if (ids.none { it in teams }) {
            chosen += candidate
            teams += ids
            if (choose(index, candidates, from + 1, chosen, teams, size, placeholders)) return true
            chosen.removeAt(chosen.lastIndex)
            teams -= ids.toSet()
        }
        return choose(index, candidates, from + 1, chosen, teams, size, placeholders)
    }

    /** Known teams of the winners or losers of an already placed round; null where the match is undecided. */
    private fun entrants(feed: Feed): List<String?> =
        rounds[feed.round].map { m -> (if (feed.winners) m.decidedWinner() else m.decidedLoser())?.identity }

    /** The known teams of [match] are teams the round receives, one from each group when it has two. */
    private fun fits(match: SectionMatch, groups: List<Set<String>>, seedRound: Boolean): Boolean {
        val ids = match.realTeams.map { it.identity }
        if (ids.distinct().size != ids.size) return false
        if (seedRound) return ids.size == 2 && ids.all { it in groups[0] }
        if (groups.size == 1) return ids.all { it in groups[0] }
        val (first, second) = groups
        return when (ids.size) {
            2 -> (ids[0] in first && ids[1] in second) || (ids[0] in second && ids[1] in first)
            else -> ids.single() in first || ids.single() in second
        }
    }
}

/**
 * The rounds of one bracket side, each ordered so that the matches feeding match m of the next round sit
 * together (2m and 2m+1 when the next round halves, m when it keeps its size), earlier ids first. A round is
 * linked to the next one only when all its matches could be ordered that way; the rest keep id order.
 */
private fun orderForDrawing(rounds: List<List<SectionMatch>>): List<Pair<List<SectionMatch>, Boolean>> {
    val ordered = rounds.map { it.sortedWith(MATCH_ID_ORDER) }.toMutableList()
    val linked = MutableList(rounds.size) { false }
    for (j in rounds.lastIndex - 1 downTo 0) {
        val previous = ordered[j]
        val byFeed = mutableListOf<SectionMatch>()
        ordered[j + 1].forEach { next ->
            val teams = next.realTeams.map { it.identity }.toSet()
            byFeed += previous.filter { it !in byFeed && it.decidedWinner()?.identity in teams }
        }
        linked[j] = byFeed.size == previous.size
        ordered[j] = byFeed + previous.filter { it !in byFeed }
    }
    return ordered.zip(linked)
}
