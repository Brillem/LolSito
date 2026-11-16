package com.example.lolsito.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The accent color of the team whose logo is at [imageUrl], taken from the logo itself since the API
 * gives no team color (see [accentOf]). It is transparent until the logo has been read and then fades
 * in; a team without a logo, or whose logo has no color, gets [fallback].
 */
@Composable
fun rememberTeamAccent(imageUrl: String?, fallback: Color = MaterialTheme.colorScheme.primary): Color {
    val context = LocalPlatformContext.current
    val accent by produceState(initialValue = TeamAccents.known(imageUrl), imageUrl) {
        value = TeamAccents.known(imageUrl) ?: TeamAccents.read(context, imageUrl)
    }
    val target = when (val argb = accent) {
        null -> Color.Transparent
        NO_ACCENT -> fallback
        else -> Color(argb)
    }
    val color by animateColorAsState(target, tween(durationMillis = 350), label = "teamAccent")
    return color
}

/** Stands for "this logo has no color"; a real accent is opaque, so never this value. */
private const val NO_ACCENT = 0

/** Accents by logo address, kept for the app's lifetime: a logo is read once, however many screens show it. */
private object TeamAccents {
    private val byUrl = ConcurrentHashMap<String, Int>()

    /** The accent if already known ([NO_ACCENT] when there is none), or null when the logo has not been read. */
    fun known(imageUrl: String?): Int? = if (imageUrl.isNullOrBlank()) NO_ACCENT else byUrl[imageUrl]

    /**
     * Loads the logo small through the app's image loader, which already has it cached for [TeamLogo],
     * and reads its accent. A logo that fails to load has no accent for now and is tried again later.
     */
    suspend fun read(context: PlatformContext, imageUrl: String?): Int {
        if (imageUrl.isNullOrBlank()) return NO_ACCENT
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(SAMPLE_SIZE)
            .allowHardware(false) // Pixels of a hardware bitmap cannot be read.
            .build()
        val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image ?: return NO_ACCENT
        val accent = withContext(Dispatchers.Default) {
            val bitmap = image.toBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            accentOf(pixels) ?: NO_ACCENT
        }
        byUrl[imageUrl] = accent
        return accent
    }

    /** Enough pixels to tell a logo's color. */
    private const val SAMPLE_SIZE = 64
}
