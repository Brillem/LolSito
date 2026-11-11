package com.example.lolsito.feature.game.domain

/** A team of the game as the screen is opened with it: its [code] and, when it has one, its [logoUrl]. */
data class GameTeam(val code: String, val logoUrl: String? = null)
