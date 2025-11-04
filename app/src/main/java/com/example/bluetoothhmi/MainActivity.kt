package com.example.bluetoothhmi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.bluetoothhmi.AppNavigation
import com.example.bluetoothhmi.ui.theme.BluetoothHMITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BluetoothHMITheme {
                AppNavigation() // <-- Llama a la navegación aquí
            }
        }
    }
}
