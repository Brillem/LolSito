package com.example.lolsito.feature.standings.data

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.formatMatchResult
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.core.network.dto.TournamentsResponse
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StandingsMappersTest {

    private fun tournaments(json: String) = LolEsportsJson.decodeFromString<TournamentsResponse>(json).toDomainTournaments()

    private fun standings(json: String) = LolEsportsJson.decodeFromString<StandingsResponse>(json).toDomainStandings()

    @Test
    fun `worlds tournaments map in API order with parsed dates`() {
        val mapped = tournaments(Fixtures.read("getTournamentsForLeague_worlds.json"))

        assertEquals(11, mapped.size)
        val first = mapped.first()
        assertEquals("115660540725177488", first.id)
        assertEquals("worlds_2026", first.slug)
        assertEquals(LocalDate.of(2026, 10, 15), first.startDate)
        assertEquals(LocalDate.of(2026, 11, 15), first.endDate)
        assertEquals("worlds_2011", mapped.last().slug)
    }

    @Test
    fun `tournaments drop blank ids and keep unparseable dates as null`() {
        val mapped = tournaments(
            """{"data":{"leagues":[{"tournaments":[
                {"id":"1","slug":"a","startDate":"not-a-date"},
                {"id":"","slug":"blank"},
                {"slug":"missing"}
            ]}]}}"""
        )
        assertEquals(listOf("1"), mapped.map { it.id })
        assertNull(mapped.single().startDate)
        assertNull(mapped.single().endDate)
    }

    @Test
    fun `missing data maps to empty lists`() {
        assertTrue(tournaments("{}").isEmpty())
        assertTrue(standings("{}").stages.isEmpty())
    }

    @Test
    fun `lck standings map ranked group sections`() {
        val mapped = standings(Fixtures.read("getStandings_lck.json"))

        assertEquals(listOf("Groups", "Play-Ins", "Regional Championship"), mapped.stages.map { it.name })
        val groups = mapped.stages.first()
        assertFalse(groups.isElimination)
        assertEquals(listOf("Legend Group", "Rise Group"), groups.sections.map { it.name })

        val legend = groups.sections.first()
        assertEquals(5, legend.rankings.size)
        assertEquals(listOf(1, 2, 3, 4, 5), legend.rankings.map { it.ordinal })
        val gen = legend.rankings.first()
        assertEquals("GEN", gen.team.code)
        assertEquals("100205573495116443", gen.team.id)
        assertEquals(5, gen.wins)
        assertEquals(3, gen.losses)
        assertEquals("https://static.lolesports.com/teams/1773829250929_GENGLOGO_GOLD.png", gen.team.imageUrl)
        val dk = legend.rankings.single { it.team.code == "DK" }
        assertEquals(4, dk.ordinal)
        assertEquals(6, dk.wins)
        assertEquals(2, dk.losses)
        assertEquals(20, legend.matches.size)
    }

    @Test
    fun `lck bracket stages keep their matches with results`() {
        val playIns = standings(Fixtures.read("getStandings_lck.json")).stages[1]

        assertTrue(playIns.isElimination)
        val matches = playIns.sections.single().matches
        assertEquals(3, matches.size)
        val first = matches.first()
        assertEquals("117030752644841571", first.match.id)
        assertEquals(MatchState.COMPLETED, first.state)
        assertEquals(listOf("BRO", "KT"), first.match.teams.map { it.code })
        assertTrue(first.match.teams.all { it.imageUrl!!.startsWith("https://") })
        val result = formatMatchResult(first.state, first.match.teams, first.match.bestOf)
        assertEquals("2 - 3", result.score)
        assertEquals(1, result.winnerIndex)
    }

    @Test
    fun `worlds 2026 stages have no rankings and fall back to elimination`() {
        val mapped = standings(Fixtures.read("getStandings_worlds2026.json"))

        assertEquals(listOf("Play-Ins", "Swiss", "Knockouts"), mapped.stages.map { it.name })
        assertTrue(mapped.stages.all { it.isElimination })
        assertTrue(mapped.stages.all { stage -> stage.sections.all { it.rankings.isEmpty() } })
        assertEquals(listOf(6, 33, 7), mapped.stages.map { stage -> stage.sections.sumOf { it.matches.size } })

        val opener = mapped.stages.first().sections.single().matches.first()
        assertEquals("3 - 0", formatMatchResult(opener.state, opener.match.teams, opener.match.bestOf).score)
        val swiss = mapped.stages[1].sections.single().matches.first()
        assertEquals(MatchState.UNSTARTED, swiss.state)
        assertEquals(listOf("TBD", "TBD"), swiss.match.teams.map { it.code })
    }

    @Test
    fun `tied teams share an ordinal and rankings are sorted by ordinal`() {
        val section = standings(
            """{"data":{"standings":[{"stages":[{"name":"Groups","sections":[{"name":"A","rankings":[
                {"ordinal":2,"teams":[{"id":"c","code":"CCC","record":{"wins":1,"losses":2}}]},
                {"ordinal":1,"teams":[
                    {"id":"a","code":"AAA","record":{"wins":2,"losses":1}},
                    {"id":"b","code":"BBB","record":{"wins":2,"losses":1}}
                ]},
                {"teams":[{"id":"d","code":"DDD"}]}
            ]}]}]}]}}"""
        ).stages.single().sections.single()

        assertEquals(listOf("AAA", "BBB", "CCC", "DDD"), section.rankings.map { it.team.code })
        assertEquals(listOf(1, 1, 2, null), section.rankings.map { it.ordinal })
        assertTrue(section.matches.isEmpty())
    }

    private fun singleRanking(teamJson: String) = standings(
        """{"data":{"standings":[{"stages":[{"name":"Groups","sections":[{"name":"A","rankings":[
            {"ordinal":1,"teams":[$teamJson]}
        ]}]}]}]}}"""
    ).stages.single().sections.single().rankings.single()

    @Test
    fun `ranking without a record keeps wins and losses unknown`() {
        val ranking = singleRanking("""{"id":"d","code":"DDD"}""")
        assertNull(ranking.wins)
        assertNull(ranking.losses)
    }

    @Test
    fun `ranking with a record maps wins and losses`() {
        val ranking = singleRanking("""{"id":"a","code":"AAA","record":{"wins":0,"losses":4}}""")
        assertEquals(0, ranking.wins)
        assertEquals(4, ranking.losses)
    }

    @Test
    fun `ranking with a partial record keeps only the present value`() {
        val ranking = singleRanking("""{"id":"a","code":"AAA","record":{"wins":3}}""")
        assertEquals(3, ranking.wins)
        assertNull(ranking.losses)
    }

    @Test
    fun `stages from every standings entry are flattened and matches without id are dropped`() {
        val mapped = standings(
            """{"data":{"standings":[
                {"stages":[{"name":"One","sections":[{"name":"S","matches":[{"state":"completed"},{"id":"m1"}]}]}]},
                {"stages":[{"name":"Two"}]}
            ]}}"""
        )
        assertEquals(listOf("One", "Two"), mapped.stages.map { it.name })
        val match = mapped.stages.first().sections.single().matches.single()
        assertEquals("m1", match.match.id)
        assertEquals(MatchState.UNKNOWN, match.state)
    }
}
