package com.example.bluetoothhmi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun DashboardScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dashboard")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("connection_screen") }) {
            Text("Connection Screen")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { navController.navigate("device_data_screen") }) {
            Text("Device Data Screen")
        }
    }
}
