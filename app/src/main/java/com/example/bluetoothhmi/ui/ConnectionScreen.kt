package com.example.bluetoothhmi.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log // <-- Import añadido
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

import com.example.bluetoothhmi.data.MyBluetoothViewModel
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

    // --- 1. ESTE LAUNCHEDEFFECT INICIA EL SERVICIO ---
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

    // --- 2. LÓGICA DE PERMISOS (¡CORREGIDA!) ---
    // Aquí estaba el error de compilación.
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ (API 33)
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS // ¡Crítico para el Foreground Service!
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12 (API 31-32)
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        // Android 11 e inferior
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
    val permissionState = rememberMultiplePermissionsState(permissions = permissions)

    // --- 3. LAUNCHEDEFFECT PARA PROMOVER EL SERVICIO (sin cambios) ---
    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) {
            Log.d("ConnectionScreen", "Permisos concedidos, promoviendo servicio a FG.")
            viewModel.<promoteServiceToForeground()
        }
    }

    // --- NAVEGACIÓN (sin cambios) ---
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            navController.navigate("device_data_screen")
        }
    }

    // --- DIÁLOGO MIUI ---
    if (showMiuiDialog) {
        MiuiOptimizationDialog(
            onDismiss = { viewModel.onDismissMiuiWarning() }
        )
    }

    // Listener para los mensajes de error de escaneo
    LaunchedEffect(key1 = true) {
        viewModel.scanErrorEvent.collect { message ->
            // Muestra el error
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // --- UI de la Pantalla de Conexión (¡ESTRUCTURA CORREGIDA!) ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Conexión Bluetooth", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(20.dp))

        // --- 4. LÓGICA DE LAYOUT CORREGIDA ---
        // El if/else ahora solo controla la PARTE MEDIA de la pantalla.
        // El Spacer y el ConnectionState de abajo siempre estarán visibles.
        if (permissionState.allPermissionsGranted) {
            // --- SI SÍ TIENE PERMISOS, MUESTRA LA APP NORMAL ---
            ScanSection(
                isScanning = isScanning,
                onStartScan = { viewModel.startScan() },
                onStopScan = { viewModel.stopScan() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            DeviceList(
                devices = discoveredDevices,
                onDeviceClick = { device -> viewModel.connectToDevice(device) },
                modifier = Modifier.weight(1f) // <-- Correcto
            )
        } else {
            // --- NO TIENE PERMISOS: Muestra la explicación ---
            // ¡CAMBIO! Se usa Modifier.weight(1f) para que ocupe el espacio
            // central y empuje el estado de conexión hacia abajo.
            Column(
                modifier = Modifier
                    .fillMaxWidth() // No fillMaxSize
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val textToShow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Esta app necesita permisos de 'Dispositivos Cercanos' (Bluetooth) y 'Notificaciones' para funcionar."
                } else {
                    "Esta app necesita permisos de Bluetooth y de 'Ubicación' para poder escanear dispositivos cercanos."
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

                // Botón para ir a Ajustes si el usuario los denegó permanentemente
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

        // --- 5. ESTA PARTE AHORA ESTÁ FUERA DEL IF/ELSE (CORRECTO) ---
        Spacer(modifier = Modifier.height(16.dp))

        // Muestra el estado de la conexión
        if (connectionState is ConnectionState.Connecting) {
            CircularProgressIndicator()
            Text("Conectando...")
        } else if (connectionState is ConnectionState.Error) {
            Text(
                "Error: ${(connectionState as ConnectionState.Error).message}",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center // Añadido para errores largos
            )
        }
    }
}
