package com.example.lolsito.core.datadragon

import com.example.lolsito.core.network.ApiConfig

private val PATCH = Regex("""^(\d+)\.(\d+)(?:\..*)?$""")

/**
 * Data Dragon version that serves the assets of game [patchVersion] (for example "16.20.826.3891"
 * in the livestats feed). Data Dragon names its versions "<season>.<split>.1" ("16.20.1"), so the
 * build numbers of the game patch are replaced by the ".1". Returns null for a missing or
 * unrecognized patch. A version Data Dragon has not published yet simply fails to load its assets.
 */
fun dataDragonVersion(patchVersion: String?): String? {
    val match = PATCH.matchEntire(patchVersion?.trim().orEmpty()) ?: return null
    val (season, split) = match.destructured
    return "$season.$split.1"
}

/** Icon of item [itemId] at Data Dragon [version], or null when the version is unknown. */
fun itemIconUrl(version: String?, itemId: Int): String? =
    version?.let { "${ApiConfig.DATA_DRAGON_BASE_URL}cdn/$it/img/item/$itemId.png" }

/** Portrait of champion [championId] (the feed's Data Dragon key, "KSante") at Data Dragon [version]; null when either is missing. */
fun championIconUrl(version: String?, championId: String?): String? {
    val id = championId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return version?.let { "${ApiConfig.DATA_DRAGON_BASE_URL}cdn/$it/img/champion/$id.png" }
}

/**
 * Names of the rune trees and keystones as Data Dragon's runesReforged.json has them. Bundled so
 * the runes show without a second network call; an id missing here (a rune added after this table)
 * is shown as its number by [runeName].
 */
private val RUNE_NAMES: Map<Int, String> = mapOf(
    8000 to "Precision",
    8100 to "Domination",
    8200 to "Sorcery",
    8300 to "Inspiration",
    8400 to "Resolve",
    8005 to "Press the Attack",
    8008 to "Lethal Tempo",
    8010 to "Conqueror",
    8021 to "Fleet Footwork",
    8112 to "Electrocute",
    8128 to "Dark Harvest",
    9923 to "Hail of Blades",
    8214 to "Summon Aery",
    8229 to "Arcane Comet",
    8230 to "Stormraider's Surge",
    8992 to "Deathfire Touch",
    8351 to "Glacial Augment",
    8360 to "Unsealed Spellbook",
    8369 to "First Strike",
    8437 to "Grasp of the Undying",
    8439 to "Aftershock",
    8465 to "Guardian",
)

/** Name of rune tree or keystone [id], or the number itself when unknown. */
fun runeName(id: Int): String = RUNE_NAMES[id] ?: id.toString()

/**
 * Icon path of every keystone as Data Dragon's runesReforged.json has it, relative to its unversioned
 * `cdn/img/` folder. Bundled with [RUNE_NAMES] so the rune shows without a second network call; a
 * keystone added after this table has no icon and [keystoneIconUrl] returns null and the screen falls back to its name or number.
 */
private val KEYSTONE_ICONS: Map<Int, String> = mapOf(
    8005 to "perk-images/Styles/Precision/PressTheAttack/PressTheAttack.png",
    8008 to "perk-images/Styles/Precision/LethalTempo/LethalTempoTemp.png",
    8010 to "perk-images/Styles/Precision/Conqueror/Conqueror.png",
    8021 to "perk-images/Styles/Precision/FleetFootwork/FleetFootwork.png",
    8112 to "perk-images/Styles/Domination/Electrocute/Electrocute.png",
    8128 to "perk-images/Styles/Domination/DarkHarvest/DarkHarvest.png",
    9923 to "perk-images/Styles/Domination/HailOfBlades/HailOfBlades.png",
    8214 to "perk-images/Styles/Sorcery/SummonAery/SummonAery.png",
    8229 to "perk-images/Styles/Sorcery/ArcaneComet/ArcaneComet.png",
    8230 to "perk-images/Styles/Sorcery/PhaseRush/StormraidersSurgeRuneIcon2.png",
    8992 to "perk-images/Styles/Sorcery/DeathfireTouch/DEATHFIRE_TOUCH_KEYSTONE.png",
    8351 to "perk-images/Styles/Inspiration/GlacialAugment/GlacialAugment.png",
    8360 to "perk-images/Styles/Inspiration/UnsealedSpellbook/UnsealedSpellbook.png",
    8369 to "perk-images/Styles/Inspiration/FirstStrike/FirstStrike.png",
    8437 to "perk-images/Styles/Resolve/GraspOfTheUndying/GraspOfTheUndying.png",
    8439 to "perk-images/Styles/Resolve/VeteranAftershock/VeteranAftershock.png",
    8465 to "perk-images/Styles/Resolve/Guardian/Guardian.png",
)

/** Ids of the keystones that have an icon in the bundled table. */
val KEYSTONE_IDS: Set<Int> = KEYSTONE_ICONS.keys

/** Icon of keystone [keystoneId] (not versioned, so no Data Dragon version is needed), or null when the keystone is unknown. */
fun keystoneIconUrl(keystoneId: Int): String? =
    KEYSTONE_ICONS[keystoneId]?.let { "${ApiConfig.DATA_DRAGON_BASE_URL}cdn/img/$it" }
