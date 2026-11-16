package com.example.lolsito.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * A team's logo. A faint disc stands in while it loads, when it fails and when the team has none, so a
 * row keeps its shape; the logo then fades in over it.
 */
@Composable
fun TeamLogo(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val context = LocalPlatformContext.current
    val request = remember(context, imageUrl) { ImageRequest.Builder(context).data(imageUrl).crossfade(true).build() }
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val placeholder = remember(placeholderColor) { DiscPainter(placeholderColor) }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
    )
}

/** A filled circle as large as the space it is given. */
private class DiscPainter(private val color: Color) : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawCircle(color)
    }
}
