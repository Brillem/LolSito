package com.example.lolsito.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** The distances every screen shares, so lists and cards line up from one screen to the next. */
object Spacing {
    /** From the screen edge to content and to cards. */
    val Screen = 16.dp

    /** Between two cards of a list. */
    val CardGap = 8.dp

    /** Inside a card, between its edge and its content. */
    val CardPadding = 12.dp
}

object Shapes {
    val Card = RoundedCornerShape(14.dp)
    val Block = RoundedCornerShape(6.dp)
}
