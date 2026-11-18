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
    encodeRecords(teams.map { team -> listOf(team.id, team.slug, team.name, team.code, team.imageUrl.orEmpty()) })

/**
 * Inverse of [encodeFavoriteTeams]. A value that does not parse (wrong field count, bad escape) has
 * no favorites at all, rather than a partial list; a team repeated by id keeps its first occurrence.
 */
internal fun decodeFavoriteTeams(raw: String): List<FavoriteTeam> =
    decodeRecords(raw, FIELD_COUNT) { fields -> FavoriteTeam(fields[0], fields[1], fields[2], fields[3], fields[4].ifEmpty { null }) }
        .distinctBy { it.id }

/** Records of string fields as one string; see [encodeFavoriteTeams] for the format. */
internal fun encodeRecords(records: List<List<String>>): String =
    records.joinToString(RECORD_SEPARATOR.toString()) { fields ->
        fields.joinToString(FIELD_SEPARATOR.toString()) { URLEncoder.encode(it, Charsets.UTF_8.name()) }
    }

/**
 * Inverse of [encodeRecords], each record turned by [build]. Every record must have [fieldCount] fields and
 * [build] may throw [IllegalArgumentException]; either way the whole value reads as the empty list.
 */
internal fun <T> decodeRecords(raw: String, fieldCount: Int, build: (List<String>) -> T): List<T> = try {
    raw.split(RECORD_SEPARATOR)
        .filter { it.isNotEmpty() }
        .map { record ->
            val fields = record.split(FIELD_SEPARATOR).map { URLDecoder.decode(it, Charsets.UTF_8.name()) }
            require(fields.size == fieldCount) { "unexpected field count" }
            build(fields)
        }
} catch (e: IllegalArgumentException) {
    emptyList()
}
