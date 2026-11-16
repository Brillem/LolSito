package com.example.lolsito.core.ui

import kotlin.math.roundToInt

/**
 * The accent color of a logo given as ARGB [pixels]: the color that covers most of it, as an opaque ARGB
 * value, or null when the logo has no color to speak of.
 *
 * Transparent pixels are not part of the logo, and white, gray and black ones are not colors. The rest
 * are grouped by hue, so the shades of one color count together, each weighing more the more vivid it
 * is; the average of the heaviest group wins. A result too dark to be seen on the app's black background
 * is lightened without changing its hue.
 */
fun accentOf(pixels: IntArray): Int? {
    val weights = FloatArray(HUE_GROUPS)
    val reds = FloatArray(HUE_GROUPS)
    val greens = FloatArray(HUE_GROUPS)
    val blues = FloatArray(HUE_GROUPS)
    var opaque = 0
    var colored = 0
    for (pixel in pixels) {
        if (pixel ushr 24 < MIN_ALPHA) continue
        opaque++
        val r = pixel shr 16 and 0xFF
        val g = pixel shr 8 and 0xFF
        val b = pixel and 0xFF
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max == 0) continue
        val value = max / 255f
        val saturation = (max - min) / max.toFloat()
        if (value < MIN_VALUE || saturation < MIN_SATURATION) continue
        colored++
        val group = (hueOf(r, g, b, max, min) / (360f / HUE_GROUPS)).toInt().coerceIn(0, HUE_GROUPS - 1)
        val weight = saturation * value
        weights[group] += weight
        reds[group] += r * weight
        greens[group] += g * weight
        blues[group] += b * weight
    }
    if (opaque == 0 || colored < opaque * MIN_COLORED_SHARE) return null
    val best = weights.indices.maxBy { weights[it] }
    return visibleOnBlack(
        r = (reds[best] / weights[best]).roundToInt(),
        g = (greens[best] / weights[best]).roundToInt(),
        b = (blues[best] / weights[best]).roundToInt(),
    )
}

/** The hue in degrees, [0, 360), of a color that is not gray ([max] > [min]). */
private fun hueOf(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
    val span = (max - min).toFloat()
    val hue = when (max) {
        r -> (g - b) / span % 6f
        g -> (b - r) / span + 2f
        else -> (r - g) / span + 4f
    } * 60f
    return if (hue < 0f) hue + 360f else hue
}

/** Scales the channels together until the strongest reaches [MIN_VISIBLE_CHANNEL], which keeps the hue. */
private fun visibleOnBlack(r: Int, g: Int, b: Int): Int {
    val max = maxOf(r, g, b)
    val factor = if (max in 1 until MIN_VISIBLE_CHANNEL) MIN_VISIBLE_CHANNEL / max.toFloat() else 1f
    fun scaled(channel: Int) = (channel * factor).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (scaled(r) shl 16) or (scaled(g) shl 8) or scaled(b)
}

/** Below this alpha a pixel is background showing through, not logo. */
private const val MIN_ALPHA = 128

/** Below this brightness a pixel is black. */
private const val MIN_VALUE = 0.2f

/** Below this saturation a pixel is white or gray. */
private const val MIN_SATURATION = 0.25f

/** A logo with a smaller share of colored pixels is treated as having no color. */
private const val MIN_COLORED_SHARE = 0.03f

/** Hues are grouped in steps of 15 degrees. */
private const val HUE_GROUPS = 24

/** The strongest channel of an accent is at least this, about 70% brightness. */
private const val MIN_VISIBLE_CHANNEL = 178
