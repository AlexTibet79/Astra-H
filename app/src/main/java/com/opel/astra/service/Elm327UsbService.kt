package com.opel.astra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.opel.astra.data.local.AppDatabase
import com.opel.astra.data.local.TelemetryEntryEntity
import com.opel.astra.data.local.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException

data class ObdTelemetry(
    val coolantTemp: Int = 0,
    val engineRpm: Int = 0,
    val vehicleSpeed: Int = 0,
    val isConnected: Boolean = false,
    val errorMessage: String? = null
)

class Elm327UsbService : LifecycleService() {
    private val binder = LocalBinder()
    private var usbPort: UsbSerialPort? = null
    private lateinit var db: AppDatabase
    private var currentTripId: Long = 0L
    private var currentTrip: TripEntity? = null
    private val telemetryBuffer = mutableListOf<TelemetryEntryEntity>()
    private var batchFlushJob: Job? = null

    private val _telemetry = MutableStateFlow(ObdTelemetry())
    val telemetry: StateFlow<ObdTelemetry> = _telemetry.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): Elm327UsbService = this@Elm327UsbService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Инициализация ELM327 USB..."))

        lifecycleScope.launch(Dispatchers.IO) {
            val newTrip = TripEntity()
            currentTripId = db.obdDao().insertTrip(newTrip)
            currentTrip = newTrip.copy(tripId = currentTripId)
            startBatchFlushing()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        lifecycleScope.launch(Dispatchers.IO) {
            connectAndPollUsb()
        }
        return START_STICKY
    }

    private suspend fun connectAndPollUsb() = withContext(Dispatchers.IO) {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        if (availableDrivers.isEmpty()) {
            updateError("USB ELM327 не найден")
            return@withContext
        }

        val driver = availableDrivers[0]
        val connection = usbManager.openDevice(driver.device) ?: run {
            updateError("Ошибка доступа к USB")
            return@withContext
        }

        usbPort = driver.ports[0].apply {
            open(connection)
            setParameters(38400, 8, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1)
        }

        try {
            sendElmCommand("AT Z")
            delay(1000)
            sendElmCommand("AT E0")
            sendElmCommand("AT SP 0")

            while (isActive) {
                val temp = parseCoolantTemp(sendElmCommand("0105"))
                val rpm = parseRpm(sendElmCommand("010C"))
                val speed = parseSpeed(sendElmCommand("010D"))

                _telemetry.value = ObdTelemetry(temp, rpm, speed, true, null)
                recordTelemetryPoint(temp, rpm, speed)
                updateNotification("ОЖ: $temp°C | $rpm RPM | $speed км/ч")
                delay(200)
            }
        } catch (e: Exception) {
            updateError("Сбой: ${e.localizedMessage}")
        } finally {
            closeUsbPort()
        }
    }

    private fun recordTelemetryPoint(coolant: Int, rpm: Int, speed: Int) {
        if (currentTripId == 0L) return

        synchronized(telemetryBuffer) {
            telemetryBuffer.add(
                TelemetryEntryEntity(
                    tripId = currentTripId,
                    coolantTemp = coolant,
                    engineRpm = rpm,
                    vehicleSpeed = speed
                )
            )
        }

        currentTrip?.let { trip ->
            var updated = false

            if (coolant > trip.maxCoolantTemp) {
                trip.maxCoolantTemp = coolant
                updated = true
            }
            if (rpm > trip.maxRpm) {
                trip.maxRpm = rpm
                updated = true
            }
            if (speed > trip.maxSpeed) {
                trip.maxSpeed = speed
                updated = true
            }

            if (updated) {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.obdDao().updateTrip(trip)
                }
            }
        }
    }

    private fun startBatchFlushing() {
        batchFlushJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5000)
                flushBufferToDatabase()
            }
        }
    }

    private suspend fun flushBufferToDatabase() = withContext(Dispatchers.IO) {
        val entriesToSave = synchronized(telemetryBuffer) {
            if (telemetryBuffer.isEmpty()) return@withContext
            val list = ArrayList(telemetryBuffer)
            telemetryBuffer.clear()
            list
        }

        db.obdDao().insertTelemetryBatch(entriesToSave)
    }

    private fun sendElmCommand(command: String): String {
        val port = usbPort ?: return ""
        port.write((command + "\r").toByteArray(Charsets.US_ASCII), 1000)

        val buffer = ByteArray(256)
        var response = ""
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < 1000) {
            val len = port.read(buffer, 200)
            if (len > 0) {
                response += String(buffer, 0, len, Charsets.US_ASCII)
                if (response.contains(">")) break
            }
        }

        return response.replace(">", "").trim()
    }

    private fun parseCoolantTemp(raw: String): Int {
        val clean = raw.replace(" ", "").uppercase()

        if (clean.contains("4105")) {
            val hex = clean.substringAfter("4105").take(2)
            return (hex.toIntOrNull(16) ?: 40) - 40
        }

        return _telemetry.value.coolantTemp
    }

    private fun parseRpm(raw: String): Int {
        val clean = raw.replace(" ", "").uppercase()

        if (clean.contains("410C")) {
            val hex = clean.substringAfter("410C").take(4)
            val bytes = hex.chunked(2).mapNotNull { it.toIntOrNull(16) }
            if (bytes.size >= 2) return ((bytes[0] * 256) + bytes[1]) / 4
        }

        return _telemetry.value.engineRpm
    }

    private fun parseSpeed(raw: String): Int {
        val clean = raw.replace(" ", "").uppercase()

        if (clean.contains("410D")) {
            val hex = clean.substringAfter("410D").take(2)
            return hex.toIntOrNull(16) ?: 0
        }

        return _telemetry.value.vehicleSpeed
    }

    private fun updateError(msg: String) {
        _telemetry.value = _telemetry.value.copy(isConnected = false, errorMessage = msg)
        updateNotification("Ошибка: $msg")
    }

    private fun closeUsbPort() {
        try {
            usbPort?.close()
        } catch (_: IOException) {
        }
        usbPort = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "OBD2 Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Opel Astra OBD2 Service")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        runBlocking(Dispatchers.IO) {
            batchFlushJob?.cancel()
            flushBufferToDatabase()
            currentTrip?.let {
                it.endTimeTimestamp = System.currentTimeMillis()
                db.obdDao().updateTrip(it)
            }
        }

        closeUsbPort()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "obd2_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
