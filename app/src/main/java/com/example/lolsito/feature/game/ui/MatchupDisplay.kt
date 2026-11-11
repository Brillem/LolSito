package com.example.lolsito.feature.game.ui

import com.example.lolsito.core.datadragon.itemIconUrl
import com.example.lolsito.feature.game.domain.GameStats
import com.example.lolsito.feature.game.domain.Matchup
import com.example.lolsito.feature.game.domain.PlayerDetails
import com.example.lolsito.feature.game.domain.PlayerStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Keys of the open rows that are still among [matchups], in the order they were opened. */
fun retainedExpanded(expanded: List<String>, matchups: List<Matchup>): List<String> {
    val keys = matchups.mapTo(HashSet()) { it.key }
    return expanded.filter { it in keys }
}

/** [open] after a tap on the row with [key]: opened when it was closed, closed when it was open. */
fun toggled(open: List<String>, key: String): List<String> = if (key in open) open - key else open + key

/** What one item slot of a details panel draws. */
sealed interface ItemSlotContent {
    /** Nothing: the slot holds no item. */
    data object Empty : ItemSlotContent

    /** The item id as text: there is no icon to load. */
    data class IdText(val text: String) : ItemSlotContent

    /** The icon at [url]; [idText] is drawn instead when it fails to load. */
    data class Icon(val url: String, val idText: String) : ItemSlotContent
}

/** What the slot of item [itemId] draws: its icon at Data Dragon [version], else the id; nothing for an empty slot. */
fun itemSlotContent(version: String?, itemId: Int): ItemSlotContent {
    if (itemId <= 0) return ItemSlotContent.Empty
    val url = itemIconUrl(version, itemId)
    return if (url == null) ItemSlotContent.IdText(itemId.toString()) else ItemSlotContent.Icon(url, itemId.toString())
}

/** What the opened panel of one player shows. */
sealed interface DetailsPanelContent {
    /** The details feed had nothing for the player, or what it had expired. */
    data object Unavailable : DetailsPanelContent

    /** [details], with the time they are from in [staleSince] when they are older than the stats shown. */
    data class Shown(val details: PlayerDetails, val staleSince: Instant?) : DetailsPanelContent
}

/** What the panel of [player] shows, given [staleSince] from [GameStats.staleDetailsTime]. */
fun detailsPanelContent(player: PlayerStats?, staleSince: Instant?): DetailsPanelContent {
    val details = player?.details ?: return DetailsPanelContent.Unavailable
    return DetailsPanelContent.Shown(details, staleSince)
}

/** "Details as of 12:34:56": the note for details older than the stats beside them, in [zone]'s time. */
fun detailsAsOfLabel(since: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
    "Details as of " + DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(since)
