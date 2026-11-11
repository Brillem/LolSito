package com.example.lolsito.feature.game.domain

/** The five lanes in the order a team lists them, top to support. */
enum class Role(val feedValue: String, val label: String) {
    TOP("top", "Top"),
    JUNGLE("jungle", "Jungle"),
    MID("mid", "Mid"),
    BOTTOM("bottom", "Bottom"),
    SUPPORT("support", "Support"),
}

/**
 * One row of the comparison: the players facing each other, either of which is null when its team has
 * nobody for the slot. [key] identifies the row across refreshes and is unique within a game.
 */
data class Matchup(val key: String, val role: Role?, val blue: PlayerStats?, val red: PlayerStats?)

/**
 * The players paired lane by lane, in lane order. Pairing uses the roles from the feed when every
 * player of both teams has a known role and no team repeats one; otherwise it falls back to
 * participant order, where the n-th player of each team face each other (the feed lists lanes top to support).
 */
fun GameStats.matchups(): List<Matchup> {
    val blueByRole = blueTeam.players.byRole()
    val redByRole = redTeam.players.byRole()
    if (blueByRole != null && redByRole != null) {
        return Role.entries
            .filter { it in blueByRole || it in redByRole }
            .map { Matchup("role:${it.name}", it, blueByRole[it], redByRole[it]) }
    }
    val rows = maxOf(blueTeam.players.size, redTeam.players.size)
    return (0 until rows).map { i ->
        Matchup("slot:$i", Role.entries.getOrNull(i), blueTeam.players.getOrNull(i), redTeam.players.getOrNull(i))
    }
}

/** Players by lane, or null when one has no known role or two share one. */
private fun List<PlayerStats>.byRole(): Map<Role, PlayerStats>? {
    val byRole = associateBy { player -> Role.entries.firstOrNull { it.feedValue.equals(player.role, ignoreCase = true) } }
    if (byRole.size != size || null in byRole) return null
    return byRole.mapKeys { it.key!! }
}
