package com.example.lolsito.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LeaguesResponse(val data: LeaguesData = LeaguesData())

@Serializable
data class LeaguesData(val leagues: List<LeagueDto> = emptyList())

@Serializable
data class LeagueDto(
    val id: String = "",
    val slug: String = "",
    val name: String = "",
    val region: String? = null,
    val image: String? = null,
    val priority: Int? = null,
    val displayPriority: DisplayPriorityDto? = null,
)

@Serializable
data class DisplayPriorityDto(val position: Int? = null, val status: String? = null)
