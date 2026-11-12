package com.example.lolsito.feature.match.domain

enum class WatchProvider { YOUTUBE, TWITCH }

/** A playable VOD or live stream: the [provider] it lives on and its video id or channel name. */
data class WatchLink(val provider: WatchProvider, val parameter: String, val locale: String?) {
    /** Web URL that opens in the provider app when installed, otherwise in the browser. */
    val url: String
        get() = when (provider) {
            WatchProvider.YOUTUBE -> "https://www.youtube.com/watch?v=$parameter"
            WatchProvider.TWITCH -> "https://www.twitch.tv/$parameter"
        }
}

/** Maps the API provider name to a known provider; null for anything else. */
fun String?.toWatchProvider(): WatchProvider? = when (this?.lowercase()) {
    "youtube" -> WatchProvider.YOUTUBE
    "twitch" -> WatchProvider.TWITCH
    else -> null
}

/** Prefers an English link, otherwise the first one available; null when the list is empty. */
fun pickWatchLink(links: List<WatchLink>): WatchLink? =
    links.firstOrNull { it.locale?.startsWith("en", ignoreCase = true) == true } ?: links.firstOrNull()
