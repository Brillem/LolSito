package com.example.lolsito.ui.theme

import androidx.compose.ui.graphics.Color

// Black surfaces with one red accent. Contrast: white on Red 4.9, Red on Black 4.3 (fine for bold
// text and components), RedText on Black 6.5 and on the Surface 5.9, TextSecondary on Black 8.3.
val Black = Color(0xFF000000)
val Surface = Color(0xFF0D0D0D)
val SurfaceContainer = Color(0xFF141414)
val SurfaceContainerHigh = Color(0xFF1C1C1C)
val SurfaceContainerHighest = Color(0xFF262626)
val SurfaceBright = Color(0xFF2A2A2A)

val TextPrimary = Color(0xFFF2F2F2)
val TextSecondary = Color(0xFFA3A3A3)
val Outline = Color(0xFF4D4D4D)
val OutlineVariant = Color(0xFF2E2E2E)

/** The accent: filled containers (LIVE badge, selected LIVE chip, buttons) with white content. */
val Red = Color(0xFFE2012D)
val OnRed = Color(0xFFFFFFFF)

/** Tinted container for selected chips and similar, with [OnRedContainer] content. */
val RedContainer = Color(0xFF4A0D18)
val OnRedContainer = Color(0xFFFFE1E5)

/** Lighter red for accent text and icons on dark surfaces (links, text buttons, active tab). */
val RedText = Color(0xFFFF4D67)
val ErrorRed = Color(0xFFFF6B7A)

/** A won series in a team's recent form; losses use the error color. */
val WinGreen = Color(0xFF3DDC84)
