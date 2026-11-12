package com.example.lolsito.feature.game.domain

import java.time.Duration
import java.time.Instant

/** Blue team gold minus red team gold ([difference], negative when red leads) [gameTime] after the game's first frame. */
data class GoldPoint(val gameTime: Duration, val difference: Int)

/** Each team's total gold in the frame recorded at [frameTime]. */
data class GoldSample(val frameTime: Instant, val blueGold: Int, val redGold: Int)

/** Gold difference over a game whose first frame was recorded at [start]; [points] are in game time order. */
data class GoldHistory(val start: Instant, val points: List<GoldPoint>) {

    /**
     * This history with the gold difference of [stats] appended, when its frame comes after the last
     * point; otherwise (or when the frame time is unknown) this same history.
     */
    fun including(stats: GameStats): GoldHistory {
        val frameTime = stats.frameTime ?: return this
        val gameTime = Duration.between(start, frameTime)
        val last = points.lastOrNull()?.gameTime
        if (gameTime.isNegative || (last != null && gameTime <= last)) return this
        return copy(points = points + GoldPoint(gameTime, stats.blueTeam.totalGold - stats.redTeam.totalGold))
    }

    companion object {
        /** History of [samples] since [start], one point per frame time; samples from before [start] are dropped. */
        fun of(start: Instant, samples: List<GoldSample>): GoldHistory = GoldHistory(
            start,
            samples
                .filterNot { it.frameTime.isBefore(start) }
                .distinctBy { it.frameTime }
                .sortedBy { it.frameTime }
                .map { GoldPoint(Duration.between(start, it.frameTime), it.blueGold - it.redGold) },
        )
    }
}
