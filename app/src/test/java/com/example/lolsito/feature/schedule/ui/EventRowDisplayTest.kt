package com.example.lolsito.feature.schedule.ui

import com.example.lolsito.core.model.Match
import com.example.lolsito.core.model.MatchOutcome
import com.example.lolsito.core.model.MatchState
import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.model.Team
import com.example.lolsito.core.model.TeamEmphasis
import com.example.lolsito.feature.schedule.domain.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class EventRowDisplayTest {

    private fun event(state: MatchState, first: Int? = 2, second: Int? = 1, outcomes: Boolean = true) = ScheduleEvent(
        startTime = Instant.parse("2026-10-20T07:00:00Z"),
        state = state,
        blockName = null,
        leagueName = "Worlds",
        leagueSlug = "worlds",
        match = Match(
            id = "m",
            teams = listOf(
                Team("T1", "T1", null, MatchOutcome.WIN.takeIf { outcomes }, first),
                Team("Gen.G", "GEN", null, MatchOutcome.LOSS.takeIf { outcomes }, second),
            ),
            format = SeriesFormat(SeriesType.BEST_OF, 3),
        ),
    )

    @Test
    fun `a finished match shows its score and dims the loser when the mode is off`() {
        val display = eventRowDisplay(event(MatchState.COMPLETED), isLive = false, noSpoilers = false)

        assertEquals("2 - 1", display.score)
        assertEquals("2 - 1", display.result.score)
        assertEquals(TeamEmphasis.WINNER, display.result.emphasisFor(0))
        assertEquals(TeamEmphasis.LOSER, display.result.emphasisFor(1))
        assertEquals("Bo3", display.result.formatLabel)
    }

    @Test
    fun `a finished match shows vs and dims nobody when the mode is on`() {
        val display = eventRowDisplay(event(MatchState.COMPLETED), isLive = false, noSpoilers = true)

        assertEquals("vs", display.score)
        assertNull(display.result.score)
        assertNull(display.result.winnerIndex)
        assertEquals(TeamEmphasis.NEUTRAL, display.result.emphasisFor(0))
        assertEquals(TeamEmphasis.NEUTRAL, display.result.emphasisFor(1))
        assertEquals("Bo3", display.result.formatLabel)
    }

    @Test
    fun `a finished match without a known score also reads vs when the mode is on`() {
        val display = eventRowDisplay(event(MatchState.COMPLETED, first = null, second = null), isLive = false, noSpoilers = true)

        assertEquals("vs", display.score)
    }

    @Test
    fun `an unstarted match is the same with the mode on or off`() {
        val unstarted = event(MatchState.UNSTARTED, first = null, second = null, outcomes = false)

        assertEquals(eventRowDisplay(unstarted, isLive = false, noSpoilers = false), eventRowDisplay(unstarted, isLive = false, noSpoilers = true))
        assertNull(eventRowDisplay(unstarted, isLive = false, noSpoilers = true).score)
    }

    @Test
    fun `a live match keeps its live score with the mode on`() {
        val live = event(MatchState.IN_PROGRESS, first = 1, second = 0, outcomes = false)

        assertEquals("1 - 0", eventRowDisplay(live, isLive = true, noSpoilers = true).score)
        assertEquals("1 - 0", eventRowDisplay(live, isLive = true, noSpoilers = false).score)
    }

    @Test
    fun `a live row without a score reads vs`() {
        val live = event(MatchState.IN_PROGRESS, first = null, second = null, outcomes = false)

        assertEquals("vs", eventRowDisplay(live, isLive = true, noSpoilers = false).score)
    }
}
