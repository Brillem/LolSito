package com.example.lolsito.feature.standings.data

import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.formatMatchResult
import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.StandingsResponse
import com.example.lolsito.core.network.dto.TournamentsResponse
import com.example.lolsito.feature.standings.domain.StandingsSection
import com.example.lolsito.feature.standings.domain.WinnerLink
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
    fun `lck standings map ranked group sections without a layout`() {
        val mapped = standings(Fixtures.read("getStandingsV3_lck.json"))

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
        assertTrue(legend.columns.isEmpty())
        assertTrue(legend.matches.isEmpty())
    }

    @Test
    fun `lck bracket stages map the official columns and cells with their results`() {
        val mapped = standings(Fixtures.read("getStandingsV3_lck.json"))
        val playIns = mapped.stages[1]

        assertTrue(playIns.isElimination)
        val section = playIns.sections.single()
        assertEquals(listOf(listOf("Round 1"), listOf("Round 2")), section.cellNames())
        assertEquals(3, section.matches.size)
        val first = section.matches.first()
        assertEquals("117030752644841571", first.match.id)
        assertEquals(MatchState.COMPLETED, first.state)
        assertEquals(listOf("BRO", "KT"), first.match.teams.map { it.code })
        assertTrue(first.match.teams.all { it.imageUrl!!.startsWith("https://") })
        val result = formatMatchResult(first.state, first.match.teams, first.match.format)
        assertEquals("2 - 3", result.score)
        assertEquals(1, result.winnerIndex)

        val regional = mapped.stages[2].sections.single()
        assertEquals(
            listOf(
                listOf("Round 1"),
                listOf("Upper Bracket - Round 2", "Lower Bracket - Round 1"),
                listOf("Lower Bracket - Round 2"),
                listOf("Loser's Bracket - Round 3"),
                listOf("Upper Bracket - Finals"),
                listOf("Lower Bracket - Finals"),
                listOf("Finals"),
            ),
            regional.cellNames(),
        )
        assertEquals(10, regional.matches.size)
        assertEquals(regional.columns.flatMap { c -> c.cells.flatMap { it.matches } }, regional.matches)
    }

    @Test
    fun `lec playoffs map a six-team double elimination with its regular season table`() {
        val mapped = standings(Fixtures.read("getStandingsV3_lec.json"))

        assertEquals(listOf("Regular Season", "Playoffs"), mapped.stages.map { it.name })
        assertEquals(10, mapped.stages.first().sections.single().rankings.size)
        val playoffs = mapped.stages[1].sections.single()
        assertEquals(
            listOf(
                listOf("Upper Bracket - Round 1"),
                listOf("Upper Bracket - Round 2", "Lower Bracket - Round 1"),
                listOf("Lower Bracket - Round 2"),
                listOf("Lower Bracket - Round 3"),
                listOf("Finals"),
            ),
            playoffs.cellNames(),
        )
        assertEquals(listOf(listOf(2), listOf(1, 2), listOf(1), listOf(1), listOf(1)), playoffs.cellSizes())
    }

    @Test
    fun `msi maps both double-elimination sections with their official titles`() {
        val mapped = standings(Fixtures.read("getStandingsV3_msi2026.json"))

        assertEquals(listOf("Play-Ins", "Knockouts"), mapped.stages.map { it.name })
        val playIns = mapped.stages[0].sections.single()
        assertEquals("Play In Knockouts", playIns.name)
        assertEquals(
            listOf(listOf("Round 1"), listOf("Upper Bracket - Finals", "Lower Bracket - Semifinals"), listOf("Lower Bracket - Finals"), listOf("Finals")),
            playIns.cellNames(),
        )
        val knockouts = mapped.stages[1].sections.single()
        assertEquals(
            listOf(
                listOf("Upper Bracket - Round 1"),
                listOf("Upper Bracket - Round 2", "Lower Bracket - Round 1"),
                listOf("Upper Bracket - Round 3", "Lower Bracket - Round 2"),
                listOf("Lower Bracket - Round 3"),
                listOf("Lower Bracket - Round 4"),
                listOf("Finals"),
            ),
            knockouts.cellNames(),
        )
        assertEquals(listOf(listOf(4), listOf(2, 2), listOf(1, 2), listOf(1), listOf(1), listOf(1)), knockouts.cellSizes())
        assertEquals(14, knockouts.matches.size)
    }

    @Test
    fun `worlds 2026 maps play-ins, swiss rounds and knockouts with no rankings`() {
        val mapped = standings(Fixtures.read("getStandingsV3_worlds2026.json"))

        assertEquals(listOf("Play-Ins", "Swiss", "Knockouts"), mapped.stages.map { it.name })
        assertTrue(mapped.stages.all { it.isElimination })
        assertTrue(mapped.stages.all { stage -> stage.sections.all { it.rankings.isEmpty() } })
        assertEquals(listOf(6, 33, 7), mapped.stages.map { stage -> stage.sections.sumOf { it.matches.size } })

        val swiss = mapped.stages[1].sections.single()
        assertEquals((1..5).map { listOf("Round $it") }, swiss.cellNames())
        assertEquals(listOf(listOf(8), listOf(8), listOf(8), listOf(6), listOf(3)), swiss.cellSizes())
        assertTrue(swiss.winnerLinks.isEmpty())
        val knockouts = mapped.stages[2].sections.single()
        assertEquals(listOf(listOf("Quarterfinals"), listOf("Semifinals"), listOf("Finals")), knockouts.cellNames())

        val opener = mapped.stages.first().sections.single().matches.first()
        assertEquals("3 - 0", formatMatchResult(opener.state, opener.match.teams, opener.match.format).score)
    }

    @Test
    fun `winner links follow slot 1 of a match origin and drops to the lower bracket are not links`() {
        val playIns = standings(Fixtures.read("getStandingsV3_msi2026.json")).stages[0].sections.single()

        assertEquals(
            listOf(
                // Round 1 winners meet in the upper final; the losers' lower semifinal is no link.
                WinnerLink("115570934355614497", "115570934355614509"),
                WinnerLink("115570934355614503", "115570934355614509"),
                // The lower semifinal winner plays the lower final, against the upper final loser (no link).
                WinnerLink("115846993721104369", "115570934355614521"),
                WinnerLink("115570934355614509", "115570969745329263"),
                WinnerLink("115570934355614521", "115570969745329263"),
            ),
            playIns.winnerLinks,
        )
    }

    @Test
    fun `winner links of a custom gauntlet skip slots that come from a decision point`() {
        val regional = standings(Fixtures.read("getStandingsV3_lck.json")).stages[2].sections.single()

        assertEquals(
            listOf(
                WinnerLink("117030752644841613", "117030752644841625"),
                WinnerLink("117030752644841625", "117030752644841631"),
                WinnerLink("117030752644841607", "117030752644841619"),
                WinnerLink("117030752644841601", "117030752644841619"),
                WinnerLink("117030752644841631", "117030752644841637"),
                WinnerLink("117030752644841619", "117030752644841643"),
                WinnerLink("117030752644841637", "117030752644841643"),
            ),
            regional.winnerLinks,
        )
    }

    @Test
    fun `matches whose teams are not known yet keep their cell and their links`() {
        val section = standings(
            """{"data":{"standings":[{"stages":[{"name":"Knockouts","sections":[{"name":"Knockouts","type":"bracket","columns":[
                {"cells":[{"name":"Semifinals","matches":[
                    {"id":"s1","structuralId":"match_s1","state":"unstarted","teams":[
                        {"id":"0","code":"TBD","name":"TBD","origin":{"structuralId":"dp","type":"decisionPoint","slot":1}},
                        {"id":"0","code":"TBD","name":"TBD","origin":{"structuralId":"dp","type":"decisionPoint","slot":2}}]},
                    {"id":"s2","structuralId":"match_s2","state":"unstarted","teams":[]}
                ]}]},
                {"cells":[{"name":"Finals","matches":[
                    {"id":"f","structuralId":"match_f","state":"unstarted","teams":[
                        {"id":"0","code":"TBD","name":"TBD","origin":{"structuralId":"match_s1","type":"match","slot":1}},
                        {"id":"0","code":"TBD","name":"TBD","origin":{"structuralId":"match_s2","type":"match","slot":1}}]}
                ]}]}
            ]}]}]}]}}"""
        ).stages.single().sections.single()

        assertEquals(listOf(listOf("Semifinals"), listOf("Finals")), section.cellNames())
        assertEquals(listOf("s1", "s2", "f"), section.matches.map { it.match.id })
        assertTrue(section.matches.all { it.state == MatchState.UNSTARTED })
        assertEquals(listOf("TBD", "TBD"), section.matches.first().match.teams.map { it.code })
        assertEquals(listOf(WinnerLink("s1", "f"), WinnerLink("s2", "f")), section.winnerLinks)
    }

    @Test
    fun `a bracket section without columns keeps a flat match list and no layout`() {
        val sections = standings(
            """{"data":{"standings":[{"stages":[{"name":"Playoffs","sections":[
                {"name":"Empty","type":"bracket","columns":[]},
                {"name":"Flat","type":"bracket","matches":[{"id":"m1","state":"completed"}]}
            ]}]}]}}"""
        ).stages.single().sections

        assertTrue(sections[0].matches.isEmpty())
        assertTrue(sections[0].columns.isEmpty())
        assertEquals(listOf("m1"), sections[1].matches.map { it.match.id })
        assertTrue(sections[1].columns.isEmpty())
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
    fun `layout matches without id are dropped along with the links that point to them`() {
        val section = standings(
            """{"data":{"standings":[{"stages":[{"name":"S","sections":[{"name":"S","type":"bracket","columns":[
                {"cells":[{"name":"Round 1","matches":[{"structuralId":"match_a","state":"completed"},{"id":"b","structuralId":"match_b"}]}]},
                {"cells":[{"name":"Final","matches":[{"id":"f","structuralId":"match_f","teams":[
                    {"code":"TBD","origin":{"structuralId":"match_a","type":"match","slot":1}},
                    {"code":"TBD","origin":{"structuralId":"match_b","type":"match","slot":1}}]}]}]}
            ]}]}]}]}}"""
        ).stages.single().sections.single()

        assertEquals(listOf("b", "f"), section.matches.map { it.match.id })
        assertEquals(listOf(WinnerLink("b", "f")), section.winnerLinks)
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

    private fun StandingsSection.cellNames() = columns.map { column -> column.cells.map { it.name } }

    private fun StandingsSection.cellSizes() = columns.map { column -> column.cells.map { it.matches.size } }
}
