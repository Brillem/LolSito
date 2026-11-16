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

    @Test
    fun `going from one tab to another is a tab switch`() {
        assertTrue(isTabSwitch(Routes.SCHEDULE, Routes.STANDINGS))
        assertTrue(isTabSwitch(Routes.TEAMS, Routes.SCHEDULE))
    }

    @Test
    fun `opening or closing a pushed screen is not a tab switch`() {
        assertFalse(isTabSwitch(Routes.SCHEDULE, Routes.MATCH))
        assertFalse(isTabSwitch(Routes.MATCH, Routes.SCHEDULE))
        assertFalse(isTabSwitch(Routes.MATCH, Routes.GAME))
    }

    @Test
    fun `without a known route on either side it is not a tab switch`() {
        assertFalse(isTabSwitch(null, Routes.SCHEDULE))
        assertFalse(isTabSwitch(Routes.SCHEDULE, null))
    }
}
