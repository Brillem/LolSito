package com.example.lolsito.feature.game.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchupsTest {

    private fun player(id: Int, role: String?) = PlayerStats(id, "P$id", "Olaf", 0, 0, 0, 0, 1, role = role)

    private fun team(vararg players: PlayerStats) = TeamStats(0, 0, 0, 0, 0, players.toList())

    private fun stats(blue: TeamStats, red: TeamStats) = GameStats(isFinished = true, blueTeam = blue, redTeam = red)

    private val roles = listOf("top", "jungle", "mid", "bottom", "support")

    private fun fullTeam(firstId: Int, roleOrder: List<String?> = roles) =
        team(*roleOrder.mapIndexed { i, role -> player(firstId + i, role) }.toTypedArray())

    @Test
    fun `players with the same role face each other in role order`() {
        val matchups = stats(fullTeam(1), fullTeam(6)).matchups()

        assertEquals(
            listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOTTOM, Role.SUPPORT),
            matchups.map { it.role },
        )
        assertEquals(listOf(1, 2, 3, 4, 5), matchups.map { it.blue?.participantId })
        assertEquals(listOf(6, 7, 8, 9, 10), matchups.map { it.red?.participantId })
    }

    @Test
    fun `roles pair players even when a team lists them in another order`() {
        val shuffled = listOf("support", "mid", "top", "bottom", "jungle")
        val matchups = stats(fullTeam(1), fullTeam(6, shuffled)).matchups()

        assertEquals(listOf(1, 2, 3, 4, 5), matchups.map { it.blue?.participantId })
        assertEquals(listOf(8, 10, 7, 9, 6), matchups.map { it.red?.participantId })
    }

    @Test
    fun `role values are matched ignoring case`() {
        val upper = roles.map { it.uppercase() }
        val matchups = stats(fullTeam(1, upper), fullTeam(6)).matchups()

        assertEquals(Role.entries, matchups.map { it.role })
    }

    @Test
    fun `missing roles fall back to participant order`() {
        val noRoles = List(5) { null }
        val matchups = stats(fullTeam(1, noRoles), fullTeam(6, noRoles)).matchups()

        assertEquals(listOf(1, 2, 3, 4, 5), matchups.map { it.blue?.participantId })
        assertEquals(listOf(6, 7, 8, 9, 10), matchups.map { it.red?.participantId })
        assertEquals(Role.entries, matchups.map { it.role })
    }

    @Test
    fun `one team without roles sends both teams to participant order`() {
        val shuffled = listOf("support", "mid", "top", "bottom", "jungle")
        val matchups = stats(fullTeam(1, shuffled), fullTeam(6, List(5) { null })).matchups()

        assertEquals(listOf(1, 2, 3, 4, 5), matchups.map { it.blue?.participantId })
        assertEquals(listOf(6, 7, 8, 9, 10), matchups.map { it.red?.participantId })
    }

    @Test
    fun `a repeated or unknown role falls back to participant order`() {
        val repeated = listOf("top", "top", "mid", "bottom", "support")
        val unknown = listOf("top", "jungle", "mid", "bottom", "coach")
        assertEquals(
            listOf(6, 7, 8, 9, 10),
            stats(fullTeam(1, repeated), fullTeam(6)).matchups().map { it.red?.participantId },
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5),
            stats(fullTeam(1, unknown), fullTeam(6)).matchups().map { it.blue?.participantId },
        )
    }

    @Test
    fun `fewer than five players leave the opponent slot empty`() {
        val blue = team(player(1, "top"), player(3, "mid"))
        val matchups = stats(blue, fullTeam(6)).matchups()

        assertEquals(Role.entries, matchups.map { it.role })
        assertEquals(listOf(1, null, 3, null, null), matchups.map { it.blue?.participantId })
        assertEquals(listOf(6, 7, 8, 9, 10), matchups.map { it.red?.participantId })
    }

    @Test
    fun `fewer than five players without roles pair by order`() {
        val matchups = stats(team(player(1, null), player(2, null)), team(player(6, null))).matchups()

        assertEquals(listOf(1, 2), matchups.map { it.blue?.participantId })
        assertEquals(listOf(6, null), matchups.map { it.red?.participantId })
        assertEquals(listOf(Role.TOP, Role.JUNGLE), matchups.map { it.role })
    }

    @Test
    fun `players beyond the fifth have no role label`() {
        val six = team(*(1..6).map { player(it, null) }.toTypedArray())
        val matchups = stats(six, team()).matchups()

        assertEquals(6, matchups.size)
        assertNull(matchups.last().role)
    }

    @Test
    fun `no players means no matchups`() {
        assertEquals(emptyList<Matchup>(), stats(team(), team()).matchups())
    }

    @Test
    fun `matchup keys are unique and follow the role, not the participant`() {
        val roleKeys = stats(fullTeam(1), fullTeam(6)).matchups().map { it.key }
        val swappedIds = stats(fullTeam(11), fullTeam(16)).matchups().map { it.key }

        assertEquals(5, roleKeys.toSet().size)
        assertEquals(roleKeys, swappedIds)
    }
}
