package com.example.lolsito.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lolsito.ui.theme.LolSitoTheme

/** One gray shape standing for content that is loading; size it with [modifier]. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = Shapes.Block) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, shape))
}

/**
 * Loading state of a screen: [content] (skeleton blocks shaped like what is coming) pulsing as one, and
 * read by a screen reader as a single "Loading".
 */
@Composable
fun SkeletonScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = pulse }
            .clearAndSetSemantics { contentDescription = "Loading" },
    ) {
        content()
    }
}

/** The silhouette of a [MatchCard]: two teams around a result, and a caption below. */
@Composable
fun MatchCardSkeleton(modifier: Modifier = Modifier) {
    MatchCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            SkeletonBlock(Modifier.size(width = 44.dp, height = 18.dp))
            Spacer(Modifier.width(8.dp))
            SkeletonBlock(Modifier.size(28.dp), CircleShape)
            Spacer(Modifier.width(20.dp))
            SkeletonBlock(Modifier.size(width = 48.dp, height = 22.dp))
            Spacer(Modifier.width(20.dp))
            SkeletonBlock(Modifier.size(28.dp), CircleShape)
            Spacer(Modifier.width(8.dp))
            SkeletonBlock(Modifier.size(width = 44.dp, height = 18.dp))
        }
        Spacer(Modifier.height(10.dp))
        SkeletonBlock(Modifier.size(width = 150.dp, height = 12.dp))
    }
}

/** A list that is loading: a heading and [cards] match cards. The default loading state of a screen. */
@Composable
fun MatchListSkeleton(modifier: Modifier = Modifier, cards: Int = 6) {
    SkeletonScreen(modifier) {
        Column {
            SkeletonBlock(Modifier.padding(start = Spacing.Screen, top = 16.dp, bottom = 8.dp).size(width = 120.dp, height = 16.dp))
            repeat(cards) { MatchCardSkeleton() }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MatchListSkeletonPreview() {
    LolSitoTheme { MatchListSkeleton(cards = 3) }
}
