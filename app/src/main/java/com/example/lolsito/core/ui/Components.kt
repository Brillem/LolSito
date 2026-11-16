package com.example.lolsito.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lolsito.ui.theme.LabelText

/**
 * A rounded surface a step lighter than the black background: the container of anything shown as a
 * card. It fills the width it is given and adds no outer margin; [MatchCard] is the list version with
 * margins. Not clickable without [onClick].
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.CardPadding),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(Shapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/**
 * The top bar of a pushed screen: a back arrow, the [title] and optional [actions]. It sits on the
 * black background, and adds no system bar insets because the app Scaffold already applies them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        windowInsets = WindowInsets(0),
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(BackIcon, contentDescription = "Back") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** The title of a section of a screen, e.g. "Games" or "Roster". [accent] colors the one that is happening now. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = if (accent) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.Screen, end = Spacing.Screen, top = 16.dp, bottom = 4.dp),
    )
}

/** A small heading in capitals over a group inside a section, e.g. a role of the roster or a bracket round. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text.uppercase(),
        style = LabelText.Overline,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = Spacing.Screen, end = Spacing.Screen, top = 12.dp, bottom = 4.dp),
    )
}

/** A line of secondary text standing where content would be, e.g. "No matches yet". */
@Composable
fun InfoText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = 8.dp),
    )
}

/** An error that leaves the rest of the screen usable: the [message] and a Retry button on one line. */
@Composable
fun InlineError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.Screen), verticalAlignment = Alignment.CenterVertically) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/**
 * Dropdown picker drawn as a card. [label] names the current selection and [imageUrl] is its picture, if
 * any (a league logo); [items] are listed in order, each with its own picture from [itemImageUrl]. With
 * no [items] it only shows the selection and cannot be opened.
 */
@Composable
fun <T> DropdownPicker(
    label: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    itemImageUrl: (T) -> String? = { null },
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = if (items.isNotEmpty()) ({ expanded = true }) else null,
            contentPadding = PaddingValues(horizontal = Spacing.CardPadding, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (imageUrl != null) {
                    TeamLogo(imageUrl, contentDescription = null, size = 22.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (items.isNotEmpty()) {
                    Text("▾", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        DropdownMenu(expanded = expanded && items.isNotEmpty(), onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                val itemImage = itemImageUrl(item)
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    leadingIcon = itemImage?.let { url -> { TeamLogo(url, contentDescription = null, size = 22.dp) } },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                )
            }
        }
    }
}

/** Material "arrow back" glyph. Drawn here because the project has no icon library. */
val BackIcon: ImageVector =
    ImageVector.Builder("Back", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"),
            fill = SolidColor(Color.Black),
        )
        .build()
