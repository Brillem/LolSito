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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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
 * Picker drawn as a card. [label] names the current selection and [imageUrl] is its picture, if any (a
 * league logo). Tapping the card opens a bottom sheet, headed by [title] when given, that lists [items]
 * in order, each with its own picture from [itemImageUrl]; the sheet scrolls by dragging its list and is
 * dismissed by dragging it down. The row where [isSelected] is true is highlighted with the accent color
 * and a check; by default it is the one whose label equals [label]. With no [items] it only shows the
 * selection and cannot be opened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownPicker(
    label: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    itemImageUrl: (T) -> String? = { null },
    title: String? = null,
    isSelected: (T) -> Boolean = { itemLabel(it) == label },
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
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        ChevronDownIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
    if (expanded && items.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        PickerSheet(title = title, onDismiss = { expanded = false }, sheetState = sheetState) {
            LazyColumn(contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
                items(items) { item ->
                    PickerRow(
                        label = itemLabel(item),
                        imageUrl = itemImageUrl(item),
                        selected = isSelected(item),
                        onClick = {
                            onSelect(item)
                            scope.launch { sheetState.hide() }.invokeOnCompletion { expanded = false }
                        },
                    )
                }
            }
        }
    }
}

/**
 * The bottom sheet shared by every picker: a dark container with the default drag handle, the [title]
 * when given, then [content], typically a [LazyColumn] of [PickerRow]s that honors the navigation bar
 * insets. It is dismissed by dragging it down, tapping the scrim or going back, which calls [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
    title: String?,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Screen, vertical = 8.dp),
            )
        }
        content()
    }
}

/**
 * One row of a [PickerSheet]: the optional picture at [imageUrl], the [label] and, when [selected], the
 * accent color, a tinted background and a check at the end.
 */
@Composable
fun PickerRow(label: String, imageUrl: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = Spacing.Screen, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (imageUrl != null) {
            TeamLogo(imageUrl, contentDescription = null, size = 28.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                CheckIcon,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Material "keyboard arrow down" chevron. Drawn here because the project has no icon library. */
val ChevronDownIcon: ImageVector =
    ImageVector.Builder("ChevronDown", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes("M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6 1.41,-1.41z"),
            fill = SolidColor(Color.Black),
        )
        .build()

/** Material "check" mark. Drawn here because the project has no icon library. */
val CheckIcon: ImageVector =
    ImageVector.Builder("Check", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes("M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"),
            fill = SolidColor(Color.Black),
        )
        .build()

/** Material "arrow back" glyph. Drawn here because the project has no icon library. */
val BackIcon: ImageVector =
    ImageVector.Builder("Back", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = addPathNodes("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"),
            fill = SolidColor(Color.Black),
        )
        .build()
