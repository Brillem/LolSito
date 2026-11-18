package com.example.lolsito.core.cache

/** A thread-safe cache that drops its least recently used entry once it holds more than [maxEntries]. */
class BoundedCache<K : Any, V : Any>(private val maxEntries: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxEntries
    }

    operator fun get(key: K): V? = synchronized(map) { map[key] }

    operator fun set(key: K, value: V) {
        synchronized(map) { map[key] = value }
    }
}
