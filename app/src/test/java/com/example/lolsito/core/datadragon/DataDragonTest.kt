package com.example.lolsito.core.datadragon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataDragonTest {

    @Test
    fun `a game patch maps to the Data Dragon version of its season and split`() {
        assertEquals("16.20.1", dataDragonVersion("16.20.826.3891"))
        assertEquals("15.9.1", dataDragonVersion("15.9.712.3456"))
        assertEquals("16.22.1", dataDragonVersion("16.22"))
    }

    @Test
    fun `a missing or malformed patch has no Data Dragon version`() {
        assertNull(dataDragonVersion(null))
        assertNull(dataDragonVersion(""))
        assertNull(dataDragonVersion("  "))
        assertNull(dataDragonVersion("16"))
        assertNull(dataDragonVersion("latest"))
        assertNull(dataDragonVersion("16.x.1"))
    }

    @Test
    fun `item icon url points at the item image of that version`() {
        assertEquals(
            "https://ddragon.leagueoflegends.com/cdn/16.20.1/img/item/3047.png",
            itemIconUrl("16.20.1", 3047),
        )
    }

    @Test
    fun `item icon url is null without a version`() {
        assertNull(itemIconUrl(null, 3047))
    }

    @Test
    fun `rune trees and keystones have their names`() {
        assertEquals("Precision", runeName(8000))
        assertEquals("Domination", runeName(8100))
        assertEquals("Sorcery", runeName(8200))
        assertEquals("Inspiration", runeName(8300))
        assertEquals("Resolve", runeName(8400))
        assertEquals("Conqueror", runeName(8010))
        assertEquals("Electrocute", runeName(8112))
        assertEquals("Summon Aery", runeName(8214))
        assertEquals("Grasp of the Undying", runeName(8437))
        assertEquals("First Strike", runeName(8369))
    }

    @Test
    fun `an unknown rune id falls back to the number`() {
        assertEquals("1234", runeName(1234))
    }
}
