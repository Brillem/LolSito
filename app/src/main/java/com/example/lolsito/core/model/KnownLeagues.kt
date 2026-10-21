package com.example.lolsito.core.model

/** Readable names for well-known league ids, used as a fallback while getLeagues is unavailable. */
object KnownLeagues {
    private val names = mapOf(
        "98767975604431411" to "Worlds",
        "98767991325878492" to "MSI",
        "113464388705111224" to "First Stand",
        "98767991310872058" to "LCK",
        "98767991314006698" to "LPL",
        "98767991302996019" to "LEC",
        "113476371197627891" to "LCP",
        "113470291645289904" to "LTA North",
        "113475181634818701" to "LTA South",
        "98767991332355509" to "CBLOL",
    )

    fun nameFor(leagueId: String): String? = names[leagueId]
}
