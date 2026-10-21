package com.example.lolsito.core.network

import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.network.dto.StrategyDto

/**
 * Maps the API strategy to a series format; null without a positive count. A missing type is
 * read as best-of only when the count is odd (the historical default); any other unrecognized
 * type is [SeriesType.UNKNOWN], which never ends a series early.
 */
fun StrategyDto?.toSeriesFormat(): SeriesFormat? {
    val count = this?.count?.takeIf { it > 0 } ?: return null
    val type = when (this.type) {
        "bestOf" -> SeriesType.BEST_OF
        "playAll" -> SeriesType.PLAY_ALL
        null -> if (count % 2 == 1) SeriesType.BEST_OF else SeriesType.UNKNOWN
        else -> SeriesType.UNKNOWN
    }
    return SeriesFormat(type, count)
}
