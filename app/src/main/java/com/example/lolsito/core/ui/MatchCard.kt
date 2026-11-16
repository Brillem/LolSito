package com.example.lolsito.core.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The card of one match in a list: a rounded surface a step lighter than the black background, inset
 * from the screen edges and from its neighbours. Not clickable without [onClick].
 */
@Composable
fun MatchCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Screen, vertical = Spacing.CardGap / 2),
        onClick = onClick,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
