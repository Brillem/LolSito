package com.example.lolsito.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lolsito.feature.match.domain.FormEntry
import com.example.lolsito.feature.match.domain.FormOutcome
import com.example.lolsito.ui.theme.WinGreen

/** A mark per series of a team's recent form, in the order of [entries] (most recent first). */
@Composable
fun FormMarks(entries: List<FormEntry>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { entry -> FormMark(entry) }
    }
}

/** "W" or "L" over the opponent's code. */
@Composable
fun FormMark(entry: FormEntry) {
    val won = entry.outcome == FormOutcome.WIN
    Column(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = (if (won) "Win against " else "Loss against ") + entry.opponentCode
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (won) "W" else "L",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (won) WinGreen else MaterialTheme.colorScheme.error,
        )
        Text(
            entry.opponentCode,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
