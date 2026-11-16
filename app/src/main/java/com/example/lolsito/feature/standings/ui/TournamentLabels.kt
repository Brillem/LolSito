package com.example.lolsito.feature.standings.ui

import com.example.lolsito.feature.standings.domain.Tournament

/** Slug words that are league acronyms and read upper-cased ("lck" -> "LCK"). */
private val LEAGUE_ACRONYMS = setOf(
    "lck", "lpl", "lec", "lcs", "lcp", "lta", "msi", "cblol", "vcs", "pcs", "ljl", "emea",
)

/**
 * "lck_split_3_2026" -> "LCK Split 3 2026", "worlds_2026" -> "Worlds 2026". Words are split on
 * "_" and "-"; known league acronyms are upper-cased and every other word is capitalized.
 * Returns "" for a blank slug.
 */
fun formatTournamentSlug(slug: String): String =
    slug.split('_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            if (word.lowercase() in LEAGUE_ACRONYMS) word.uppercase()
            else word.replaceFirstChar { it.uppercaseChar() }
        }

/** Readable tournament name; falls back to the id when the slug is blank. */
val Tournament.label: String
    get() = formatTournamentSlug(slug).ifBlank { id }
