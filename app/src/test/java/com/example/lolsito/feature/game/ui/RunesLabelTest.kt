package com.example.lolsito.feature.game.ui

import com.example.lolsito.feature.game.domain.PlayerDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class RunesLabelTest {

    private val base = PlayerDetails(
        itemIds = emptyList(),
        primaryStyleId = 8000,
        secondaryStyleId = 8300,
        keystoneId = 8010,
        damageShare = 0.0,
        killParticipation = 0.0,
        totalGold = 0,
        wardsPlaced = 0,
        wardsDestroyed = 0,
    )

    @Test
    fun `shows both trees and the keystone by name`() {
        assertEquals("Precision / Inspiration - Conqueror", runesLabel(base))
    }

    @Test
    fun `unknown ids show as numbers`() {
        assertEquals("Precision / 9999 - 1234", runesLabel(base.copy(secondaryStyleId = 9999, keystoneId = 1234)))
    }

    @Test
    fun `parts the feed did not send are left out`() {
        assertEquals("Precision - Conqueror", runesLabel(base.copy(secondaryStyleId = null)))
        assertEquals("Precision / Inspiration", runesLabel(base.copy(keystoneId = null)))
        assertEquals("-", runesLabel(base.copy(primaryStyleId = null, secondaryStyleId = null, keystoneId = null)))
    }
}
