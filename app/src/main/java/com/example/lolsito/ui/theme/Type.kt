package com.example.lolsito.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.lolsito.R

/**
 * The label typeface: condensed, so codes, scores and titles stay large in narrow places. Bundled in the
 * app (see assets/licenses). Only these three weights exist; a heavier request draws the bold one.
 */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

private fun label(size: TextUnit, lineHeight: TextUnit, weight: FontWeight = FontWeight.SemiBold, spacing: TextUnit = 0.3.sp) =
    TextStyle(fontFamily = BarlowCondensed, fontWeight = weight, fontSize = size, lineHeight = lineHeight, letterSpacing = spacing)

/**
 * Headlines and titles use the label typeface, a little larger than the Material defaults because a
 * condensed face reads smaller. Body and label styles keep the system font: running text, buttons and
 * chips are read, not scanned.
 */
val Typography = Typography(
    headlineLarge = label(36.sp, 40.sp, FontWeight.Bold),
    headlineMedium = label(32.sp, 36.sp, FontWeight.Bold),
    headlineSmall = label(28.sp, 32.sp, FontWeight.Bold),
    titleLarge = label(24.sp, 28.sp),
    titleMedium = label(19.sp, 24.sp),
    titleSmall = label(16.sp, 20.sp),
)

/** Label styles with no Material role of their own. */
object LabelText {
    /** A team code next to its logo, e.g. "GEN". */
    val TeamCode = label(18.sp, 22.sp)

    /** The score of a match, e.g. "2 - 1". */
    val Score = label(22.sp, 26.sp, FontWeight.Bold, spacing = 0.5.sp)

    /** The start time standing where the score will be. */
    val Time = label(18.sp, 22.sp)

    /** A small heading in capitals over a group, e.g. a day of the schedule. */
    val Overline = label(14.sp, 18.sp, spacing = 1.sp)

    /** A number in a table, e.g. a win-loss record. */
    val Number = label(16.sp, 20.sp)
}
