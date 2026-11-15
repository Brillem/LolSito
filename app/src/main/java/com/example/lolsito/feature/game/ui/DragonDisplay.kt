package com.example.lolsito.feature.game.ui

import com.example.lolsito.feature.game.domain.DragonType

/** The short label a dragon of [type] is shown with; an unknown type is a generic dragon. */
fun dragonLabel(type: DragonType): String = when (type) {
    DragonType.INFERNAL -> "Inf"
    DragonType.OCEAN -> "Ocn"
    DragonType.CLOUD -> "Cld"
    DragonType.MOUNTAIN -> "Mtn"
    DragonType.HEXTECH -> "Hex"
    DragonType.CHEMTECH -> "Chm"
    DragonType.ELDER -> "Eld"
    DragonType.UNKNOWN -> "Drg"
}

/** The full name of a dragon of [type], for screen readers; an unknown type is a generic dragon. */
fun dragonName(type: DragonType): String = when (type) {
    DragonType.INFERNAL -> "Infernal"
    DragonType.OCEAN -> "Ocean"
    DragonType.CLOUD -> "Cloud"
    DragonType.MOUNTAIN -> "Mountain"
    DragonType.HEXTECH -> "Hextech"
    DragonType.CHEMTECH -> "Chemtech"
    DragonType.ELDER -> "Elder"
    DragonType.UNKNOWN -> "Dragon"
}

/** What a screen reader says for a team's dragons: how many, then their names in the order taken. */
fun dragonsDescription(types: List<DragonType>): String {
    if (types.isEmpty()) return "No dragons"
    val noun = if (types.size == 1) "dragon" else "dragons"
    return "${types.size} $noun: ${types.joinToString { dragonName(it) }}"
}

/** Where Community Dragon, a public mirror of the game's files, serves the minimap icons. */
private const val DRAGON_ICONS_URL = "https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons"

/**
 * The game's own icon of a dragon of [type]. The file names follow the type names the feed sends; an unknown
 * type uses the generic dragon icon. The host is not Riot's, so callers keep [dragonLabel] as the fallback.
 */
fun dragonIconUrl(type: DragonType): String = when (type) {
    DragonType.INFERNAL -> "$DRAGON_ICONS_URL/dragon_infernal.png"
    DragonType.OCEAN -> "$DRAGON_ICONS_URL/dragon_ocean.png"
    DragonType.CLOUD -> "$DRAGON_ICONS_URL/dragon_cloud.png"
    DragonType.MOUNTAIN -> "$DRAGON_ICONS_URL/dragon_mountain.png"
    DragonType.HEXTECH -> "$DRAGON_ICONS_URL/dragon_hextech.png"
    DragonType.CHEMTECH -> "$DRAGON_ICONS_URL/dragon_chemtech.png"
    DragonType.ELDER -> "$DRAGON_ICONS_URL/dragon_elder.png"
    DragonType.UNKNOWN -> "$DRAGON_ICONS_URL/dragon.png"
}
