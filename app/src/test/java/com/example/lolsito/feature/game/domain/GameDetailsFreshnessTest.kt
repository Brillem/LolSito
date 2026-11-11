package com.example.lolsito.feature.game.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class GameDetailsFreshnessTest {

    private val t0 = Instant.parse("2026-11-11T12:00:00Z")

    private fun details(gold: Int) = PlayerDetails(
        itemIds = listOf(3047),
        primaryStyleId = 8000,
        secondaryStyleId = 8300,
        keystoneId = 8010,
        damageShare = 0.25,
        killParticipation = 0.5,
        totalGold = gold,
        wardsPlaced = 1,
        wardsDestroyed = 2,
    )

    private fun player(id: Int) = PlayerStats(id, "P$id", "Olaf", kills = 0, deaths = 0, assists = 0, creepScore = 0, level = 1)

    /** A frame at [frameTime] for players 1, 2 and 6, with the details of [detailsGold] (by participant id) as of [detailsTime]. */
    private fun frame(
        frameTime: Instant?,
        detailsGold: Map<Int, Int> = emptyMap(),
        detailsTime: Instant? = null,
        isFinished: Boolean = false,
    ): GameStats {
        val base = GameStats(
            isFinished = isFinished,
            blueTeam = TeamStats(0, 0, 0, 0, 0, listOf(player(1), player(2))),
            redTeam = TeamStats(0, 0, 0, 0, 0, listOf(player(6))),
            frameTime = frameTime,
        )
        return if (detailsGold.isEmpty()) base else base.withDetails(detailsGold.mapValues { details(it.value) }, detailsTime)
    }

    private fun GameStats.goldByPlayer() =
        (blueTeam.players + redTeam.players).associate { it.participantId to it.details?.totalGold }

    @Test
    fun `details a refresh brings for only some players are shown as they are, never mixed with older ones`() {
        val previous = frame(t0, mapOf(1 to 100, 2 to 200, 6 to 600), detailsTime = t0)
        val refresh = frame(t0.plusSeconds(20), mapOf(1 to 111), detailsTime = t0.plusSeconds(20))

        val result = refresh.keepingDetailsFrom(previous)

        assertEquals(mapOf(1 to 111, 2 to null, 6 to null), result.goldByPlayer())
        assertEquals(t0.plusSeconds(20), result.detailsFrameTime)
    }

    @Test
    fun `a refresh with no details keeps the previous set as a whole with its own frame time`() {
        val previous = frame(t0, mapOf(1 to 100, 6 to 600), detailsTime = t0) // player 2 had none then either

        val result = frame(t0.plusSeconds(20)).keepingDetailsFrom(previous)

        assertEquals(mapOf(1 to 100, 2 to null, 6 to 600), result.goldByPlayer())
        assertEquals(t0, result.detailsFrameTime)
        assertEquals(t0.plusSeconds(20), result.frameTime)
    }

    @Test
    fun `details naming nobody in the game count as no details`() {
        val previous = frame(t0, mapOf(1 to 100, 2 to 200, 6 to 600), detailsTime = t0)
        val refresh = frame(t0.plusSeconds(20)).withDetails(mapOf(99 to details(1)), t0.plusSeconds(20))

        val result = refresh.keepingDetailsFrom(previous)

        assertEquals(mapOf(1 to 100, 2 to 200, 6 to 600), result.goldByPlayer())
        assertEquals(t0, result.detailsFrameTime)
    }

    @Test
    fun `with the details feed down for several polls details are kept and marked, then expire`() {
        var shown = frame(t0, mapOf(1 to 100, 2 to 200, 6 to 600), detailsTime = t0)
        assertNull(shown.staleDetailsTime())

        // The feed stays down: every refresh brings the window stats only.
        shown = frame(t0.plusSeconds(20)).keepingDetailsFrom(shown)
        assertEquals(mapOf(1 to 100, 2 to 200, 6 to 600), shown.goldByPlayer())
        assertEquals(t0, shown.staleDetailsTime())

        shown = frame(t0.plusSeconds(60)).keepingDetailsFrom(shown)
        assertEquals(mapOf(1 to 100, 2 to 200, 6 to 600), shown.goldByPlayer())
        assertEquals(t0, shown.staleDetailsTime())

        shown = frame(t0.plus(DETAILS_MAX_AGE)).keepingDetailsFrom(shown) // exactly the maximum age: still kept
        assertEquals(mapOf(1 to 100, 2 to 200, 6 to 600), shown.goldByPlayer())

        shown = frame(t0.plus(DETAILS_MAX_AGE).plusSeconds(1)).keepingDetailsFrom(shown)
        assertEquals(mapOf(1 to null, 2 to null, 6 to null), shown.goldByPlayer())
        assertNull(shown.detailsFrameTime)
        assertNull(shown.staleDetailsTime())

        // Once dropped they do not come back.
        shown = frame(t0.plus(DETAILS_MAX_AGE).plusSeconds(21)).keepingDetailsFrom(shown)
        assertEquals(mapOf(1 to null, 2 to null, 6 to null), shown.goldByPlayer())
    }

    @Test
    fun `a finished game keeps its details however old`() {
        val previous = frame(t0, mapOf(1 to 100, 2 to 200, 6 to 600), detailsTime = t0)

        val result = frame(t0.plus(Duration.ofHours(1)), isFinished = true).keepingDetailsFrom(previous)

        assertEquals(mapOf(1 to 100, 2 to 200, 6 to 600), result.goldByPlayer())
        assertEquals(t0, result.detailsFrameTime)
        assertEquals(t0, result.staleDetailsTime())
    }

    @Test
    fun `fresh details clear the stale marker`() {
        val kept = frame(t0.plusSeconds(40)).keepingDetailsFrom(frame(t0, mapOf(1 to 100), detailsTime = t0))
        assertEquals(t0, kept.staleDetailsTime())

        val recovered = frame(t0.plusSeconds(60), mapOf(1 to 160), detailsTime = t0.plusSeconds(60))
            .keepingDetailsFrom(kept)

        assertNull(recovered.staleDetailsTime())
        assertEquals(160, recovered.blueTeam.players.first().details?.totalGold)
    }

    @Test
    fun `details a moment older than the frame next to them are current, a window older are stale`() {
        val justBehind = frame(t0.plusSeconds(10), mapOf(1 to 100), detailsTime = t0)
        val olderWindow = frame(t0.plusSeconds(11), mapOf(1 to 100), detailsTime = t0)

        assertNull(justBehind.staleDetailsTime())
        assertEquals(t0, olderWindow.staleDetailsTime())
    }

    @Test
    fun `without frame times nothing is marked and nothing expires`() {
        val previous = frame(null, mapOf(1 to 100), detailsTime = null)

        val result = frame(null).keepingDetailsFrom(previous)

        assertEquals(100, result.blueTeam.players.first().details?.totalGold)
        assertNull(result.staleDetailsTime())
    }

    @Test
    fun `withDetails records the details frame time only when a player got details`() {
        val none = frame(t0).withDetails(mapOf(99 to details(1)), t0)
        val some = frame(t0).withDetails(mapOf(1 to details(1)), t0)

        assertNull(none.detailsFrameTime)
        assertEquals(t0, some.detailsFrameTime)
    }
}
