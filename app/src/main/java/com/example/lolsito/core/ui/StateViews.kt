package com.example.lolsito.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lolsito.ui.theme.LolSitoTheme

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MessageView(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/**
 * Shows the view for [status]: a spinner while loading, an error with retry, [emptyMessage] when
 * there is nothing to show, and [content] once loaded.
 */
@Composable
fun LoadStatusView(
    status: LoadStatus,
    emptyMessage: String,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    when (status) {
        LoadStatus.Loading -> LoadingView()
        is LoadStatus.Error -> ErrorView(status.message, onRetry)
        LoadStatus.Empty -> MessageView(emptyMessage)
        LoadStatus.Content -> content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ErrorViewPreview() {
    LolSitoTheme { ErrorView(message = "Could not load the schedule", onRetry = {}) }
}
