package com.example.lolsito.core.model

/**
 * Builds list keys that never collide: each base gets its occurrence ordinal appended, so the
 * first "a" is "a#0", the second "a#1", and so on. One instance covers one list.
 */
class UniqueKeys {
    private val seen = mutableMapOf<String, Int>()

    fun next(base: String): String {
        val ordinal = seen.getOrDefault(base, 0)
        seen[base] = ordinal + 1
        return "$base#$ordinal"
    }
}
