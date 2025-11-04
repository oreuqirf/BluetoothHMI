package com.example.bluetoothhmi.data

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState


import com.example.bluetoothhmi.connection.BluetoothService
import com.example.bluetoothhmi.connection.ConnectionState
import com.example.bluetoothhmi.components.DeviceList
import com.example.bluetoothhmi.components.ScanSection
import com.example.bluetoothhmi.components.MiuiOptimizationDialog


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConnectionScreen(navController: NavController, viewModel: MyBluetoothViewModel) {

    val context = LocalContext.current
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val showMiuiDialog by viewModel.showMiuiWarningDialog.collectAsState()

    // --- ¡NUEVO! ---
    // Observamos si el ViewModel está vinculado al Servicio.
    val isServiceBound by viewModel.isServiceBound.collectAsState()

    // Inicia el servicio en primer plano (solo 1 vez)
    LaunchedEffect(key1 = true) {
        Log.d("ConnectionScreen", "Iniciando BluetoothService...")
        Intent(context, BluetoothService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // Lógica de Permisos (¡CORREGIDA!)
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissions)

    // --- ¡MODIFICADO! ---
    // Llama a 'onPermissionsGranted' del ViewModel cuando los permisos cambian
    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) {
            Log.d("ConnectionScreen", "Permisos concedidos, notificando al ViewModel.")
            viewModel.onPermissionsGranted() // <-- Llama a la nueva función
        }
    }

    // Navegación (sin cambios)
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            navController.navigate("device_data_screen")
        }
    }

    // Diálogo MIUI (sin cambios)
    if (showMiuiDialog) {
        MiuiOptimizationDialog(
            onDismiss = { viewModel.onDismissMiuiWarning() }
        )
    }

    // Listener para los mensajes de error de escaneo
    LaunchedEffect(key1 = true) {
        viewModel.scanErrorEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // --- UI de la Pantalla de Conexión ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Conexión Bluetooth", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(20.dp))

        if (permissionState.allPermissionsGranted) {

            // --- ¡NUEVA LÓGICA DE UI! ---
            if (isServiceBound) {
                // 1. Permisos OK y Servicio VINCULADO: Muestra la UI de escaneo
                ScanSection(
                    isScanning = isScanning,
                    onStartScan = { viewModel.startScan() },
                    onStopScan = { viewModel.stopScan() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                DeviceList(
                    devices = discoveredDevices,
                    onDeviceClick = { device -> viewModel.connectToDevice(device) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                // 2. Permisos OK, pero servicio VINCULANDO...: Muestra un Spinner
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Iniciando servicio Bluetooth...")
                }
            }
            // --- FIN DE LA NUEVA LÓGICA DE UI ---

        } else {
            // 3. Permisos PENDIENTES: Muestra la UI de permisos (sin cambios)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val textToShow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Esta app necesita permisos de 'Dispositivos Cercanos' y 'Notificaciones' para funcionar."
                } else {
                    "Esta app necesita permisos de Bluetooth y de 'Ubicación' para poder escanear."
                }

                Text(
                    text = textToShow,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(onClick = {
                    permissionState.launchMultiplePermissionRequest()
                }) {
                    Text("Conceder Permisos")
                }

                if (!permissionState.shouldShowRationale && !permissionState.allPermissionsGranted) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", context.packageName, null)
                        context.startActivity(intent)
                    }) {
                        Text("Abrir Ajustes")
                    }
                }
            }
        }

        // Esta parte siempre está visible en la parte inferior (sin cambios)
        Spacer(modifier = Modifier.height(16.dp))
        if (connectionState is ConnectionState.Connecting) {
            CircularProgressIndicator()
            Text("Conectando...")
        } else if (connectionState is ConnectionState.Error) {
            Text(
                "Error: ${(connectionState as ConnectionState.Error).message}",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}
