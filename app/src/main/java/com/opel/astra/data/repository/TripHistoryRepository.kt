package com.opel.astra.data.repository

import com.opel.astra.data.local.AppDatabase
import com.opel.astra.domain.TripSummary
import kotlinx.coroutines.flow.Flow

class TripHistoryRepository(private val database: AppDatabase) {
    fun observeTrips(): Flow<List<TripSummary>> = database.obdDao().getTripSummaries()
}
