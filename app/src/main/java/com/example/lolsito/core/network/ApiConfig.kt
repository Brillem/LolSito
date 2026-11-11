package com.example.lolsito.core.network

object ApiConfig {
    const val BASE_URL = "https://esports-api.lolesports.com/persisted/gw/"
    const val API_KEY = "0TvQnueqKa5mxJntVWt0w4LpLfEkrV1Ta8rQBb9Z"
    const val LOCALE = "en-US"

    /** Livestats feed: takes no API key and no locale. */
    const val LIVE_STATS_BASE_URL = "https://feed.lolesports.com/livestats/v1/"

    /** Data Dragon CDN: static assets (item icons) used by the game stats screen; no API key. */
    const val DATA_DRAGON_BASE_URL = "https://ddragon.leagueoflegends.com/"
}
