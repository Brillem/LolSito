package com.example.lolsito.feature.game.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerHealthTest {

    @Test
    fun `the fraction is the current health over the maximum`() {
        assertEquals(0.5f, PlayerHealth(500, 1000).fraction, 0.0001f)
        assertEquals(1f, PlayerHealth(1000, 1000).fraction, 0f)
        assertEquals(0f, PlayerHealth(0, 1000).fraction, 0f)
    }

    @Test
    fun `a zero or negative maximum gives an empty bar instead of dividing by zero`() {
        assertEquals(0f, PlayerHealth(300, 0).fraction, 0f)
        assertEquals(0f, PlayerHealth(0, 0).fraction, 0f)
        assertEquals(0f, PlayerHealth(300, -5).fraction, 0f)
    }

    @Test
    fun `the fraction stays between empty and full`() {
        assertEquals(1f, PlayerHealth(1200, 1000).fraction, 0f)
        assertEquals(0f, PlayerHealth(-50, 1000).fraction, 0f)
    }
}
