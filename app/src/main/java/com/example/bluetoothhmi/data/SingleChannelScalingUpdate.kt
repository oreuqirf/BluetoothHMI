package com.example.bluetoothhmi.data

/**
 * Contiene los datos de escalado para un solo canal,
 * devueltos por el parser.
 */
data class SingleChannelScalingUpdate(
    val channelName: String,
    val scaling: ChannelScaling
)