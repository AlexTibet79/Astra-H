package com.opel.astra

import com.opel.astra.domain.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class TripHistoryRepositoryTest {
    @Test
    fun `trip summaries are ordered newest first`() {
        val trips = listOf(
            TripSummary(
                id = 1,
                startTimestamp = 1000L,
                endTimestamp = 2000L,
                maxCoolantTemp = 95,
                maxRpm = 4200,
                maxSpeed = 120
            ),
            TripSummary(
                id = 2,
                startTimestamp = 5000L,
                endTimestamp = 6500L,
                maxCoolantTemp = 101,
                maxRpm = 5100,
                maxSpeed = 145
            )
        )

        val sorted = trips.sortedByDescending { it.startTimestamp }

        assertEquals(2L, sorted.first().id)
        assertEquals(145, sorted.first().maxSpeed)
    }
}
