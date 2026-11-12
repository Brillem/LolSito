package com.example.lolsito.feature.schedule.domain

import com.example.lolsito.core.model.Team
import com.example.lolsito.core.preferences.FavoriteTeam

/**
 * Schedule teams have only a name and a code, so a favorite is recognized by name: trimmed and
 * case-insensitive, the same way the team profile finds a team's matches. A renamed team stops
 * matching until its profile is opened again.
 */
fun Team.isFavoriteIn(favoriteNames: Collection<String>): Boolean {
    val teamName = name.trim()
    return favoriteNames.any { it.trim().equals(teamName, ignoreCase = true) }
}

/** True when one of the match's teams is a favorite. */
fun ScheduleEvent.involvesFavorite(favoriteNames: Collection<String>): Boolean =
    favoriteNames.isNotEmpty() && match?.teams?.any { it.isFavoriteIn(favoriteNames) } == true

/** The favorites' names, trimmed, in order, without blanks or repeats (ignoring case). */
fun favoriteNamesOf(favorites: List<FavoriteTeam>): List<String> =
    favorites.map { it.name.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
