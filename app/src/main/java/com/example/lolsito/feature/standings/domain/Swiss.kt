package com.example.lolsito.feature.standings.domain

/** Wins and losses a team had going into a Swiss match. */
data class SwissRecord(val wins: Int, val losses: Int)

/** Matches of one round between teams with the same [record]; a null record holds matches still to be paired (TBD). */
data class SwissGroup(val record: SwissRecord?, val matches: List<SectionMatch>)

/** One Swiss round: its [matches] in list order, and the same matches grouped by record, best record first. */
data class SwissRound(val name: String, val matches: List<SectionMatch>, val groups: List<SwissGroup>)

/**
 * A section as a Swiss stage (a team is out at three wins or three losses), or null when it does not fit one.
 *
 * The API sends neither rounds nor records, so both come from replaying the results. Each team's matches are
 * taken in list order: its round is the number of its earlier matches plus one, and its record is what it won
 * and lost before. A match is placed only when both teams arrive at the same round with the same record, every
 * earlier match of both is decided, and neither has already reached three wins or three losses.
 *
 * The number of known teams fixes the shape: every record group must pair evenly in every round (16 teams give
 * rounds of 8, 8, 8, 6 and 3 matches), and no round or record group may get more matches than the shape allows.
 * Matches still to be paired (a TBD team) wait in the earliest round with room, under no record; rounds the API
 * does not list yet are not drawn. A section with no team known yet stays a list.
 */
fun buildSwiss(section: StandingsSection): List<SwissRound>? {
    if (!section.isMatchOnly) return null
    val (paired, unpaired) = section.matches.indices.partition { section.matches[it].hasBothRealTeams() }
    val teamCount = paired.flatMap { section.matches[it].match.teams.map { t -> t.identity } }.distinct().size
    val shape = swissShape(teamCount) ?: return null
    if (section.matches.size > shape.sumOf { it.values.sum() }) return null

    val placed = replaySwiss(section.matches, paired) ?: return null
    val counts = placed.values.groupingBy { it }.eachCount()
    if (counts.any { (slot, count) -> count > (shape.getOrNull(slot.round)?.get(slot.record) ?: 0) }) return null

    val free = shape.mapIndexed { round, groups -> groups.values.sum() - placed.values.count { it.round == round } }.toMutableList()
    val waiting = mutableMapOf<Int, Int>()
    for (index in unpaired) {
        val round = free.indexOfFirst { it > 0 }
        if (round < 0) return null
        free[round]--
        waiting[index] = round
    }

    val roundOf = placed.mapValues { it.value.round } + waiting
    val lastRound = roundOf.values.maxOrNull() ?: return null
    return (0..lastRound).map { round ->
        val inRound = section.matches.indices.filter { roundOf[it] == round }
        val byRecord = shape[round].keys.mapNotNull { record ->
            inRound.filter { placed[it]?.record == record }.takeIf { it.isNotEmpty() }?.let { SwissGroup(record, it.map(section.matches::get)) }
        }
        val toBePaired = inRound.filter { it in waiting }.takeIf { it.isNotEmpty() }?.let { SwissGroup(null, it.map(section.matches::get)) }
        SwissRound("Round ${round + 1}", inRound.map(section.matches::get), byRecord + listOfNotNull(toBePaired))
    }
}

/** Where a replayed match goes: its round (0-based) and the record both teams had going in. */
private data class SwissSlot(val round: Int, val record: SwissRecord)

/** Slots of the [paired] matches (indices into [matches], in list order), or null when the replay does not fit. */
private fun replaySwiss(matches: List<SectionMatch>, paired: List<Int>): Map<Int, SwissSlot>? {
    class TeamState(var played: Int = 0, var wins: Int = 0, var losses: Int = 0, var undecided: Boolean = false)
    val states = mutableMapOf<String, TeamState>()
    val placed = mutableMapOf<Int, SwissSlot>()
    for (index in paired) {
        val match = matches[index]
        val teams = match.match.teams.take(2)
        val (first, second) = teams.map { states.getOrPut(it.identity) { TeamState() } }
        if (first.undecided || second.undecided) return null
        if (first.played != second.played || first.wins != second.wins || first.losses != second.losses) return null
        if (first.wins >= SWISS_TARGET || first.losses >= SWISS_TARGET) return null
        placed[index] = SwissSlot(first.played, SwissRecord(first.wins, first.losses))
        val winner = match.decidedWinner()?.identity
        teams.zip(listOf(first, second)).forEach { (team, state) ->
            state.played++
            when (winner) {
                null -> state.undecided = true
                team.identity -> state.wins++
                else -> state.losses++
            }
        }
    }
    return placed
}

/** A team is out of a Swiss stage at this many wins, or this many losses. */
private const val SWISS_TARGET = 3

/**
 * Matches per record group in every round of a Swiss of [teams] teams, best record first, or null when some
 * group cannot pair evenly (so it is not a standard Swiss).
 */
private fun swissShape(teams: Int): List<Map<SwissRecord, Int>>? {
    if (teams < 2) return null
    val rounds = mutableListOf<Map<SwissRecord, Int>>()
    var groups = mapOf(SwissRecord(0, 0) to teams)
    while (groups.isNotEmpty()) {
        if (groups.values.any { it % 2 != 0 }) return null
        rounds += groups.mapValues { it.value / 2 }
        val next = mutableMapOf<SwissRecord, Int>()
        groups.forEach { (record, count) ->
            listOf(record.copy(wins = record.wins + 1), record.copy(losses = record.losses + 1))
                .filter { it.wins < SWISS_TARGET && it.losses < SWISS_TARGET }
                .forEach { next[it] = (next[it] ?: 0) + count / 2 }
        }
        groups = next.entries.sortedWith(compareByDescending<Map.Entry<SwissRecord, Int>> { it.key.wins }.thenBy { it.key.losses })
            .associate { it.key to it.value }
    }
    return rounds
}
