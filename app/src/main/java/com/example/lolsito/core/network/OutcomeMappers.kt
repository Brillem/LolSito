package com.example.lolsito.core.network

import com.example.lolsito.core.model.MatchOutcome

/** Maps the API team outcome; null when the match has no result yet or the value is unrecognized. */
fun String?.toOutcome(): MatchOutcome? = when (this) {
    "win" -> MatchOutcome.WIN
    "loss" -> MatchOutcome.LOSS
    else -> null
}
