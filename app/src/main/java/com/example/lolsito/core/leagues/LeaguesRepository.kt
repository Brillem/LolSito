package com.example.lolsito.core.leagues

import com.example.lolsito.core.model.League

/** Port for the leagues list, shared by every feature that needs it (Schedule and Standings). */
fun interface LeaguesRepository {
    suspend fun getLeagues(): List<League>
}
