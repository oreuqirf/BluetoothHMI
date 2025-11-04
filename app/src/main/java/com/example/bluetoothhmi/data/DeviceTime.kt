package com.example.bluetoothhmi.data


// 1. Usa "data class" para el manejo de estado.
// 2. Usa "val" para convertir el parámetro del constructor en una propiedad.
// 3. Usa "Long" como te recomendé para evitar el problema Y2K38.
data class DeviceTime(
    /** Valor Long que representa el tiempo del dispositivo en formato epoch */
    val time: Long
)