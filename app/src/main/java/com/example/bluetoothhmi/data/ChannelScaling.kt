package com.example.bluetoothhmi.data

data class ChannelScaling(
    // Valores por defecto para el constructor
    val rawMin: Int = DEFAULT_RAW_MIN,
    val rawMax: Int = DEFAULT_RAW_MAX,
    val zero: Float = DEFAULT_ZERO,
    val full: Float = DEFAULT_FULL
) {
    companion object {
        // --- ¡AQUÍ ESTÁN LOS VALORES QUE FALTABAN! ---
        // Estos son los valores que el DataParser usará si la trama no los incluye.
        const val DEFAULT_RAW_MIN = 800
        const val DEFAULT_RAW_MAX = 4000
        const val DEFAULT_ZERO = 0.0f
        const val DEFAULT_FULL = 100.0f

        // Una instancia por defecto para usar en otros lugares si es necesario
        val DEFAULT = ChannelScaling(DEFAULT_RAW_MIN, DEFAULT_RAW_MAX, DEFAULT_ZERO, DEFAULT_FULL)
    }
}