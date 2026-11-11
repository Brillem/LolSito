package com.example.lolsito.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lolsito.ui.theme.LolSitoTheme

/** Small "LIVE" pill marking a match that is being played right now. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = "LIVE",
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LiveBadgePreview() {
    LolSitoTheme { LiveBadge() }
}
