package com.example.bluetoothhmi.components

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType

import com.example.bluetoothhmi.data.DeviceIdentification
import com.example.bluetoothhmi.data.WorkMode
import com.example.bluetoothhmi.data.SensorData
import com.example.bluetoothhmi.data.ScalingData
import com.example.bluetoothhmi.data.ChannelScaling
import com.example.bluetoothhmi.data.GpsData

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


// ... (Tus otros imports)
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext



/**
 * Función helper para convertir un timestamp Epoch (en segundos)
 * a un String con formato local.
 */
private fun formatEpochSeconds(epochSeconds: Long?): String {
    // Estado inicial o mientras carga
    if (epochSeconds == null) {
        return "Presione la tarjeta para cargar la hora."
    }
    // El dispositivo puede devolver 0 si no tiene hora
    if (epochSeconds == 0L) {
        return "Hora no configurada."
    }

    try {
        val instant = Instant.ofEpochSecond(epochSeconds)
        // Usa la zona horaria del teléfono para mostrar la hora
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        return localDateTime.format(formatter)
    } catch (e: Exception) {
        Log.e("FormatEpoch", "Error al formatear epoch: $epochSeconds", e)
        return "Hora inválida"
    }
}


@Composable
fun DeviceTimeCard(
    deviceTimeEpoch: Long?, // <-- Acepta el Long (o null)
    onRefresh: () -> Unit,   // <-- Función para leer
    onSyncTime: () -> Unit   // <-- Función para escribir/sincronizar
) {
    // Llama al helper para obtener el texto formateado
    val formattedTime = formatEpochSeconds(deviceTimeEpoch)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable { onRefresh() }, // El clic en la tarjeta refresca
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // 1. Título
            Text(
                text = "Hora del Dispositivo",
                style = MaterialTheme.typography.titleLarge
            )

            // 2. Fila de Acciones (Refrescar y Sincronizar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Texto de ayuda
                Text(
                    text = "(Tocar tarjeta para refrescar)",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Botón de "Sincronizar"
                TextButton(onClick = onSyncTime) {
                    Text("Sincronizar Hora")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Contenido (La hora)
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.bodyLarge, // Un poco más grande
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}


@Composable
fun ScanSection(isScanning: Boolean, onStartScan: () -> Unit, onStopScan: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Button(onClick = if (isScanning) onStopScan else onStartScan) {
            Text(if (isScanning) "Detener Escaneo" else "Iniciar Escaneo")
        }
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(start = 8.dp))
        }
    }
}


@Composable
fun DeviceList(
    devices: List<BluetoothDevice>,
    onDeviceClick: (BluetoothDevice) -> Unit,
    modifier: Modifier = Modifier // <-- 1. Acepta un modifier
) {
    LazyColumn(
        // 2. Aplica el modifier que viene de la pantalla (que incluye el .weight(1f))
        // 3. Quita el .heightIn(max = 250.dp)
        modifier = modifier.fillMaxWidth()
    ) {
        items(devices) { device ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onDeviceClick(device) },
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = device.name ?: "Dispositivo Desconocido", style = MaterialTheme.typography.titleMedium)
                    Text(text = device.address, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun IdentificationCard(identificationData: DeviceIdentification?,
                       onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Dispositivo (Tocar para refrescar)", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // --- ¡MODIFICACIÓN AQUÍ! ---
            if (identificationData == null) {
                Text(
                    text = "Presione la tarjeta para cargar los datos.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text("Device ID: ${identificationData.deviceID}")
                Text("Firmware Version: ${identificationData.firmwareVersion}")
                Text("Hardware Version: ${identificationData.hardwareVersion}")
                Text("Last Configuration: ${identificationData.lastConfigurationDate}")
            }
        }
    }
}


@Composable
fun WorkModeCard(
    workModeData: WorkMode?,
    onRefreshClick: () -> Unit, // <-- 1. Acepta una lambda (función)
    onEditClick: () -> Unit     // <-- 2. Acepta otra lambda
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable { onRefreshClick() }, // <-- El clic de la tarjeta llama a onRefreshClick
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { // <-- Padding principal de la tarjeta

            // 1. Título (solo)
            Text(
                text = "Modo de Funcionamiento",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth() // Ocupa todo el ancho
            )

            // 2. Fila de Acciones (Refrescar y Editar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween // Pone un elemento a la izq. y otro a la der.
            ) {
                // Texto de ayuda para refrescar
                Text(
                    text = "(Tocar tarjeta para refrescar)",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp) // Pequeño espacio
                )

                // Botón de "Editar"
                TextButton(
                    onClick = onEditClick,
                    enabled = workModeData != null // Solo se puede editar si hay datos
                ) {
                    Text("Editar")
                }
            }

            // 3. Espaciador antes del contenido
            Spacer(modifier = Modifier.height(16.dp))

            // 4. Contenido (datos)
            when (workModeData) {
                is WorkMode.Periodic -> {
                    Text("Modo: Periódico", style = MaterialTheme.typography.titleMedium)
                    Text("Intervalo: ${workModeData.intervalMinutes} minutos")
                    Text("Encendido Sensores: ${workModeData.powerOnTime} seg")
                }

                is WorkMode.Fixed -> {
                    Text("Modo: Horas Fijas", style = MaterialTheme.typography.titleMedium)
                    if (workModeData.times.all { it.isBlank() }) {
                        Text("No hay horas programadas.")
                    } else {
                        workModeData.times.forEachIndexed { index, time ->
                            if(time.isNotBlank()) {
                                Text("Hora ${index + 1}: $time")
                            }
                        Text("Encendido Sensores: ${workModeData.powerOnTime} seg")
                        }
                    }
                }

                is WorkMode.Continuous -> {
                    Text("Modo: Continuo", style = MaterialTheme.typography.titleMedium)
                    Text("Intervalo: ${workModeData.intervalMinutes} minutos")
                    Text("Encendido Sensores: ${workModeData.powerOnTime} seg")
                }

                is WorkMode.Disabled -> {
                    Text("Modo: Deshabilitado")
                }

                // --- ¡MODIFICACIÓN AQUÍ! ---
                null -> {
                    Text(
                        text = "Presione la tarjeta para cargar el modo.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun SensorDataCard(
    sensorData: SensorData?,
    onClick: () -> Unit, // Para Refrescar
    onSetOutput: (channel: Int, state: Boolean) -> Unit // Nuevo callback para cambiar salidas
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable { onClick() }, // Refresca todo al tocar la tarjeta
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Valores de Sensores", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // --- MODIFICACIÓN (Ya estaba) ---
            if (sensorData == null) {
                // No mostramos "Cargando", sino un texto instructivo.
                Text(
                    text = "Presione la tarjeta para cargar los valores.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // --- SECCIÓN DE ENTRADAS DIGITALES (NUEVA) ---
                Text("Entradas Digitales", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    DigitalIndicator(name = "DI 1", isSet = isBitSet(sensorData.digitalInputs, 0))
                    DigitalIndicator(name = "DI 2", isSet = isBitSet(sensorData.digitalInputs, 1))
                    DigitalIndicator(name = "DI 3", isSet = isBitSet(sensorData.digitalInputs, 2))
                    DigitalIndicator(name = "DI 4", isSet = isBitSet(sensorData.digitalInputs, 3))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // --- SECCIÓN DE SALIDAS DIGITALES (NUEVA E INTERACTIVA) ---
                Text("Salidas Digitales", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    DigitalOutputControl(
                        name = "DO 1",
                        isSet = isBitSet(sensorData.digitalOutputs, 0),
                        onCheckedChange = { newState -> onSetOutput(0, newState) }
                    )
                    DigitalOutputControl(
                        name = "DO 2",
                        isSet = isBitSet(sensorData.digitalOutputs, 1),
                        onCheckedChange = { newState -> onSetOutput(1, newState) }
                    )
                    DigitalOutputControl(
                        name = "DO 3",
                        isSet = isBitSet(sensorData.digitalOutputs, 2),
                        onCheckedChange = { newState -> onSetOutput(2, newState) }
                    )
                    DigitalOutputControl(
                        name = "DO 4",
                        isSet = isBitSet(sensorData.digitalOutputs, 3),
                        onCheckedChange = { newState -> onSetOutput(3, newState) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // --- SECCIÓN DE VALORES ANALÓGICOS (SIN CAMBIOS) ---
                Text("Entradas Analógicas:", style = MaterialTheme.typography.titleMedium)
                sensorData.analogInputs.forEachIndexed { index, value ->
                    Text("  Canal ${index + 1}: $value")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Salidas Analógicas:", style = MaterialTheme.typography.titleMedium)
                sensorData.analogOutputs.forEachIndexed { index, value ->
                    Text("  Canal ${index + 1}: $value")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Voltaje Batería: ${sensorData.batteryVoltage} V", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}


/**
 * 2. El editor (ScalingChannelEditor) ahora usa 'lastUpdated' como clave
 */
@Composable
private fun ScalingChannelEditor(
    channelName: String,
    currentScaling: ChannelScaling,
    lastUpdated: Long, // <-- ACEPTA EL NUEVO PARÁMETRO
    onSave: (rawMin: Int, rawMax: Int, zero: Float, full: Float) -> Unit,
    onRead: () -> Unit
) {
    // --- ¡MODIFICACIÓN AQUÍ! ---
    // Empezar siempre con los campos de texto vacíos.
    var rawMinText by remember { mutableStateOf("") }
    var rawMaxText by remember { mutableStateOf("") }
    var zeroText by remember { mutableStateOf("") }
    var fullText by remember { mutableStateOf("") }

    // Este efecto se ejecuta si 'lastUpdated' O 'currentScaling' cambian.
    LaunchedEffect(lastUpdated, currentScaling) {
        Log.d("ScalingUI", "Refrescando UI para $channelName (Key: $lastUpdated)")

        val hasGlobalDataLoaded = lastUpdated != 0L

        if (!hasGlobalDataLoaded) {
            // Caso 1: No se ha cargado ningún dato (lastUpdated == 0L).
            // Esto se ejecuta en la primera composición. Mantiene los campos vacíos.
            rawMinText = ""
            rawMaxText = ""
            zeroText = ""
            fullText = ""
        } else {
            // Caso 2: Se han cargado datos (lastUpdated != 0L).

            // --- ¡CORRECCIÓN AQUÍ! ---
            // Comprueba si los datos *de este canal* son los de por defecto.
            // Usa 800 como el rawMin por defecto, según tu data class.
            val isChannelDataDefault = currentScaling.rawMin == 800 &&
                    currentScaling.rawMax == 4000 &&
                    currentScaling.zero == 0f &&
                    currentScaling.full == 100f

            if (isChannelDataDefault) {
                // Ej: Se cargó AI-0, pero esto es AI-1 (que sigue por defecto).
                // Muestra los campos vacíos.
                rawMinText = ""
                rawMaxText = ""
                zeroText = ""
                fullText = ""
            } else {
                // Ej: Esto es AI-0, y tiene datos reales.
                // Muestra los datos reales.
                rawMinText = currentScaling.rawMin.toString()
                rawMaxText = currentScaling.rawMax.toString()
                zeroText = currentScaling.zero.toString()
                fullText = currentScaling.full.toString()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable { onRead() } // <-- Tu clic en el bloque, ¡perfecto!
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // Título clicable
        Text("$channelName (Actualizar)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // Fila 1: Raw Min y Raw Max
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = rawMinText,
                onValueChange = { rawMinText = it },
                label = { Text("Raw Min") },
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = rawMaxText,
                onValueChange = { rawMaxText = it },
                label = { Text("Raw Max") },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Fila 2: Zero y Full
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = zeroText,
                onValueChange = { zeroText = it },
                label = { Text("Cero") },
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = fullText,
                onValueChange = { fullText = it },
                label = { Text("Full") },
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Botón "Set" al final
        Button(
            onClick = {
                onSave(
                    rawMinText.toIntOrNull() ?: 800, // <-- Usa 800 como default
                    rawMaxText.toIntOrNull() ?: 4000,
                    zeroText.toFloatOrNull() ?: 0f,
                    fullText.toFloatOrNull() ?: 100f
                )
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Guardar Escalado")
        }
    }
}

/**
 * 1. La tarjeta principal (ScalingDataCard) ahora debe pasar la marca de tiempo
 */
@Composable
fun ScalingDataCard(
    scalingData: ScalingData?,
    onReadChannel: (channel: String) -> Unit,
    onSaveChannel: (channel: String, rawMin: Int, rawMax: Int, zero: Float, full: Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        // .clickable { onRefresh() }, // <-- ELIMINA EL CLIC DE LA TARJETA
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ... (título y spacer)
            Text("Escalado", style = MaterialTheme.typography.titleLarge) // Título simple
            Spacer(modifier = Modifier.height(16.dp))

            // --- ¡CORRECCIÓN AQUÍ! ---
            // Eliminamos el 'if (scalingData == null)' que ocultaba los editores.
            // En su lugar, creamos 'data' que usa un ScalingData() vacío si 'scalingData' es null.
            // Esto asegura que los 'ScalingChannelEditor' *siempre* se muestren y se puedan clickear.
            val data = scalingData ?: ScalingData()

            // Pasa la nueva acción onRead y el lastUpdated a cada editor
            ScalingChannelEditor(
                channelName = "AI-0",
                currentScaling = data.ai0, // <-- Usamos 'data'
                lastUpdated = data.lastUpdated, // <-- Usamos 'data'
                onSave = { rMin, rMax, zero, full -> onSaveChannel("AI0", rMin, rMax, zero, full) },
                onRead = { onReadChannel("AI0") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            ScalingChannelEditor(
                channelName = "AI-1",
                currentScaling = data.ai1, // <-- Usamos 'data'
                lastUpdated = data.lastUpdated, // <-- Usamos 'data'
                onSave = { rMin, rMax, zero, full -> onSaveChannel("AI1", rMin, rMax, zero, full) },
                onRead = { onReadChannel("AI1") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            ScalingChannelEditor(
                channelName = "AI-2",
                currentScaling = data.ai2, // <-- Usamos 'data'
                lastUpdated = data.lastUpdated, // <-- Usamos 'data'
                onSave = { rMin, rMax, zero, full -> onSaveChannel("AI2", rMin, rMax, zero, full) },
                onRead = { onReadChannel("AI2") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            ScalingChannelEditor(
                channelName = "AI-3",
                currentScaling = data.ai3, // <-- Usamos 'data'
                lastUpdated = data.lastUpdated, // <-- Usamos 'data'
                onSave = { rMin, rMax, zero, full -> onSaveChannel("AI3", rMin, rMax, zero, full) },
                onRead = { onReadChannel("AI3") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            // ... (Repite esto para AI-2, AI-3, AO-0, y AO-1) ...
        }
    }
}


/**
 * Función helper privada para comprobar el estado de un bit en un entero.
 */
private fun isBitSet(value: Int, bitIndex: Int): Boolean {
    // (value shr bitIndex) -> Desplaza el bit que queremos a la posición 0
    // and 1              -> Comprueba si ese bit es 1
    return (value shr bitIndex) and 1 == 1
}

/**
 * Un Composable que muestra un indicador de E/S (un círculo de color).
 */
@Composable
private fun DigitalIndicator(name: String, isSet: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (isSet) Color(0xFF4CAF50) else Color.Gray, // Verde si ON, Gris si OFF
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(if (isSet) "ON" else "OFF", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

/**
 * Un Composable que muestra un interruptor (Switch) para controlar una E/S.
 */
@Composable
private fun DigitalOutputControl(
    name: String,
    isSet: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Text(name, style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = isSet,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f) // Hace el interruptor un poco más pequeño
        )
        Text(if (isSet) "ON" else "OFF", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}


// En el archivo ui/components/SharedUI.kt


/**
 * Valida un string de tiempo.
 * Acepta "HH:MM" donde HH es 0-23 y MM es 0-59.
 * Acepta el caso especial "24:00" como válido.
 * Acepta un string vacío como válido (para campos no usados).
 */
private fun isValidTimeFormat(time: String): Boolean {
    // Si está vacío, es válido (no se usará)
    if (time.isBlank()) return true

    // Intentar parsear "HH:MM"
    val parts = time.split(":")
    if (parts.size != 2) return false // Debe tener formato HH:MM

    val horas = parts[0].toIntOrNull()
    val minutos = parts[1].toIntOrNull()

    // Ambas partes deben ser números
    if (horas == null || minutos == null) return false

    // Forzar dos dígitos para minutos (ej: "08:05", no "08:5")
    if (parts[1].length != 2) return false

    // Caso especial: 24:00 es válido para "deshabilitado"
    if (horas == 24) {
        return minutos == 0
    }

    // Rango normal
    return (horas in 0..23) && (minutos in 0..59)
}

/**
 * Un diálogo emergente para editar el Modo de Trabajo.
 */
@Composable
fun WorkModeEditorDialog(
    currentMode: WorkMode?,
    onDismiss: () -> Unit,
    onSave: (newMode: WorkMode) -> Unit
) {
    // --- Estados internos para manejar la edición ---

    // 0 = Periódico, 1 = Fijo, 2 = Continuo,  3 = Deshabilitado
    var selectedTabIndex by remember { mutableStateOf(0) }
    var intervalText by remember { mutableStateOf("") }
    var powerOnTimeText by remember { mutableStateOf("") }

    var time1Text by remember { mutableStateOf("") }
    var time2Text by remember { mutableStateOf("") }
    var time3Text by remember { mutableStateOf("") }
    var time4Text by remember { mutableStateOf("") }
    var time5Text by remember { mutableStateOf("") }

    var isTime1Error by remember { mutableStateOf(false) }
    var isTime2Error by remember { mutableStateOf(false) }
    var isTime3Error by remember { mutableStateOf(false) }
    var isTime4Error by remember { mutableStateOf(false) }
    var isTime5Error by remember { mutableStateOf(false) }

    // Este 'LaunchedEffect' carga los datos actuales cuando se abre el diálogo
    LaunchedEffect(currentMode) {
        when (currentMode) {
            is WorkMode.Periodic -> {
                selectedTabIndex = 0
                intervalText = currentMode.intervalMinutes.toString()
                powerOnTimeText = currentMode.powerOnTime.toString()
            }
            is WorkMode.Fixed -> {
                selectedTabIndex = 1
                time1Text = currentMode.times.getOrNull(0) ?: ""
                time2Text = currentMode.times.getOrNull(1) ?: ""
                time3Text = currentMode.times.getOrNull(2) ?: ""
                time4Text = currentMode.times.getOrNull(3) ?: ""
                time5Text = currentMode.times.getOrNull(4) ?: ""
                powerOnTimeText = currentMode.powerOnTime.toString()
            }
            is WorkMode.Continuous -> {
                selectedTabIndex = 2
                intervalText = currentMode.intervalMinutes.toString()
                powerOnTimeText = currentMode.powerOnTime.toString()
            }
            is WorkMode.Disabled -> {
                selectedTabIndex = 3
            }
            null -> {
                // Estado por defecto si aún no hay datos
                selectedTabIndex = 3
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Modo de Trabajo") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Periódico") })
                    Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Fijo") })
                    Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, text = { Text("Continuo") })
                    Tab(selected = selectedTabIndex == 3, onClick = { selectedTabIndex = 3 }, text = { Text("Apagado") })
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Contenido de cada pestaña
                when (selectedTabIndex) {
                    // --- Pestaña Periódico ---
                    0 -> {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it },
                            label = { Text("Intervalo (minutos)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = powerOnTimeText,
                            onValueChange = { powerOnTimeText = it },
                            label = { Text("Encendido Sensores (seg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // --- Pestaña Fijo ---
                    1 -> {
                        Column {
                            OutlinedTextField(
                                value = time1Text,
                                // --- ¡MODIFICADO! ---
                                onValueChange = {
                                    time1Text = it
                                    isTime1Error = !isValidTimeFormat(it)
                                },
                                label = { Text("Hora 1 (HH:MM o 24:00)") },
                                modifier = Modifier.fillMaxWidth(),
                                isError = isTime1Error // <-- Muestra el error
                            )
                            OutlinedTextField(
                                value = time2Text,
                                // --- ¡MODIFICADO! ---
                                onValueChange = {
                                    time2Text = it
                                    isTime2Error = !isValidTimeFormat(it)
                                },
                                label = { Text("Hora 2 (HH:MM o 24:00)") },
                                modifier = Modifier.fillMaxWidth(),
                                isError = isTime2Error // <-- Muestra el error
                            )
                            // ... Repite esto para time3Text, time4Text, y time5Text ...
                            OutlinedTextField(
                                value = time3Text,
                                onValueChange = {
                                    time3Text = it
                                    isTime3Error = !isValidTimeFormat(it)
                                },
                                label = { Text("Hora 3 (HH:MM o 24:00)") },
                                modifier = Modifier.fillMaxWidth(),
                                isError = isTime3Error
                            )
                            OutlinedTextField(
                                value = time4Text,
                                onValueChange = {
                                    time4Text = it
                                    isTime4Error = !isValidTimeFormat(it)
                                },
                                label = { Text("Hora 4 (HH:MM o 24:00)") },
                                modifier = Modifier.fillMaxWidth(),
                                isError = isTime4Error
                            )
                            OutlinedTextField(
                                value = time5Text,
                                onValueChange = {
                                    time5Text = it
                                    isTime5Error = !isValidTimeFormat(it)
                                },
                                label = { Text("Hora 5 (HH:MM o 24:00)") },
                                modifier = Modifier.fillMaxWidth(),
                                isError = isTime5Error
                            )
                            OutlinedTextField(
                                value = powerOnTimeText,
                                onValueChange = { powerOnTimeText = it },
                                label = { Text("Encendido Sensores (seg)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // --- Pestaña Continuo ---
                    2 -> {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it },
                            label = { Text("Intervalo TX (minutos)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = powerOnTimeText,
                            onValueChange = { powerOnTimeText = it },
                            label = { Text("Encendido Sensores (seg)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // --- Pestaña Apagado ---
                    3 -> {
                        Text("El equipo no enviará datos automáticamente.")
                    }
                }
            }
        },

        confirmButton = {
            TextButton(
                onClick = {
                    when (selectedTabIndex) {
                        // --- Pestaña Periódico ---
                        0 -> {
                            val interval = intervalText.toIntOrNull() ?: 0
                            val power = powerOnTimeText.toIntOrNull() ?: 0
                            val newMode = WorkMode.Periodic(interval, power) // <-- AÑADIDO
                            onSave(newMode)
                            onDismiss()
                        }

                        // --- Pestaña Fijo (AQUÍ VA LA VALIDACIÓN) ---
                        1 -> {
                            // 1. Vuelve a validar todos los campos (por si el usuario no salió del último campo)
                            val t1Valid = isValidTimeFormat(time1Text)
                            val t2Valid = isValidTimeFormat(time2Text)
                            val t3Valid = isValidTimeFormat(time3Text)
                            val t4Valid = isValidTimeFormat(time4Text)
                            val t5Valid = isValidTimeFormat(time5Text)

                            // 2. Actualiza el estado de error de todos
                            isTime1Error = !t1Valid
                            isTime2Error = !t2Valid
                            isTime3Error = !t3Valid
                            isTime4Error = !t4Valid
                            isTime5Error = !t5Valid

                            // 3. Comprueba si TODOS son válidos
                            if (t1Valid && t2Valid && t3Valid && t4Valid && t5Valid) {
                                // Si son válidos, guarda y cierra
                                val times = listOf(time1Text, time2Text, time3Text, time4Text, time5Text)
                                val power = powerOnTimeText.toIntOrNull() ?: 0
                                val newMode = WorkMode.Fixed(times, power)
                                onSave(newMode)
                                onDismiss()
                            } else {
                                // Si alguno es inválido, no hagas nada (solo muestra los errores)
                                Log.e("WorkModeDialog", "Hay horarios con formato inválido.")
                            }
                        }

                        // --- Pestaña Continuo ---
                        2 -> {
                            val interval = intervalText.toIntOrNull() ?: 0
                            val power = powerOnTimeText.toIntOrNull() ?: 0
                            val newMode = WorkMode.Periodic(interval, power) // <-- AÑADIDO
                            onSave(newMode)
                            onDismiss()
                        }

                        // --- Pestaña Apagado ---
                        else -> {
                            val newMode = WorkMode.Disabled
                            onSave(newMode)
                            onDismiss() // Guardar y cerrar
                        }
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun GpsDataCard(
    gpsData: GpsData?,
    onClick: () -> Unit // Para refrescar
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable { onClick() }, // Clic en la tarjeta refresca
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Datos GPS", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "(Tocar tarjeta para refrescar)",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (gpsData == null) {
                Text(
                    text = "Presione la tarjeta para cargar los datos.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // Función helper interna para el texto del Fix
                val fixStatusText = when (gpsData.fixQuality) {
                    0 -> "Sin Cobertura"
                    1 -> "Fix 2D/3D"
                    2 -> "Fix Diferencial (DGPS)"
                    else -> "Desconocido (${gpsData.fixQuality})"
                }

                // Fila 1: Latitud y Longitud
                Row(Modifier.fillMaxWidth()) {
                    GpsInfoItem(title = "Latitud", value = "%.6f".format(gpsData.latitude), Modifier.weight(1f))
                    GpsInfoItem(title = "Longitud", value = "%.6f".format(gpsData.longitude), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))

                // Fila 2: Altitud y Velocidad
                Row(Modifier.fillMaxWidth()) {
                    GpsInfoItem(title = "Altitud", value = "${"%.1f".format(gpsData.altitude)} m", Modifier.weight(1f))
                    GpsInfoItem(title = "Velocidad", value = "${"%.1f".format(gpsData.speed)} km/h", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))

                // Fila 3: Calidad y Satélites
                Row(Modifier.fillMaxWidth()) {
                    GpsInfoItem(title = "Calidad Fix", value = fixStatusText, Modifier.weight(1f))
                    GpsInfoItem(title = "Satélites", value = gpsData.satellites.toString(), Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Un pequeño Composable helper para mostrar un título y un valor.
 * Puedes ponerlo al final de SharedUI.kt
 */
@Composable
private fun GpsInfoItem(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Un diálogo que advierte a los usuarios de MIUI sobre las optimizaciones de batería
 * y los lleva directamente a los ajustes de "Inicio automático".
 */
@Composable
fun MiuiOptimizationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aviso para usuarios de Xiaomi (MIUI)") },
        text = {
            Text(
                "Tu teléfono parece estar usando MIUI, el cual puede bloquear el escaneo de Bluetooth para ahorrar batería.\n\n" +
                        "Para que el escaneo funcione, por favor activa el **\"Inicio automático\" (Autostart)** para esta aplicación."
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Intenta lanzar el intent específico de "Autostart" de MIUI
                    try {
                        val intent = Intent().setComponent(
                            ComponentName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity"
                            )
                        )
                        context.startActivity(intent)
                    } catch (e1: Exception) {
                        try {
                            // Fallback 1: Intentar la página de seguridad general de MIUI
                            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                            intent.putExtra("extra_pkgname", context.packageName)
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            // Fallback 2: Abrir los ajustes generales de la app
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.fromParts("package", context.packageName, null)
                            context.startActivity(intent)
                        }
                    }
                    onDismiss() // Cierra el diálogo después de lanzar el intent
                }
            ) {
                Text("Ir a Ajustes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}