package com.example.lolsito.feature.game.ui

import com.example.lolsito.feature.game.domain.DragonType
import org.junit.Assert.assertEquals
import org.junit.Test

class DragonDisplayTest {

    @Test
    fun `every known dragon type has its own short label`() {
        assertEquals(
            listOf("Inf", "Ocn", "Cld", "Mtn", "Hex", "Chm", "Eld"),
            listOf(
                DragonType.INFERNAL, DragonType.OCEAN, DragonType.CLOUD, DragonType.MOUNTAIN,
                DragonType.HEXTECH, DragonType.CHEMTECH, DragonType.ELDER,
            ).map(::dragonLabel),
        )
    }

    @Test
    fun `an unknown dragon type is shown as a generic dragon`() {
        assertEquals("Drg", dragonLabel(DragonType.UNKNOWN))
        assertEquals("Dragon", dragonName(DragonType.UNKNOWN))
    }

    @Test
    fun `the description counts the dragons and names them in the order taken`() {
        assertEquals("No dragons", dragonsDescription(emptyList()))
        assertEquals("1 dragon: Cloud", dragonsDescription(listOf(DragonType.CLOUD)))
        assertEquals(
            "3 dragons: Ocean, Dragon, Infernal",
            dragonsDescription(listOf(DragonType.OCEAN, DragonType.UNKNOWN, DragonType.INFERNAL)),
        )
    }

    @Test
    fun `every known dragon type has the game's own icon, named as the feed names the type`() {
        val base = "https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons"
        assertEquals(
            listOf("infernal", "ocean", "cloud", "mountain", "hextech", "chemtech", "elder").map { "$base/dragon_$it.png" },
            listOf(
                DragonType.INFERNAL, DragonType.OCEAN, DragonType.CLOUD, DragonType.MOUNTAIN,
                DragonType.HEXTECH, DragonType.CHEMTECH, DragonType.ELDER,
            ).map(::dragonIconUrl),
        )
    }

    @Test
    fun `an unknown dragon type uses the generic dragon icon`() {
        assertEquals(
            "https://raw.communitydragon.org/latest/game/assets/ux/minimap/icons/dragon.png",
            dragonIconUrl(DragonType.UNKNOWN),
        )
    }
}
