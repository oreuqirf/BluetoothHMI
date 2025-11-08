package com.example.bluetoothhmi.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
// --- Imports de Iconos ---
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
// ---
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.bluetoothhmi.connection.ConnectionState
import com.example.bluetoothhmi.data.CommandProtocol
import com.example.bluetoothhmi.data.MyBluetoothViewModel
import com.example.bluetoothhmi.data.SensorData
import com.example.bluetoothhmi.data.GpsData
import com.example.bluetoothhmi.components.RealTimeLineChart
import kotlinx.coroutines.delay
// --- Imports para el Timestamp ---
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
// ---
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen( // <-- Este es el Dashboard de Gráfico/Tiempo Real
    navController: NavController,
    viewModel: MyBluetoothViewModel
) {
    val sensorData by viewModel.sensorData.collectAsState()
    val gpsData by viewModel.gpsData.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current // <-- Contexto para el Toast

    // --- ESTADO DEL GRÁFICO ---
    val analogHistory = remember { mutableStateListOf<Float>() }
    val maxHistoryPoints = 60
    var lastSampleTimestamp by remember { mutableStateOf<Long?>(null) }
    // Estado para saber qué canal estamos viendo (0=AI0, 1=AI1, etc.)
    var selectedChannelIndex by remember { mutableStateOf(0) }
    val analogChannelNames = listOf("AI-0", "AI-1", "AI-2", "AI-3")
    // ---


    // --- Sondeo (Polling) de Sensores ---
    LaunchedEffect(key1 = true) {
        while (true) {
            if (viewModel.connectionState.value is ConnectionState.Connected) {
                viewModel.sendData(CommandProtocol.GET_SENSORS)
            }
            delay(1000) // 1 segundo
        }
    }

    // --- Actualización del Historial Y TIMESTAMP ---
    LaunchedEffect(sensorData) {
        sensorData?.let { data ->
            // Actualiza el timestamp
            lastSampleTimestamp = System.currentTimeMillis()

            // Actualiza el historial del gráfico (usando el canal seleccionado)
            if (data.analogInputs.size > selectedChannelIndex) {
                val newPoint = data.analogInputs[selectedChannelIndex]
                analogHistory.add(newPoint)

                while (analogHistory.size > maxHistoryPoints) {
                    analogHistory.removeAt(0)
                }
            }
        }
    }

    // Limpia el historial si el usuario cambia de canal
    LaunchedEffect(selectedChannelIndex) {
        analogHistory.clear()
        lastSampleTimestamp = null // Resetea el timestamp también
    }

    // --- Guardián de Conexión ---
    // Si la conexión se pierde, vuelve al Splash Screen
    LaunchedEffect(connectionState) {
        // Si el estado DEJA de ser 'Connected' (es Idle o Error)
        if (connectionState !is ConnectionState.Connected) {

            // Si fue un Error (pérdida de señal, sleep del dispositivo)
            if (connectionState is ConnectionState.Error) {
                Toast.makeText(context, "Conexión perdida. Volviendo al inicio.", Toast.LENGTH_LONG).show()
                delay(1000) // Da tiempo al usuario para leer el Toast
            }

            // Navega de vuelta al Splash Screen (reinicia la app)
            navController.navigate("splash_screen") {
                // Limpia toda la pila de navegación
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard en Tiempo Real") }
                // Botones de navegación movidos abajo
            )
        }
    ) { padding ->
        // --- ESTRUCTURA DE LAYOUT CON BOTONES FIJOS ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp) // Padding lateral
        ) {
            // --- 1. ÁREA DE SCROLL ---
            Column(
                modifier = Modifier
                    .weight(1f) // Ocupa todo el espacio disponible
                    .verticalScroll(rememberScrollState())
            ) {

                // --- Tarjeta de Gráfico (Actualizada con Pestañas) ---
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.padding(vertical = 16.dp)) {
                        Text(
                            text = "Histórico Sensor (${analogChannelNames[selectedChannelIndex]})",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(Modifier.height(8.dp))

                        // --- Pestañas de Selección de Canal ---
                        TabRow(selectedTabIndex = selectedChannelIndex) {
                            analogChannelNames.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedChannelIndex == index,
                                    onClick = { selectedChannelIndex = index },
                                    text = { Text(title) }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        RealTimeLineChart(
                            data = analogHistory.toList(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(horizontal = 16.dp)
                        )

                        // --- Metadatos del Gráfico ---
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Última muestra: ${formatLocalTimestamp(lastSampleTimestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            Text(
                                text = "Muestras: ${analogHistory.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // --- Tarjeta de Batería ---
                BatteryStatusCard(sensorData = sensorData)

                // --- Tarjeta de Entradas Digitales ---
                DigitalInputsCard(sensorData = sensorData)

                // --- Tarjeta de Salidas Digitales (Interactiva) ---
                DigitalOutputsCard(
                    sensorData = sensorData,
                    onSetOutput = viewModel::onSetDigitalOutput
                )

                // --- Tarjeta de Entradas Analógicas ---
                AnalogInputsCard(sensorData = sensorData)

                // --- Tarjeta de GPS (Actualización manual) ---
                GpsCard(
                    gpsData = gpsData,
                    onClick = { viewModel.readGpsData() }
                )

                Spacer(Modifier.height(16.dp)) // Espacio al final del scroll
            }

            // --- 2. BOTONES FIJOS INFERIORES ---

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    // No necesitamos detener el sondeo, ya que el 'LaunchedEffect'
                    // se detendrá automáticamente cuando salgamos de esta pantalla.
                    navController.navigate("device_data_screen")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configuración")
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.disconnectDevice() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Desconectar")
            }

            Spacer(Modifier.height(8.dp)) // Padding inferior
        }
    }
}

// --- Tarjeta de Batería ---
@Composable
private fun BatteryStatusCard(sensorData: SensorData?) {
    val (statusText, icon, color) = getBatteryStatus(sensorData?.batteryVoltage)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Batería del Dispositivo", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            if (sensorData == null) {
                Text("Esperando datos...", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = icon, contentDescription = statusText, tint = color)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                    Text(
                        text = "%.2f V".format(sensorData.batteryVoltage),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

// --- Tarjeta de Entradas Digitales ---
@Composable
private fun DigitalInputsCard(sensorData: SensorData?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Entradas Digitales", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            if (sensorData == null) {
                Text("Esperando datos...", style = MaterialTheme.typography.bodyMedium)
            } else {
                val configuration = LocalConfiguration.current
                val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

                if (isPortrait) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0..3).forEach { index ->
                            DigitalInputItem(
                                name = "DI ${index + 1}",
                                isSet = isBitSet(sensorData.digitalInputs, index)
                            )
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DigitalInputItem(name = "DI 1", isSet = isBitSet(sensorData.digitalInputs, 0))
                            DigitalInputItem(name = "DI 2", isSet = isBitSet(sensorData.digitalInputs, 1))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DigitalInputItem(name = "DI 3", isSet = isBitSet(sensorData.digitalInputs, 2))
                            DigitalInputItem(name = "DI 4", isSet = isBitSet(sensorData.digitalInputs, 3))
                        }
                    }
                }
            }
        }
    }
}

// --- Tarjeta de Salidas Digitales ---
@Composable
private fun DigitalOutputsCard(
    sensorData: SensorData?,
    onSetOutput: (channel: Int, state: Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Salidas Digitales (Control)", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            if (sensorData == null) {
                Text("Esperando datos...", style = MaterialTheme.typography.bodyMedium)
            } else {
                val configuration = LocalConfiguration.current
                val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

                if (isPortrait) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0..3).forEach { index ->
                            DigitalOutputItem(
                                name = "DO ${index + 1}",
                                isSet = isBitSet(sensorData.digitalOutputs, index),
                                onCheckedChange = { newState ->
                                    onSetOutput(index, newState)
                                }
                            )
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DigitalOutputItem(
                                name = "DO 1",
                                isSet = isBitSet(sensorData.digitalOutputs, 0),
                                onCheckedChange = { newState -> onSetOutput(0, newState) }
                            )
                            DigitalOutputItem(
                                name = "DO 2",
                                isSet = isBitSet(sensorData.digitalOutputs, 1),
                                onCheckedChange = { newState -> onSetOutput(1, newState) }
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DigitalOutputItem(
                                name = "DO 3",
                                isSet = isBitSet(sensorData.digitalOutputs, 2),
                                onCheckedChange = { newState -> onSetOutput(2, newState) }
                            )
                            DigitalOutputItem(
                                name = "DO 4",
                                isSet = isBitSet(sensorData.digitalOutputs, 3),
                                onCheckedChange = { newState -> onSetOutput(3, newState) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Tarjeta de Entradas Analógicas ---
@Composable
private fun AnalogInputsCard(sensorData: SensorData?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Entradas Analógicas", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            if (sensorData == null) {
                Text("Esperando datos...", style = MaterialTheme.typography.bodyMedium)
            } else {
                sensorData.analogInputs.forEachIndexed { index, value ->
                    AnalogInputItem(name = "AI ${index + 1}", value = value)
                }
            }
        }
    }
}

// --- Tarjeta de GPS (Actualización manual) ---
@Composable
private fun GpsCard(
    gpsData: GpsData?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }, // Habilita el clic
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Posición GPS", style = MaterialTheme.typography.titleLarge)
                Text(
                    "(Tocar para refrescar)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(16.dp))

            if (gpsData == null) {
                Text(
                    "Presione la tarjeta para cargar los datos.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // --- ¡LÓGICA ACTUALIZADA! ---
                // Muestra los nuevos campos: model, softRevision, mobileID
                GpsInfoItem(name = "Modelo", value = gpsData.model)
                Spacer(Modifier.height(8.dp))
                GpsInfoItem(name = "Revisión SW", value = gpsData.softRevision)
                Spacer(Modifier.height(8.dp))
                GpsInfoItem(name = "ID Móvil", value = gpsData.mobileID)
                Spacer(Modifier.height(8.dp))
                GpsInfoItem(name = "Latitud", value = "%.6f".format(gpsData.latitude))
                GpsInfoItem(name = "Longitud", value = "%.6f".format(gpsData.longitude))
            }
        }
    }
}

// --- Items de UI para las tarjetas ---

@Composable
private fun DigitalInputItem(name: String, isSet: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isSet) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (isSet) "ON" else "OFF",
            tint = if (isSet) MaterialTheme.colorScheme.primary else Color.Gray
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$name: ",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = if (isSet) "ON" else "OFF",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isSet) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}

@Composable
private fun DigitalOutputItem(
    name: String,
    isSet: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$name:",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Switch(
            checked = isSet,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AnalogInputItem(name: String, value: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$name:",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "%.2f V".format(value), // Muestra 2 decimales
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun GpsInfoItem(
    name: String,
    value: String,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = "$name: ",
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// --- Helpers ---

private data class BatteryStatus(val text: String, val icon: ImageVector, val color: Color)

@Composable
private fun getBatteryStatus(voltage: Float?): BatteryStatus {
    val green = MaterialTheme.colorScheme.primary
    val orange = Color(0xFFFFA000) // Ámbar
    val red = MaterialTheme.colorScheme.error
    val grey = Color.Gray

    return when {
        voltage == null -> BatteryStatus("Desconocido", Icons.Default.BatteryUnknown, grey)
        voltage > 4.0f -> BatteryStatus("Cargada", Icons.Default.BatteryFull, green)
        voltage > 3.7f -> BatteryStatus("Normal", Icons.Default.BatteryStd, green)
        voltage > 3.5f -> BatteryStatus("Baja", Icons.Default.BatteryAlert, orange)
        else -> BatteryStatus("Crítica", Icons.Default.BatteryAlert, red)
    }
}

private fun isBitSet(value: Int, bitIndex: Int): Boolean {
    return (value shr bitIndex) and 1 == 1
}

// --- Helper para el Timestamp ---
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatLocalTimestamp(timestamp: Long?): String {
    if (timestamp == null) return "--:--:--"
    return try {
        val instant = Instant.ofEpochMilli(timestamp)
        LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(timeFormatter)
    } catch (e: Exception) {
        "--:--:--"
    }
}
