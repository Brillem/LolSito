package com.example.lolsito.feature.game.domain

import java.time.Duration
import java.time.Instant

/** How far behind the stats' frame kept details may fall before they are dropped, while the game is live. */
val DETAILS_MAX_AGE: Duration = Duration.ofMinutes(2)

/** How far behind the stats' frame details may be and still count as current: the two feeds' frames of one window differ by less. */
val DETAILS_FRESH_TOLERANCE: Duration = Duration.ofSeconds(10)

/** Details of every player the details feed knows, from the frame recorded at [frameTime]. */
data class GameDetails(val frameTime: Instant?, val players: Map<Int, PlayerDetails>)

/** Stats of one game at the latest frame the livestats feed has for it. */
data class GameStats(
    /** True once the feed reports the game as finished; otherwise the frame is from a live game. */
    val isFinished: Boolean,
    val blueTeam: TeamStats,
    val redTeam: TeamStats,
    /** Data Dragon version to load item icons from, null when the feed's patch is unknown. */
    val dataDragonVersion: String? = null,
    /** Id of the team the feed puts on the blue side; null when the feed does not say. */
    val blueTeamId: String? = null,
    /** Id of the team the feed puts on the red side; null when the feed does not say. */
    val redTeamId: String? = null,
    /** When the frame these stats are from was recorded; null when the feed does not say. */
    val frameTime: Instant? = null,
    /** When the frame the players' [PlayerStats.details] are from was recorded; null when there are none or the feed does not say. */
    val detailsFrameTime: Instant? = null,
) {
    /**
     * These stats with [details] (by participant id, from the frame at [frameTime]) attached to the players
     * they belong to. The frame time is kept only when some player got details.
     */
    fun withDetails(details: Map<Int, PlayerDetails>, frameTime: Instant? = null): GameStats {
        val joined = mapPlayers { it.copy(details = details[it.participantId]) }
        return if (joined.hasDetails()) joined.copy(detailsFrameTime = frameTime) else joined
    }

    /**
     * These stats with the details [previous] showed, when these have none. Details are all or nothing per
     * frame: a refresh that brings details for some players shows exactly those, and one that brings none
     * keeps the previous set as a whole, with its own frame time, until it is older than [DETAILS_MAX_AGE]
     * behind this frame (a finished game's details never expire).
     */
    fun keepingDetailsFrom(previous: GameStats): GameStats {
        if (hasDetails() || previous.detailsExpiredAt(this)) return this
        val before = (previous.blueTeam.players + previous.redTeam.players)
            .mapNotNull { player -> player.details?.let { player.participantId to it } }
            .toMap()
        if (before.isEmpty()) return this
        return mapPlayers { it.copy(details = before[it.participantId]) }.copy(detailsFrameTime = previous.detailsFrameTime)
    }

    /**
     * Frame time of the shown details when it is more than [DETAILS_FRESH_TOLERANCE] behind the stats'
     * frame, that is when they were kept from an earlier refresh; null when they are current or a time is unknown.
     */
    fun staleDetailsTime(): Instant? {
        val detailsTime = detailsFrameTime ?: return null
        val statsTime = frameTime ?: return null
        return detailsTime.takeIf { Duration.between(it, statsTime) > DETAILS_FRESH_TOLERANCE }
    }

    /** True while the game is live and the feed sent some player's health; a finished game shows none. */
    fun showsHealth(): Boolean = !isFinished && (blueTeam.players + redTeam.players).any { it.health != null }

    private fun hasDetails() = (blueTeam.players + redTeam.players).any { it.details != null }

    private fun detailsExpiredAt(next: GameStats): Boolean {
        if (next.isFinished) return false
        val detailsTime = detailsFrameTime ?: return false
        val statsTime = next.frameTime ?: return false
        return Duration.between(detailsTime, statsTime) > DETAILS_MAX_AGE
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
    /** Dragons taken, of any type. */
    val dragons: Int,
    val barons: Int,
    /** Ordered by participant id, which follows the role order (top to support). */
    val players: List<PlayerStats>,
    /** The type of each dragon taken, in the order taken; [dragons] is their count. */
    val dragonTypes: List<DragonType> = emptyList(),
    /** Inhibitors destroyed. Not shown; it is what tells who won (see [winnerSide]). */
    val inhibitors: Int = 0,
)

/** The kind of a dragon a team took; [UNKNOWN] is a type the feed sent that the app does not know. */
enum class DragonType { INFERNAL, OCEAN, CLOUD, MOUNTAIN, HEXTECH, CHEMTECH, ELDER, UNKNOWN }

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
    /** Lane as the feed sends it (for example "top"); null when it does not say. */
    val role: String? = null,
    /** Null when the feed sends no health for this player, and always once the game is finished. */
    val health: PlayerHealth? = null,
)

/** A player's health at the stats' frame. */
data class PlayerHealth(val current: Int, val max: Int) {
    /** How full the health bar is, 0 to 1; 0 when the maximum is not positive. */
    val fraction: Float
        get() = if (max <= 0) 0f else (current.toFloat() / max).coerceIn(0f, 1f)
}

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

    /**
     * Gold difference of game [gameId] from its first frame up to [until], or null when it cannot be
     * built. It is secondary to the stats: a repository without it has none.
     */
    suspend fun getGoldHistory(gameId: String, until: Instant): GoldHistory? = null

    /**
     * Id of the team that won game [gameId], or null when the game is not finished, has no stats, or its
     * stats do not tell (see [winnerTeamId]).
     */
    suspend fun getWinnerTeamId(gameId: String): String? = getGameStats(gameId)?.winnerTeamId()
}
