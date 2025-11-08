package com.example.bluetoothhmi

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import android.app.Application
import androidx.compose.ui.platform.LocalContext

// Importa todas tus pantallas
import com.example.bluetoothhmi.ui.SplashScreen
import com.example.bluetoothhmi.ui.ConnectionScreen
import com.example.bluetoothhmi.ui.DeviceDataScreen // Pantalla de Configuración
import com.example.bluetoothhmi.ui.DashboardScreen  // Pantalla de Gráfico

// Importa el ViewModel y la Factory
import com.example.bluetoothhmi.data.MyBluetoothViewModelFactory
import com.example.bluetoothhmi.data.MyBluetoothViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Obtiene el contexto de Application para la Factory
    val application = LocalContext.current.applicationContext as Application

    // Crea una instancia única del ViewModel usando la Factory
    val viewModel: MyBluetoothViewModel = viewModel(
        factory = MyBluetoothViewModelFactory(application)
    )

    // El NavHost ahora empieza en "splash_screen"
    NavHost(navController = navController, startDestination = "splash_screen") {

        // Pantalla 0: Splash (Inicio)
        composable("splash_screen") {
            SplashScreen(navController = navController)
            // Esta pantalla maneja su propia redirección a "connection_screen"
        }

        // Pantalla 1: Conexión
        composable("connection_screen") {
            ConnectionScreen(
                navController = navController,
                viewModel = viewModel
            )
            // Esta pantalla navega a "dashboard_screen" al conectar
        }

        // Pantalla 2: Dashboard de Sensores (Gráfico)
        composable("dashboard_screen") {
            DashboardScreen(
                navController = navController,
                viewModel = viewModel
            )
            // Esta pantalla tiene un botón para ir a "device_data_screen"
        }

        // Pantalla 3: Datos del Dispositivo (Configuración)
        composable("device_data_screen") {
            DeviceDataScreen(
                navController = navController,
                viewModel = viewModel
            )
            // Esta pantalla tiene un botón "Atrás" para volver a "dashboard_screen"
        }
    }
}
