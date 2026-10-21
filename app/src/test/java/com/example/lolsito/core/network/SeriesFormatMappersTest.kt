package com.example.lolsito.core.network

import com.example.lolsito.core.model.SeriesFormat
import com.example.lolsito.core.model.SeriesType
import com.example.lolsito.core.network.dto.StrategyDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesFormatMappersTest {

    @Test
    fun `known strategy types map to their series type`() {
        assertEquals(SeriesFormat(SeriesType.BEST_OF, 5), StrategyDto("bestOf", 5).toSeriesFormat())
        assertEquals(SeriesFormat(SeriesType.PLAY_ALL, 2), StrategyDto("playAll", 2).toSeriesFormat())
    }

    @Test
    fun `missing type with an odd count is treated as best of`() {
        assertEquals(SeriesFormat(SeriesType.BEST_OF, 3), StrategyDto(null, 3).toSeriesFormat())
    }

    @Test
    fun `missing type with an even count or an unknown type is unknown`() {
        assertEquals(SeriesFormat(SeriesType.UNKNOWN, 2), StrategyDto(null, 2).toSeriesFormat())
        assertEquals(SeriesFormat(SeriesType.UNKNOWN, 3), StrategyDto("swiss", 3).toSeriesFormat())
    }

    @Test
    fun `missing strategy or non-positive count has no format`() {
        assertNull((null as StrategyDto?).toSeriesFormat())
        assertNull(StrategyDto("bestOf", null).toSeriesFormat())
        assertNull(StrategyDto("bestOf", 0).toSeriesFormat())
    }
}
