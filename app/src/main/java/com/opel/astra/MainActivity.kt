package com.opel.astra

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opel.astra.data.local.AppDatabase
import com.opel.astra.data.repository.TripHistoryRepository
import com.opel.astra.domain.TripSummary
import com.opel.astra.service.Elm327UsbService
import com.opel.astra.service.ObdTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpelAstraApp()
        }
    }
}

sealed class AppDestination(val route: String, val label: String) {
    data object Dashboard : AppDestination("dashboard", "Dashboard")
    data object Trips : AppDestination("trips", "Trips")
    data object Dtc : AppDestination("dtc", "DTC")
    data object Adaptation : AppDestination("adaptation", "Adaptation")
    data object Settings : AppDestination("settings", "Settings")
}

@Composable
fun OpelAstraApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val telemetryState = rememberObdTelemetryState(context)
    val db = remember { AppDatabase.getDatabase(context) }
    val repository = remember { TripHistoryRepository(db) }
    val trips by repository.observeTrips().collectAsState(initial = emptyList())

    Scaffold(
        containerColor = Color(0xFF111111),
        bottomBar = {
            OpelBottomBar(navController)
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(AppDestination.Dashboard.route) {
                DashboardScreen(telemetryState = telemetryState)
            }
            composable(AppDestination.Trips.route) {
                TripsHistoryScreen(trips = trips)
            }
            composable(AppDestination.Dtc.route) {
                DtcScreen()
            }
            composable(AppDestination.Adaptation.route) {
                AdaptationScreen()
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun OpelBottomBar(navController: NavHostController) {
    val items = listOf(
        AppDestination.Dashboard,
        AppDestination.Trips,
        AppDestination.Dtc,
        AppDestination.Adaptation,
        AppDestination.Settings
    )
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    NavigationBar(
        containerColor = Color(0xFF1A1A1A),
        tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                label = { Text(item.label, fontSize = 10.sp) },
                icon = { }
            )
        }
    }
}

@Composable
fun DashboardScreen(telemetryState: State<ObdTelemetry>) {
    val telemetry = telemetryState.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFA81C1C), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "OPEL ASTRA H - DIAGNOSTICS",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricCard(title = "Engine temp", value = "${telemetry.coolantTemp}°C", accent = Color(0xFFF1B738), modifier = Modifier.weight(1f))
            MetricCard(title = "RPM", value = telemetry.engineRpm.toString(), accent = Color(0xFF33B5E5), modifier = Modifier.weight(1f))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricCard(title = "Speed", value = "${telemetry.vehicleSpeed} km/h", accent = Color(0xFF00C853), modifier = Modifier.weight(1f))
            MetricCard(title = "Status", value = if (telemetry.isConnected) "LIVE" else "WAITING", accent = if (telemetry.isConnected) Color(0xFF4CAF50) else Color(0xFFE53935), modifier = Modifier.weight(1f))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Live data", color = Color(0xFFF1B738), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow("Coolant", "${telemetry.coolantTemp}°C")
                InfoRow("RPM", telemetry.engineRpm.toString())
                InfoRow("Speed", "${telemetry.vehicleSpeed} km/h")
                InfoRow("Error", telemetry.errorMessage ?: "system nominal")
            }
        }
    }
}

@Composable
fun TripsHistoryScreen(trips: List<TripSummary>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Trip history",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(trips) { trip ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Trip #${trip.id}", color = Color(0xFFF1B738), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "${formatDate(trip.startTimestamp)} → ${trip.endTimestamp?.let { formatDate(it) } ?: "in progress"}", color = Color(0xFFE0E0E0))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Duration: ${trip.durationMinutes} min | Max temp: ${trip.maxCoolantTemp}°C | Max RPM: ${trip.maxRpm} | Max speed: ${trip.maxSpeed} km/h", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun DtcScreen() {
    val dtcList = listOf(
        "P0100 - Mass Air Flow circuit",
        "P0116 - Coolant temperature sensor",
        "P0301 - Cylinder 1 misfire",
        "P0420 - Catalytic efficiency below threshold",
        "U0100 - Lost communication with ECU"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "DTC diagnostics", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        dtcList.forEach { code ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = code, modifier = Modifier.padding(16.dp), color = Color(0xFFF1B738))
            }
        }
    }
}

@Composable
fun AdaptationScreen() {
    val rows = listOf(
        "Twinport adaptation" to "enabled",
        "Idle stabilization" to "0.4°",
        "Throttle learning" to "complete",
        "Fuel trim correction" to "+3.2%",
        "Injector balance" to "optimized"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Adaptation", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        rows.forEach { (name, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1B1B), RoundedCornerShape(10.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, color = Color.White)
                Text(value, color = Color(0xFFF1B738), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        val settings = listOf(
            "USB reconnect on attach" to "enabled",
            "Overlay visible" to "on",
            "Background logging" to "5s flush",
            "Notification channel" to "active"
        )

        settings.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1B1B), RoundedCornerShape(10.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = Color.White)
                Text(value, color = Color(0xFFF1B738), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFFBDBDBD), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFBDBDBD))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun rememberObdTelemetryState(context: Context): State<ObdTelemetry> {
    val telemetryState = remember { mutableStateOf(ObdTelemetry()) }

    DisposableEffect(context) {
        var boundService: Elm327UsbService? = null

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                boundService = (service as Elm327UsbService.LocalBinder).getService()
                CoroutineScope(Dispatchers.Main).launch {
                    boundService?.telemetry?.collect { telemetryState.value = it }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }

        val intent = Intent(context, Elm327UsbService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            try {
                context.unbindService(connection)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    return telemetryState
}

private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}
