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
