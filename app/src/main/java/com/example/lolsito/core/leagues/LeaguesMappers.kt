package com.example.lolsito.core.leagues

import com.example.lolsito.core.model.League
import com.example.lolsito.core.network.dto.LeagueDto
import com.example.lolsito.core.network.toHttps

private val statusRank = mapOf("force_selected" to 0, "selected" to 1, "not_selected" to 2)

/** Drops hidden leagues and sorts by display status, position, priority, then name. */
fun List<LeagueDto>.toDomainLeagues(): List<League> =
    filter { it.id.isNotBlank() && it.displayPriority?.status != "hidden" }
        .sortedWith(
            compareBy<LeagueDto> { statusRank[it.displayPriority?.status] ?: Int.MAX_VALUE }
                .thenBy { it.displayPriority?.position ?: Int.MAX_VALUE }
                .thenBy { it.priority ?: Int.MAX_VALUE }
                .thenBy { it.name }
        )
        .map { League(it.id, it.slug, it.name, it.region, it.image.toHttps()) }
