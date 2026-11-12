package com.example.lolsito.core.preferences

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A team the user starred. It keeps what the Teams tab needs to list and open it without a request
 * ([id] opens the profile). Schedule only knows team names, so it recognizes a favorite by [name].
 */
data class FavoriteTeam(
    val id: String,
    val slug: String,
    val name: String,
    val code: String,
    val imageUrl: String?,
)

private const val FIELD_SEPARATOR = ','
private const val RECORD_SEPARATOR = ';'
private const val FIELD_COUNT = 5

/**
 * One string for the whole list: fields URL-encoded (so any character in a name is safe, and neither
 * separator can appear inside a field), joined by `,`, records joined by `;`. A missing logo is an
 * empty field. The empty list is the empty string.
 */
internal fun encodeFavoriteTeams(teams: List<FavoriteTeam>): String =
    teams.joinToString(RECORD_SEPARATOR.toString()) { team ->
        listOf(team.id, team.slug, team.name, team.code, team.imageUrl.orEmpty())
            .joinToString(FIELD_SEPARATOR.toString()) { URLEncoder.encode(it, Charsets.UTF_8.name()) }
    }

/**
 * Inverse of [encodeFavoriteTeams]. A value that does not parse (wrong field count, bad escape) has
 * no favorites at all, rather than a partial list; a team repeated by id keeps its first occurrence.
 */
internal fun decodeFavoriteTeams(raw: String): List<FavoriteTeam> = try {
    raw.split(RECORD_SEPARATOR)
        .filter { it.isNotEmpty() }
        .map { record ->
            val fields = record.split(FIELD_SEPARATOR).map { URLDecoder.decode(it, Charsets.UTF_8.name()) }
            require(fields.size == FIELD_COUNT) { "unexpected field count" }
            FavoriteTeam(fields[0], fields[1], fields[2], fields[3], fields[4].ifEmpty { null })
        }
        .distinctBy { it.id }
} catch (e: IllegalArgumentException) {
    emptyList()
}
