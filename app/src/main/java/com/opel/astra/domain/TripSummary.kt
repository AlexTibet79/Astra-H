package com.opel.astra.domain

data class TripSummary(
    val id: Long,
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val maxCoolantTemp: Int,
    val maxRpm: Int,
    val maxSpeed: Int
) {
    val durationMinutes: Long
        get() = endTimestamp?.let { (it - startTimestamp) / 60_000 } ?: 0L
}
