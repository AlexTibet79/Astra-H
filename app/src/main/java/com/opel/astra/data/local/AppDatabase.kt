package com.opel.astra.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Dao
import com.opel.astra.domain.TripSummary
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val tripId: Long = 0,
    val startTimeTimestamp: Long = System.currentTimeMillis(),
    var endTimeTimestamp: Long? = null,
    var maxCoolantTemp: Int = 0,
    var maxRpm: Int = 0,
    var maxSpeed: Int = 0
)

@Entity(
    tableName = "telemetry_entries",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["tripId"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tripId"]) ]
)
data class TelemetryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val coolantTemp: Int,
    val engineRpm: Int,
    val vehicleSpeed: Int
)

@Dao
interface ObdDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("SELECT * FROM trips ORDER BY startTimeTimestamp DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query(
        """
        SELECT
            tripId AS id,
            startTimeTimestamp AS startTimestamp,
            endTimeTimestamp AS endTimestamp,
            maxCoolantTemp AS maxCoolantTemp,
            maxRpm AS maxRpm,
            maxSpeed AS maxSpeed
        FROM trips
        ORDER BY startTimeTimestamp DESC
        """
    )
    fun getTripSummaries(): Flow<List<TripSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetryBatch(entries: List<TelemetryEntryEntity>)

    @Query("SELECT * FROM telemetry_entries WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getTelemetryForTrip(tripId: Long): Flow<List<TelemetryEntryEntity>>
}

@Database(entities = [TripEntity::class, TelemetryEntryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun obdDao(): ObdDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "opel_astra_obd.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
