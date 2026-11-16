package com.example.lolsito.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/** Whether going [from] one route [to] another only changes the bottom bar tab, rather than opening or closing a screen. */
internal fun isTabSwitch(from: String?, to: String?): Boolean =
    from != null && to != null && showsBottomBar(from) && showsBottomBar(to)

private const val DURATION_MILLIS = 220

/** The share of the screen width a pushed screen travels while it fades. */
private const val SLIDE_FRACTION = 10

/** A tab fades in; a pushed screen also comes a short way from the right. */
internal fun screenEnter(tabSwitch: Boolean): EnterTransition {
    val fade = fadeIn(tween(DURATION_MILLIS))
    return if (tabSwitch) fade else fade + slideInHorizontally(tween(DURATION_MILLIS)) { it / SLIDE_FRACTION }
}

/** The screen left behind, by a tab switch or under a pushed screen, just fades. */
internal fun screenExit(): ExitTransition = fadeOut(tween(DURATION_MILLIS))

/** The screen uncovered by going back just fades in. */
internal fun screenPopEnter(): EnterTransition = fadeIn(tween(DURATION_MILLIS))

/** A screen closed by going back leaves a short way to the right while it fades. */
internal fun screenPopExit(tabSwitch: Boolean): ExitTransition {
    val fade = fadeOut(tween(DURATION_MILLIS))
    return if (tabSwitch) fade else fade + slideOutHorizontally(tween(DURATION_MILLIS)) { it / SLIDE_FRACTION }
}
