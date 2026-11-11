package com.example.lolsito.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarTest {

    @Test
    fun `without a back stack entry the start destination drives the bottom bar`() {
        assertEquals(START_ROUTE, bottomBarRoute(currentRoute = null))
        assertTrue(showsBottomBar(bottomBarRoute(currentRoute = null)))
    }

    @Test
    fun `current route wins once there is a back stack entry`() {
        assertEquals(Routes.STANDINGS, bottomBarRoute(Routes.STANDINGS))
    }

    @Test
    fun `bottom bar shows on top-level tabs only`() {
        assertTrue(showsBottomBar(Routes.SCHEDULE))
        assertTrue(showsBottomBar(Routes.STANDINGS))
        assertFalse(showsBottomBar(Routes.MATCH))
        assertFalse(showsBottomBar(Routes.GAME))
    }
}
