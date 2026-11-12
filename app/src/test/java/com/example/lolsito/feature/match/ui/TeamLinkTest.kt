package com.example.lolsito.feature.match.ui

import com.example.lolsito.core.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamLinkTest {

    private fun team(id: String?, code: String = "T1") = Team("Name", code, null, null, null, id = id)

    @Test
    fun `a team with an id is tappable with that id`() {
        assertEquals("123", team("123").profileId())
    }

    @Test
    fun `a team without an id is not tappable`() {
        assertNull(team(null).profileId())
        assertNull(team("").profileId())
    }

    @Test
    fun `a TBD placeholder is not tappable`() {
        assertNull(team("0", code = "TBD").profileId())
        assertNull(team("0").profileId())
    }

    @Test
    fun `a TBD code is not tappable even with an id`() {
        assertNull(team("55", code = "TBD").profileId())
    }
}
