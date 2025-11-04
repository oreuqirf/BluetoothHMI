package com.example.bluetoothhmi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.example.bluetoothhmi.connection.ConnectionState
import com.example.bluetoothhmi.components.IdentificationCard
import com.example.bluetoothhmi.components.WorkModeCard
import com.example.bluetoothhmi.components.SensorDataCard // <-- Importa la nueva tarjeta
import com.example.bluetoothhmi.components.ScalingDataCard // <-- Importa la nueva tarjeta
import com.example.bluetoothhmi.components.DeviceTimeCard  // <-- Importa la nueva tarjeta
import com.example.bluetoothhmi.components.GpsDataCard  // <-- Importa la nueva tarjeta

import com.example.bluetoothhmi.data.MyBluetoothViewModel
import com.example.bluetoothhmi.data.CommandProtocol
import com.example.bluetoothhmi.data.WorkMode
import com.example.bluetoothhmi.data.SensorData // <-- Importa la clase
import com.example.bluetoothhmi.data.ScalingData           // <-- Importa la clase
import com.example.bluetoothhmi.data.DeviceIdentification
import com.example.bluetoothhmi.data.GpsData            // <-- Importa la clase


// En el archivo ui/devicedata/DeviceDataScreen.kt
// ... (otros imports)
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.bluetoothhmi.components.WorkModeEditorDialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState


@Composable
fun DeviceDataScreen(navController: NavController, viewModel: MyBluetoothViewModel) {

    // 2. Recolectamos los datos (el valor inicial será 'null' desde el ViewModel)
    val identificationData by viewModel.identificationData.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val workModeData by viewModel.workModeData.collectAsState()
    val sensorData by viewModel.sensorData.collectAsState()
    val scalingData by viewModel.scalingData.collectAsState()
    val isEditing by viewModel.isEditingWorkMode.collectAsState()
    val gpsData by viewModel.gpsData.collectAsState()

    // --- 1. MODIFICADO: El nombre del estado y su tipo (DeviceTime?) ---
    val deviceTimeState by viewModel.deviceTime.collectAsState()


    // HANDLERS DE CONTEXTO Y PERMISOS ---
    val context = LocalContext.current

    // gestor de permisos (solo para API 28 e inferior)
    val storagePermissionState = rememberPermissionState(
        permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    // 1. ELIMINAMOS EL LaunchedEffect(key1 = true)
    // Ya no se pide nada automáticamente al entrar a la pantalla.

    // Vuelve atrás si la conexión se pierde
    LaunchedEffect(connectionState) {
        if (connectionState !is ConnectionState.Connected) {
            navController.popBackStack()
        }
    }

    // --- LISTENER PARA LOS MENSAJES (TOASTS) ---
    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    if (isEditing) { // <-- MODIFICADO
        WorkModeEditorDialog(
            currentMode = workModeData,
            onDismiss = { viewModel.onCancelEditWorkMode() }, // <-- MODIFICADO
            onSave = { newMode ->
                // --- MODIFICADO: La lógica ahora está en el ViewModel ---
                viewModel.onSaveWorkMode(newMode)
            }
        )
    }

    // Esta es la Columna principal que ocupa toda la pantalla
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dispositivo", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(20.dp))

        // COLUMN ANIDADO AQUÍ ---
        // Este Column ocupará todo el espacio disponible y permitirá el scroll
        Column(
            modifier = Modifier
                .weight(1f) // <-- Ocupa todo el espacio disponible, empujando el botón hacia abajo
                .verticalScroll(rememberScrollState()) // <-- HACE QUE ESTA ÁREA SEA DESPLAZABLE
        ) {
            // La tarjeta de Identificación AHORA pide sus propios datos
            IdentificationCard(
                identificationData = identificationData,
                onClick = {
                    // Esto SÓLO pide los datos de identificación
                    viewModel.sendData(CommandProtocol.GET_IDENTIFICATION)
                }
            )
            DeviceTimeCard(
                // --- 2. MODIFICADO: Pasa el Long de adentro del estado ---
                deviceTimeEpoch = deviceTimeState?.time, // <-- Accede a la propiedad .time
                onRefresh = { viewModel.readDeviceTime() },
                onSyncTime = { viewModel.setDeviceTime() }
            )
            // La tarjeta de Modo de Trabajo AHORA pide sus propios datos
            WorkModeCard(
                workModeData = workModeData,
                onRefreshClick = {
                    viewModel.onRefreshWorkMode()
                },
                onEditClick = {
                    viewModel.onStartEditingWorkMode()
                }
            )

            SensorDataCard(
                sensorData = sensorData,
                onClick = {
                    viewModel.sendData(CommandProtocol.GET_SENSORS)
                },
                // --- ¡MODIFICACIÓN APLICADA AQUÍ! ---
                // Ahora llama a la función del ViewModel que hace la actualización optimista
                onSetOutput = viewModel::onSetDigitalOutput
                // --- FIN DE LA MODIFICACIÓN ---
            )

            ScalingDataCard(
                scalingData = scalingData,
                // El nombre del parámetro cambió de 'onRefresh' a 'onReadChannel'
                onReadChannel = { channel ->
                    viewModel.sendData(
                        CommandProtocol.getChannelScaling(channel)
                    )
                },
                onSaveChannel = { channel, rawMin, rawMax, zero, full ->
                    viewModel.onSaveChannelScaling(channel, rawMin, rawMax, zero, full)
                }
            )

            GpsDataCard(
                gpsData = gpsData,
                onClick = {
                    viewModel.readGpsData()
                }
            )

        }

        Spacer(modifier = Modifier.height(16.dp)) // Empuja el botón hacia abajo

        // --- BOTÓN Reporte ---
        Button(
            onClick = {
                // Función de ayuda para llamar al ViewModel
                fun generateReport() {
                    viewModel.generatePdfReport(
                        identification = identificationData,
                        workMode = workModeData,
                        sensorData = sensorData,
                        scaling = scalingData
                    )
                }

                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    // Es Android 9 (API 28) o inferior, necesitamos permiso
                    if (storagePermissionState.status.isGranted) {
                        generateReport()
                    } else {
                        storagePermissionState.launchPermissionRequest()
                    }
                } else {
                    // Es Android 10 (API 29) o superior, no se necesita permiso en runtime
                    generateReport()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generar Reporte PDF")
        }

        Spacer(modifier = Modifier.height(8.dp)) // Espacio entre botones

        Button(
            onClick = { viewModel.disconnectDevice() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Desconectar")
        }
    }
}

