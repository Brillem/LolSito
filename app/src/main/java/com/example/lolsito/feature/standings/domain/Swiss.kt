package com.example.lolsito.feature.standings.domain

/** Wins and losses a team had going into a Swiss match. */
data class SwissRecord(val wins: Int, val losses: Int)

/** Matches of one round between teams with the same [record]; a null record holds matches still to be paired (TBD), or every match of a round without records. */
data class SwissGroup(val record: SwissRecord?, val matches: List<SectionMatch>)

/** One Swiss round: its [matches] in API order, and the same matches grouped by record, best record first. */
data class SwissRound(val name: String, val matches: List<SectionMatch>, val groups: List<SwissGroup>)

/**
 * A section laid out as rounds, or null when it is not one: it must have columns and no match may feed
 * another (no [WinnerLink]), as in a Swiss stage. Each column is a round, named after its cells.
 *
 * The API sends no records, so they come from replaying the results round by round (a team is out at three
 * wins or three losses). A match between two known teams goes under the record both had going in; that needs
 * both to have played every earlier round, all of it decided, with the same record, and neither already out.
 * Matches still to be paired (a TBD team) wait under no record at the end of their round.
 *
 * The number of known teams fixes the shape: every record group must pair evenly in every round (16 teams give
 * rounds of 8, 8, 8, 6 and 3 matches), and no round or record group may get more matches than the shape allows.
 * When any of this fails, or no team is known yet, every round shows its matches under no record.
 */
fun buildSwiss(section: StandingsSection): List<SwissRound>? {
    if (!section.isMatchOnly || section.columns.isEmpty() || section.winnerLinks.isNotEmpty()) return null
    val rounds = section.columns.map { column -> column.cells.flatMap { it.matches } }
    val groups = swissGroups(rounds)
    return section.columns.mapIndexed { index, column ->
        SwissRound(
            name = column.cells.joinToString(" / ") { it.name },
            matches = rounds[index],
            groups = groups?.get(index) ?: listOf(SwissGroup(null, rounds[index])),
        )
    }
}

/** The groups of every round of [rounds], or null when the records cannot be worked out. */
private fun swissGroups(rounds: List<List<SectionMatch>>): List<List<SwissGroup>>? {
    val teamCount = rounds.flatten().filter { it.hasBothRealTeams() }.flatMap { m -> m.match.teams.take(2).map { it.identity } }.distinct().size
    val shape = swissShape(teamCount) ?: return null
    if (rounds.size > shape.size) return null

    class TeamState(var played: Int = 0, var wins: Int = 0, var losses: Int = 0, var undecided: Boolean = false)
    val states = mutableMapOf<String, TeamState>()
    return rounds.mapIndexed { round, matches ->
        if (matches.size > shape[round].values.sum()) return null
        val (paired, unpaired) = matches.partition { it.hasBothRealTeams() }
        val records = paired.map { match ->
            val teams = match.match.teams.take(2)
            val (first, second) = teams.map { states.getOrPut(it.identity) { TeamState() } }
            if (first.undecided || second.undecided) return null
            if (first.played != round || second.played != round) return null
            if (first.wins != second.wins || first.losses != second.losses) return null
            if (first.wins >= SWISS_TARGET || first.losses >= SWISS_TARGET) return null
            val record = SwissRecord(first.wins, first.losses)
            val winner = match.decidedWinner()?.identity
            teams.zip(listOf(first, second)).forEach { (team, state) ->
                state.played++
                when (winner) {
                    null -> state.undecided = true
                    team.identity -> state.wins++
                    else -> state.losses++
                }
            }
            record
        }
        val counts = records.groupingBy { it }.eachCount()
        if (counts.any { (record, count) -> count > (shape[round][record] ?: 0) }) return null
        val byRecord = shape[round].keys.mapNotNull { record ->
            paired.filterIndexed { i, _ -> records[i] == record }.takeIf { it.isNotEmpty() }?.let { SwissGroup(record, it) }
        }
        byRecord + listOfNotNull(unpaired.takeIf { it.isNotEmpty() }?.let { SwissGroup(null, it) })
    }
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
