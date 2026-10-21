package com.example.lolsito.core.leagues

import com.example.lolsito.core.model.League
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the first successful [source] result in memory for the app's lifetime. Concurrent callers
 * wait on one lock, so only one request is in flight and the others reuse its result. A failure
 * (or a cancelled caller) caches nothing, so the next call fetches again.
 */
class CachedLeaguesRepository(private val source: LeaguesRepository) : LeaguesRepository {
    private val mutex = Mutex()

    @Volatile
    private var cached: List<League>? = null

    override suspend fun getLeagues(): List<League> {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: source.getLeagues().also { cached = it }
        }
    }
}
