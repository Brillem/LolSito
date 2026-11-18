package com.example.lolsito.feature.teams.data

import com.example.lolsito.core.network.LolEsportsApi
import com.example.lolsito.feature.teams.domain.DirectoryTeam
import com.example.lolsito.feature.teams.domain.TeamDirectoryRepository
import com.example.lolsito.feature.teams.domain.TeamSearchIndex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The full team list: requested the first time it is needed, mapped off the main thread and kept in memory,
 * with its search index, built once on [mapperDispatcher] too. A failed request is not kept, so asking again
 * requests it again.
 */
class TeamDirectoryRepositoryImpl(
    private val api: LolEsportsApi,
    private val mapperDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TeamDirectoryRepository {
    private val lock = Mutex()
    private var cached: List<DirectoryTeam>? = null
    private var index: TeamSearchIndex? = null

    override suspend fun getAllTeams(): List<DirectoryTeam> = lock.withLock { teamsLocked() }

    override suspend fun getSearchIndex(): TeamSearchIndex = lock.withLock {
        index ?: teamsLocked().let { teams -> withContext(mapperDispatcher) { TeamSearchIndex(teams) } }.also { index = it }
    }

    private suspend fun teamsLocked(): List<DirectoryTeam> =
        cached ?: withContext(mapperDispatcher) { api.getAllTeams().toDirectoryTeams() }.also { cached = it }
}
