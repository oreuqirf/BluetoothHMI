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
import com.example.bluetoothhmi.components.SensorDataCard
import com.example.bluetoothhmi.components.ScalingDataCard
import com.example.bluetoothhmi.components.DeviceTimeCard
import com.example.bluetoothhmi.components.GpsDataCard

import com.example.bluetoothhmi.data.MyBluetoothViewModel
import com.example.bluetoothhmi.data.CommandProtocol
import com.example.bluetoothhmi.data.WorkMode
import com.example.bluetoothhmi.data.SensorData
import com.example.bluetoothhmi.data.ScalingData
import com.example.bluetoothhmi.data.DeviceIdentification
import com.example.bluetoothhmi.data.GpsData

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

import com.example.bluetoothhmi.components.WorkModeEditorDialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DeviceDataScreen(navController: NavController, viewModel: MyBluetoothViewModel) {

    val identificationData by viewModel.identificationData.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val workModeData by viewModel.workModeData.collectAsState()
    val sensorData by viewModel.sensorData.collectAsState()
    val scalingData by viewModel.scalingData.collectAsState()
    val isEditing by viewModel.isEditingWorkMode.collectAsState()
    val gpsData by viewModel.gpsData.collectAsState()
    val deviceTimeState by viewModel.deviceTime.collectAsState()

    val context = LocalContext.current
    val storagePermissionState = rememberPermissionState(
        permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    // --- ¡GUARDIÁN DE CONEXIÓN ACTUALIZADO! ---
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

    // Listener para los mensajes (Toasts)
    LaunchedEffect(key1 = true) {
        viewModel.uiEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Diálogo de edición de Modo de Trabajo
    if (isEditing) {
        WorkModeEditorDialog(
            currentMode = workModeData,
            onDismiss = { viewModel.onCancelEditWorkMode() },
            onSave = { newMode ->
                viewModel.onSaveWorkMode(newMode)
            }
        )
    }

    // --- ¡UI ACTUALIZADA! ---
    // Añadido Scaffold con TopAppBar para volver atrás
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración del Dispositivo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { // <-- Vuelve al Dashboard
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding) // <-- Padding del Scaffold
                .padding(horizontal = 16.dp) // Padding lateral
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- Tarjetas de Configuración ---

            IdentificationCard(
                identificationData = identificationData,
                onClick = { viewModel.sendData(CommandProtocol.GET_IDENTIFICATION) }
            )
            DeviceTimeCard(
                deviceTimeEpoch = deviceTimeState?.time,
                onRefresh = { viewModel.readDeviceTime() },
                onSyncTime = { viewModel.setDeviceTime() }
            )
            WorkModeCard(
                workModeData = workModeData,
                onRefreshClick = { viewModel.onRefreshWorkMode() },
                onEditClick = { viewModel.onStartEditingWorkMode() }
            )
            SensorDataCard(
                sensorData = sensorData,
                onClick = { viewModel.sendData(CommandProtocol.GET_SENSORS) },
                onSetOutput = viewModel::onSetDigitalOutput
            )
            ScalingDataCard(
                scalingData = scalingData,
                onReadChannel = { channel ->
                    viewModel.sendData(CommandProtocol.getChannelScaling(channel))
                },
                onSaveChannel = { channel, rawMin, rawMax, zero, full ->
                    viewModel.onSaveChannelScaling(channel, rawMin, rawMax, zero, full)
                }
            )
            GpsDataCard(
                gpsData = gpsData,
                onClick = { viewModel.readGpsData() }
            )

            // --- Botones de Acción ---

            Spacer(modifier = Modifier.height(16.dp))

            // (Botón de ir a Dashboard eliminado, ahora está en la TopAppBar)

            Button(
                onClick = {
                    fun generateReport() {
                        viewModel.generatePdfReport(
                            identification = identificationData,
                            workMode = workModeData,
                            sensorData = sensorData,
                            scaling = scalingData
                        )
                    }
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        if (storagePermissionState.status.isGranted) {
                            generateReport()
                        } else {
                            storagePermissionState.launchPermissionRequest()
                        }
                    } else {
                        generateReport()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generar Reporte PDF")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.disconnectDevice() }, // La desconexión te llevará a SplashScreen
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Desconectar")
            }

            Spacer(modifier = Modifier.height(16.dp)) // Espacio al final
        }
    }
}
