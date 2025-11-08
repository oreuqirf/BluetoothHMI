package com.example.bluetoothhmi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {

    // Este efecto se ejecuta una sola vez cuando la pantalla aparece
    LaunchedEffect(key1 = true) {
        // 1. Espera 3 segundos
        delay(3000L) // 3000 milisegundos = 3 segundos

        // 2. Navega a la pantalla de conexión
        navController.navigate("connection_screen") {
            // 3. ¡Importante! Limpia la pantalla de Splash del historial de navegación.
            // Esto evita que el usuario pueda presionar "Atrás" y volver aquí.
            popUpTo("splash_screen") { inclusive = true }
            launchSingleTop = true
        }
    }

    // El contenido de tu pantalla "Acerca De"
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Bluetooth HMI",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Acerca De la Aplicación",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Nombre: [Nombre de tu App]")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Autor: [Tu Nombre o Compañía]")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Revisión: 1.0.0")
        }
    }
}
