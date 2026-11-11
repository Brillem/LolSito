package com.example.lolsito.feature.game.domain

import com.example.lolsito.core.model.UniqueKeys

/** Stats of one game at the latest frame the livestats feed has for it. */
data class GameStats(
    /** True once the feed reports the game as finished; otherwise the frame is from a live game. */
    val isFinished: Boolean,
    val blueTeam: TeamStats,
    val redTeam: TeamStats,
    /** Data Dragon version to load item icons from, null when the feed's patch is unknown. */
    val dataDragonVersion: String? = null,
) {
    /** These stats with [details] (by participant id) attached to the players they belong to. */
    fun withDetails(details: Map<Int, PlayerDetails>): GameStats = mapPlayers { it.copy(details = details[it.participantId]) }

    /** These stats, where a player has no details, with the ones [previous] had for that participant. */
    fun keepingDetailsFrom(previous: GameStats): GameStats {
        val before = (previous.blueTeam.players + previous.redTeam.players)
            .mapNotNull { player -> player.details?.let { player.participantId to it } }
            .toMap()
        return mapPlayers { it.copy(details = it.details ?: before[it.participantId]) }
    }

    private fun mapPlayers(transform: (PlayerStats) -> PlayerStats) = copy(
        blueTeam = blueTeam.copy(players = blueTeam.players.map(transform)),
        redTeam = redTeam.copy(players = redTeam.players.map(transform)),
    )
}

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
    /** Null when the details feed has nothing for this player (yet). */
    val details: PlayerDetails? = null,
)

data class PlayerDetails(
    /** Items in slot order, without empty slots. */
    val itemIds: List<Int>,
    val primaryStyleId: Int?,
    val secondaryStyleId: Int?,
    val keystoneId: Int?,
    /** Share of the team's damage to champions, 0 to 1. */
    val damageShare: Double,
    /** Share of the team's kills this player took part in, 0 to 1. */
    val killParticipation: Double,
    val totalGold: Int,
    val wardsPlaced: Int,
    val wardsDestroyed: Int,
)

interface GameStatsRepository {
    /** Latest stats of game [gameId], or null when the feed has no frames for it. */
    suspend fun getGameStats(gameId: String): GameStats?
}
