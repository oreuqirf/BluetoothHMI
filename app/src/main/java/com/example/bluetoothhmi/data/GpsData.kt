package com.example.bluetoothhmi.data

data class GpsData (
    val latitude: Double,
    val longitude: Double,
    val altitude: Float,
    val speed: Float,
    val fixQuality: Int, // 0=No Fix, 1=Fix 2D/3D, 2=Fix Diferencial
    val satellites: Int
    )

