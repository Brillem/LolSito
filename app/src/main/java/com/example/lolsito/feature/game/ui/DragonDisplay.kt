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
