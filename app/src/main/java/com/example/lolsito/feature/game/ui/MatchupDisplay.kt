package com.example.lolsito.feature.game.ui

import com.example.lolsito.core.datadragon.championIconUrl
import com.example.lolsito.core.datadragon.itemIconUrl
import com.example.lolsito.core.datadragon.runeName
import com.example.lolsito.feature.game.domain.PlayerDetails
import com.example.lolsito.feature.game.domain.PlayerStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What the scoreboard shows on the second line of every player's half; one view applies to all rows. */
enum class ScoreboardView(val label: String) {
    ITEMS("Items"),
    STATS("Stats"),
}

/** What an icon slot (an item or a champion portrait) draws. */
sealed interface IconSlotContent {
    /** Nothing: the slot holds no icon. */
    data object Empty : IconSlotContent

    /** The id as text: there is no icon to load. */
    data class IdText(val text: String) : IconSlotContent

    /** The icon at [url]; [idText] is drawn instead when it fails to load. */
    data class Icon(val url: String, val idText: String) : IconSlotContent
}

/** What the slot of item [itemId] draws: its icon at Data Dragon [version], else the id; nothing for an empty slot. */
fun itemSlotContent(version: String?, itemId: Int): IconSlotContent {
    if (itemId <= 0) return IconSlotContent.Empty
    val url = itemIconUrl(version, itemId)
    return if (url == null) IconSlotContent.IdText(itemId.toString()) else IconSlotContent.Icon(url, itemId.toString())
}

/** What the portrait slot of [championId] draws: its portrait at Data Dragon [version], else the id; nothing without an id. */
fun championSlotContent(version: String?, championId: String): IconSlotContent {
    val id = championId.trim()
    if (id.isEmpty()) return IconSlotContent.Empty
    val url = championIconUrl(version, id)
    return if (url == null) IconSlotContent.IdText(id) else IconSlotContent.Icon(url, id)
}

/** Item slots on a player's strip: the feed sends up to six items and the trinket. */
const val ITEM_SLOT_COUNT = 7

/** [itemIds] padded with empty slots (0) to [ITEM_SLOT_COUNT], so every strip has the same length and lines up. */
fun itemSlots(itemIds: List<Int>): List<Int> =
    (itemIds + List(maxOf(0, ITEM_SLOT_COUNT - itemIds.size)) { 0 }).take(ITEM_SLOT_COUNT)

// Row layout, in dp. A half is what is left of the screen width beside the center role column; the item
// strip must fit in it next to the side bar (360 dp: (360 - 2 * 4 - 28) / 2 = 162; the strip is 7 * 20 + 6 * 2 = 152).
const val SCREEN_PADDING_DP = 4
const val ROLE_COLUMN_DP = 28
const val SIDE_BAR_DP = 3
const val ITEM_SLOT_DP = 20
const val ITEM_GAP_DP = 2

/** Width of one player's half on a screen [screenDp] wide. */
fun halfWidthDp(screenDp: Int): Int = (screenDp - 2 * SCREEN_PADDING_DP - ROLE_COLUMN_DP) / 2

/** Width of the item strip: [ITEM_SLOT_COUNT] slots and the gaps between them. */
fun itemStripWidthDp(): Int = ITEM_SLOT_COUNT * ITEM_SLOT_DP + (ITEM_SLOT_COUNT - 1) * ITEM_GAP_DP

/** One label and value of the stats view, short enough not to wrap in a third of a half. */
data class StatCell(val label: String, val value: String)

/** Gold, kill participation, damage share and wards of [details], in the order the stats view lays them out. */
fun statCells(details: PlayerDetails): List<StatCell> = listOf(
    StatCell("Gold", formatGold(details.totalGold)),
    StatCell("KP", formatPercent(details.killParticipation)),
    StatCell("DMG", formatPercent(details.damageShare)),
    StatCell("Wards", "${details.wardsPlaced}/${details.wardsDestroyed}"),
)

/** What the second line of one player's half shows. */
sealed interface PlayerLine {
    /** The details feed had nothing for the player, or what it had expired. */
    data object Unavailable : PlayerLine

    /** The padded item strip ([ITEM_SLOT_COUNT] item ids, 0 for an empty slot). */
    data class Items(val slots: List<Int>) : PlayerLine

    /** The compact stat [cells] and the [keystone] rune name ("-" when the feed did not send it). */
    data class Stats(val cells: List<StatCell>, val keystone: String) : PlayerLine
}

/** The second line of [player]'s half in [view]. */
fun playerLine(player: PlayerStats, view: ScoreboardView): PlayerLine {
    val details = player.details ?: return PlayerLine.Unavailable
    return when (view) {
        ScoreboardView.ITEMS -> PlayerLine.Items(itemSlots(details.itemIds))
        ScoreboardView.STATS -> PlayerLine.Stats(statCells(details), details.keystoneId?.let { runeName(it) } ?: "-")
    }
}

/** "212 CS · 13" (creep score and level). */
fun csLevelLabel(player: PlayerStats): String = "${player.creepScore} CS · ${player.level}"

/** "Details as of 12:34:56": the note for details older than the stats beside them, in [zone]'s time. */
fun detailsAsOfLabel(since: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
    "Details as of " + DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(since)
