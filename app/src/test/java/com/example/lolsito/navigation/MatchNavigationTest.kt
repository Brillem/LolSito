package com.example.lolsito.navigation

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchNavigationTest {

    @Test
    fun `navigation starts only from a resumed entry`() {
        assertTrue(canNavigateFrom(Lifecycle.State.RESUMED))
    }

    @Test
    fun `an entry that is already leaving does not navigate again`() {
        assertFalse(canNavigateFrom(Lifecycle.State.STARTED))
        assertFalse(canNavigateFrom(Lifecycle.State.CREATED))
        assertFalse(canNavigateFrom(Lifecycle.State.DESTROYED))
    }
}
