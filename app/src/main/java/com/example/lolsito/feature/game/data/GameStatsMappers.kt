package com.example.lolsito.feature.game.data

import com.example.lolsito.core.datadragon.dataDragonVersion
import com.example.lolsito.core.network.dto.LiveStatsDetailsResponse
import com.example.lolsito.core.network.dto.LiveStatsParticipantDetailsDto
import com.example.lolsito.core.network.dto.LiveStatsParticipantFrameDto
import com.example.lolsito.core.network.dto.LiveStatsParticipantMetadataDto
import com.example.lolsito.core.network.dto.LiveStatsTeamFrameDto
import com.example.lolsito.core.network.dto.LiveStatsTeamMetadataDto
import com.example.lolsito.core.network.dto.LiveStatsWindowResponse
import com.example.lolsito.feature.game.domain.GameDetails
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.PlayerDetails
import com.example.lolsito.feature.game.domain.PlayerStats
import com.example.lolsito.feature.game.domain.TeamStats
import java.time.Instant

/**
 * Stats of the last frame, each team's players joined with that team's metadata by participant id.
 * Returns null when there are no frames. Participants without an id cannot be identified and are
 * skipped; missing numbers are 0.
 */
fun LiveStatsWindowResponse.toGameStats(): GameStats? {
    val frame = frames.orEmpty().lastOrNull() ?: return null
    return GameStats(
        isFinished = frame.gameState == "finished",
        blueTeam = frame.blueTeam.toTeamStats(gameMetadata?.blueTeamMetadata),
        redTeam = frame.redTeam.toTeamStats(gameMetadata?.redTeamMetadata),
        dataDragonVersion = dataDragonVersion(gameMetadata?.patchVersion),
        blueTeamId = gameMetadata?.blueTeamMetadata?.esportsTeamId?.takeIf { it.isNotBlank() },
        redTeamId = gameMetadata?.redTeamMetadata?.esportsTeamId?.takeIf { it.isNotBlank() },
        frameTime = frame.rfc460Timestamp.toInstantOrNull(),
    )
}

private fun LiveStatsTeamFrameDto?.toTeamStats(metadata: LiveStatsTeamMetadataDto?): TeamStats {
    val metadataById = metadata?.participantMetadata.orEmpty()
        .filter { it.participantId != null }
        .associateBy { it.participantId }
    return TeamStats(
        totalGold = this?.totalGold ?: 0,
        kills = this?.totalKills ?: 0,
        towers = this?.towers ?: 0,
        dragons = this?.dragons.orEmpty().size,
        barons = this?.barons ?: 0,
        players = this?.participants.orEmpty()
            .mapNotNull { participant ->
                val id = participant.participantId ?: return@mapNotNull null
                participant.toPlayerStats(id, metadataById[id])
            }
            .sortedBy { it.participantId },
    )
}

private fun LiveStatsParticipantFrameDto.toPlayerStats(id: Int, metadata: LiveStatsParticipantMetadataDto?) =
    PlayerStats(
        participantId = id,
        summonerName = metadata?.summonerName?.takeIf { it.isNotBlank() } ?: "Player $id",
        championId = metadata?.championId?.takeIf { it.isNotBlank() } ?: "Unknown",
        kills = kills ?: 0,
        deaths = deaths ?: 0,
        assists = assists ?: 0,
        creepScore = creepScore ?: 0,
        level = level ?: 0,
        role = metadata?.role?.takeIf { it.isNotBlank() },
    )

/**
 * Details of the last frame by participant id, with that frame's time. Returns null when there are no
 * frames. Participants without an id are skipped; missing numbers are 0 and empty item slots (id 0) are dropped.
 */
fun LiveStatsDetailsResponse.toGameDetails(): GameDetails? {
    val frame = frames.orEmpty().lastOrNull() ?: return null
    val players = frame.participants.orEmpty()
        .mapNotNull { participant -> participant.participantId?.let { it to participant.toPlayerDetails() } }
        .toMap()
    return GameDetails(frame.rfc460Timestamp.toInstantOrNull(), players)
}

/** The details of [toGameDetails] without their frame time. */
fun LiveStatsDetailsResponse.toPlayerDetails(): Map<Int, PlayerDetails>? = toGameDetails()?.players

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun LiveStatsParticipantDetailsDto.toPlayerDetails() = PlayerDetails(
    itemIds = items.orEmpty().filter { it > 0 },
    primaryStyleId = perkMetadata?.styleId,
    secondaryStyleId = perkMetadata?.subStyleId,
    keystoneId = perkMetadata?.perks?.firstOrNull(),
    damageShare = championDamageShare ?: 0.0,
    killParticipation = killParticipation ?: 0.0,
    totalGold = totalGoldEarned ?: 0,
    wardsPlaced = wardsPlaced ?: 0,
    wardsDestroyed = wardsDestroyed ?: 0,
)
