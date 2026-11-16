package com.example.lolsito.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lolsito.ui.theme.LolSitoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Strip at the very top of the app while stored data is shown. It draws behind the status bar and pads
 * its text below it, so it replaces the status bar inset the app Scaffold applies otherwise.
 */
@Composable
fun OfflineBanner(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Text of the banner for data stored at [cachedSince]: its time in [zone], preceded by its date when
 * that is not [today].
 */
fun offlineBannerLabel(
    cachedSince: Instant,
    zone: ZoneId,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): String {
    val stored = cachedSince.atZone(zone)
    val formatter = if (stored.toLocalDate() == today) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    }
    return "Offline · showing data from " + formatter.withLocale(locale).format(stored)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun OfflineBannerPreview() {
    LolSitoTheme { OfflineBanner(label = "Offline · showing data from 14:32") }
}
