package com.example.bluetoothhmi.data

/**
 * Representa los valores de "cero" y "escala completa" (full scale)
 * para un solo canal de ingeniería.
 */
data class ChannelScaling(
    val rawMin: Int = 800,
    val rawMax: Int = 4000,
    val zero: Float = 0.0f,
    val full: Float = 100.0f
)


// ... (ChannelScaling data class)

data class ScalingData(
    val ai0: ChannelScaling = ChannelScaling(),
    val ai1: ChannelScaling = ChannelScaling(),
    val ai2: ChannelScaling = ChannelScaling(),
    val ai3: ChannelScaling = ChannelScaling(),
    val ao0: ChannelScaling = ChannelScaling(),
    val ao1: ChannelScaling = ChannelScaling(),
    // propieda que permite un cambio en cada bloque que se consulta
    // para que los datos no sean iguales y se dispare la actualizacion
    val lastUpdated: Long = System.currentTimeMillis()
) {

    /**
     * Devuelve una NUEVA copia de ScalingData con un canal actualizado.
     */
    fun updateChannel(channelName: String, newScaling: ChannelScaling): ScalingData {
        // 2. AÑADE 'lastUpdated' A CADA .copy()
        return when (channelName) {
            "AI0" -> this.copy(ai0 = newScaling, lastUpdated = System.currentTimeMillis())
            "AI1" -> this.copy(ai1 = newScaling, lastUpdated = System.currentTimeMillis())
            "AI2" -> this.copy(ai2 = newScaling, lastUpdated = System.currentTimeMillis())
            "AI3" -> this.copy(ai3 = newScaling, lastUpdated = System.currentTimeMillis())
            "AO0" -> this.copy(ao0 = newScaling, lastUpdated = System.currentTimeMillis())
            "AO1" -> this.copy(ao1 = newScaling, lastUpdated = System.currentTimeMillis())
            else -> this
        }
    }
}
