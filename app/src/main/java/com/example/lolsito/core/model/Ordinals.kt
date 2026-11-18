package com.example.lolsito.core.model

/** English ordinal: 1st, 2nd, 3rd, 4th, 11th, 12th, 13th, 21st... */
fun ordinalLabel(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
