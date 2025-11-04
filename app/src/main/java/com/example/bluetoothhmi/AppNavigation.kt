package com.example.bluetoothhmi

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import android.app.Application
import androidx.compose.ui.platform.LocalContext

import com.example.bluetoothhmi.ui.ConnectionScreen
import com.example.bluetoothhmi.ui.DeviceDataScreen

import com.example.bluetoothhmi.data.MyBluetoothViewModelFactory
import com.example.bluetoothhmi.data.MyBluetoothViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    val application = LocalContext.current.applicationContext as Application

    val viewModel: MyBluetoothViewModel = viewModel(
        factory = MyBluetoothViewModelFactory(application) // This call now matches the factory's constructor
    )

    NavHost(navController = navController, startDestination = "connection_screen") {
        // Pantalla 1: Conexión
        composable("connection_screen") {
            ConnectionScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        // Pantalla 2: Datos del Dispositivo
        composable("device_data_screen") {
            DeviceDataScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}

