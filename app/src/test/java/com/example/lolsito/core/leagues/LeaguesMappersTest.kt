package com.example.lolsito.core.leagues

import com.example.lolsito.core.network.LolEsportsJson
import com.example.lolsito.core.network.dto.LeaguesResponse
import com.example.lolsito.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaguesMappersTest {

    private val leagues = LolEsportsJson.decodeFromString<LeaguesResponse>(
        Fixtures.read("getLeagues.json")
    ).data.leagues

    @Test
    fun `leagues exclude hidden and are sorted with worlds first`() {
        val mapped = leagues.toDomainLeagues()
        assertTrue(mapped.isNotEmpty())
        assertTrue(mapped.size < 51)
        assertEquals("worlds", mapped.first().slug)
        assertEquals("98767975604431411", mapped.first().id)
        assertTrue(mapped.first().imageUrl!!.startsWith("https://"))
    }
}
