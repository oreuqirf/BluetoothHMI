package com.example.bluetoothhmi.data


data class SensorData(
    /** Valor entero que representa el estado de todas las entradas digitales (bitmask) */
    val digitalInputs: Int,
    /** Lista de 4 valores flotantes para las entradas analógicas */
    val analogInputs: List<Float>,
    /** Valor entero que representa el estado de todas las salidas digitales (bitmask) */
    val digitalOutputs: Int,
    /** Lista de 2 valores flotantes para las salidas analógicas */
    val analogOutputs: List<Float>,
    /** Valor flotante para el voltaje de la batería */
    val batteryVoltage: Float
)