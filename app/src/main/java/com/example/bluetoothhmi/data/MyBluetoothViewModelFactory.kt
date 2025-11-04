package com.example.bluetoothhmi.data

import android.app.Application // <-- Import Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.bluetoothhmi.data.MyBluetoothViewModel // <-- ¡CORRECCIÓN AÑADIDA!

// Change the constructor parameter from Context to Application
class MyBluetoothViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // --- ¡MODIFICACIÓN! ---
        // Cambiamos de 'isAssignableFrom' a una comprobación de nombre de clase.
        // Esto evita un error de inferencia de tipos genéricos (generics)
        // que a veces ocurre con 'isAssignableFrom'.
        if (modelClass.name == MyBluetoothViewModel::class.java.name) {
            @Suppress("UNCHECKED_CAST")
            // Pass the application object directly
            return MyBluetoothViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

