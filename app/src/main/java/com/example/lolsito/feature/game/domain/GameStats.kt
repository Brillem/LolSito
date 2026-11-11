package com.example.lolsito.feature.game.domain

import com.example.lolsito.core.model.UniqueKeys

/** Stats of one game at the latest frame the livestats feed has for it. */
data class GameStats(
    /** True once the feed reports the game as finished; otherwise the frame is from a live game. */
    val isFinished: Boolean,
    val blueTeam: TeamStats,
    val redTeam: TeamStats,
)

data class TeamStats(
    val totalGold: Int,
    val kills: Int,
    val towers: Int,
    /** Dragons taken, of any element. */
    val dragons: Int,
    val barons: Int,
    /** Ordered by participant id, which follows the role order (top to support). */
    val players: List<PlayerStats>,
) {
    /** LazyColumn keys aligned with [players], unique even if the feed repeats a participant id. */
    val playerKeys: List<String> by lazy {
        val unique = UniqueKeys()
        players.map { unique.next("player:${it.participantId}") }
    }
}

data class PlayerStats(
    val participantId: Int,
    val summonerName: String,
    /** Champion name as the feed sends it (for example "Olaf"). */
    val championId: String,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val creepScore: Int,
    val level: Int,
)

interface GameStatsRepository {
    /** Latest stats of game [gameId], or null when the feed has no frames for it. */
    suspend fun getGameStats(gameId: String): GameStats?
}
