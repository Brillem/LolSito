package com.example.lolsito.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LiveStatsWindowResponse(
    val esportsGameId: String? = null,
    val gameMetadata: LiveStatsGameMetadataDto? = null,
    val frames: List<LiveStatsFrameDto>? = null,
)

@Serializable
data class LiveStatsGameMetadataDto(
    /** Game build such as "16.20.826.3891", not a Data Dragon version. */
    val patchVersion: String? = null,
    val blueTeamMetadata: LiveStatsTeamMetadataDto? = null,
    val redTeamMetadata: LiveStatsTeamMetadataDto? = null,
)

@Serializable
data class LiveStatsTeamMetadataDto(val participantMetadata: List<LiveStatsParticipantMetadataDto>? = null)

@Serializable
data class LiveStatsParticipantMetadataDto(
    val participantId: Int? = null,
    val summonerName: String? = null,
    val championId: String? = null,
)

@Serializable
data class LiveStatsFrameDto(
    val rfc460Timestamp: String? = null,
    val gameState: String? = null,
    val blueTeam: LiveStatsTeamFrameDto? = null,
    val redTeam: LiveStatsTeamFrameDto? = null,
)

@Serializable
data class LiveStatsTeamFrameDto(
    val totalGold: Int? = null,
    val totalKills: Int? = null,
    val towers: Int? = null,
    val barons: Int? = null,
    val dragons: List<String>? = null,
    val participants: List<LiveStatsParticipantFrameDto>? = null,
)

@Serializable
data class LiveStatsParticipantFrameDto(
    val participantId: Int? = null,
    val level: Int? = null,
    val kills: Int? = null,
    val deaths: Int? = null,
    val assists: Int? = null,
    val creepScore: Int? = null,
)

@Serializable
data class LiveStatsDetailsResponse(val frames: List<LiveStatsDetailsFrameDto>? = null)

@Serializable
data class LiveStatsDetailsFrameDto(
    val rfc460Timestamp: String? = null,
    val participants: List<LiveStatsParticipantDetailsDto>? = null,
)

@Serializable
data class LiveStatsParticipantDetailsDto(
    val participantId: Int? = null,
    /** Item ids in slot order; 0 is an empty slot. */
    val items: List<Int>? = null,
    val perkMetadata: LiveStatsPerkMetadataDto? = null,
    val totalGoldEarned: Int? = null,
    /** Share (0 to 1) of the team's kills this player took part in. */
    val killParticipation: Double? = null,
    /** Share (0 to 1) of the team's damage to champions. */
    val championDamageShare: Double? = null,
    val wardsPlaced: Int? = null,
    val wardsDestroyed: Int? = null,
)

@Serializable
data class LiveStatsPerkMetadataDto(
    val styleId: Int? = null,
    val subStyleId: Int? = null,
    /** The keystone first, then the other runes and stat shards. */
    val perks: List<Int>? = null,
)
