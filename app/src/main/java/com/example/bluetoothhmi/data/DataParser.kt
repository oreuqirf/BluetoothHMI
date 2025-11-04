package com.example.bluetoothhmi.data

import android.util.Log

object DataParser {

    // --- Helpers (movidos arriba para reutilizarlos) ---
    private val defaultScaling = ChannelScaling()
    private fun getFloatVal(parts: List<String>, prefix: String, default: Float) = parts.find { it.startsWith(prefix) }
        ?.removePrefix(prefix)?.toFloatOrNull() ?: default
    private fun getIntVal(parts: List<String>, prefix: String, default: Int) = parts.find { it.startsWith(prefix) }
        ?.removePrefix(prefix)?.toIntOrNull() ?: default


    fun parse(data: ByteArray): Any? {
        val text = String(data).trim()

        // Primero, identificamos el tipo de bloque
        if (text.startsWith("TYPE=ID;")) {
            return parseIdentification(text)
        }
    // Primero, identificamos el tipo de bloque
        if (text.startsWith("TYPE=TIME;")) {
            return parseDeviceTime(text)
        }
        if (text.startsWith("TYPE=SENSORS;")) {
            return parseSensorData(text)
        }
        if (text.startsWith("TYPE=MODE;")) {
            return parseWorkMode(text)
        }
        // Respuesta de escalado COMPLETA
        if (text.startsWith("TYPE=SCALE;")) {
            return parseScalingDataAll(text)
        }
        // Respuesta de escalado INDIVIDUAL
        if (text.startsWith("TYPE=SCALE_CH;")) {
            // --- 2. AÑADE ESTA LÍNEA DE LOG ---
            Log.d("DataParser", "Parseando escalado de canal: $text")
            return parseScalingDataSingle(text)
        }
        // --- 2. AÑADE EL CASO PARA GPS ---
        if (text.startsWith("TYPE=GPS;")) {
            Log.d("DataParser", "Parseando GPS Data: $text")
            return parseGpsData(text)
        }
        return null // Devuelve null si no se reconoce el formato
    }

    private fun parseIdentification(text: String): DeviceIdentification? {
        try {
            val content = text.removePrefix("TYPE=ID;")
            val parts = content.split(',')

            // Usamos el operador Elvis (?:) para dar un valor por defecto si no se encuentra el campo
            val deviceID = parts.find { it.startsWith("DID=") }?.removePrefix("DID=") ?: ""
            val firmwareVersion = parts.find { it.startsWith("FW=") }?.removePrefix("FW=") ?: ""
            val hardwareVersion = parts.find { it.startsWith("HW=") }?.removePrefix("HW=") ?: ""
            val lastConfigDate = parts.find { it.startsWith("CFG=") }?.removePrefix("CFG=") ?: ""

            return DeviceIdentification(
                deviceID = deviceID,
                firmwareVersion = firmwareVersion,
                hardwareVersion = hardwareVersion,
                lastConfigurationDate = lastConfigDate
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseDeviceTime(text: String): DeviceTime? {
        try {
            // Asume formato: "TYPE=TIME;EPOCH=1730260779"
            // El split(',') es por si en el futuro añades más campos
            val content = text.removePrefix("TYPE=TIME;")
            val parts = content.split(',')

            // Usa el helper 'getLongVal' que creamos
            val epoch = getLongVal(parts, "EPOCH=", 0L)

            // Devuelve el objeto DeviceTime que tu ViewModel espera
            return DeviceTime(epoch)

        } catch (e: Exception) {
            Log.e("DataParser", "Error parseando DeviceTime: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun parseWorkMode(text: String): WorkMode? {
        try {
            val content = text.removePrefix("TYPE=MODE;")
            val parts = content.split(',')
            val mode = parts.find { it.startsWith("MODE=") }?.removePrefix("MODE=")

            return when (mode) {
                "PERIODIC" -> {
                    val interval = getIntVal(parts, "INT=", 0) // Usando tu helper
                    // POWER
                    val power = getIntVal(parts, "POWER=", 0)
                    WorkMode.Periodic(interval, power)
                }
                "FIXED" -> {
                    // Busca todas las horas (T1 a T5) y las añade a una lista
                    val times = mutableListOf<String>()
                    parts.find { it.startsWith("T1=") }?.removePrefix("T1=")?.let { times.add(it) }
                    parts.find { it.startsWith("T2=") }?.removePrefix("T2=")?.let { times.add(it) }
                    parts.find { it.startsWith("T3=") }?.removePrefix("T3=")?.let { times.add(it) }
                    parts.find { it.startsWith("T4=") }?.removePrefix("T4=")?.let { times.add(it) }
                    parts.find { it.startsWith("T5=") }?.removePrefix("T5=")?.let { times.add(it) }
                    // --- POWER ---
                    val power = getIntVal(parts, "POWER=", 0)
                    WorkMode.Fixed(times, power)
                }
                "CONTINUOUS" -> {
                    val interval = getIntVal(parts, "INT=", 0)
                    val power = getIntVal(parts, "POWER=", 0)
                    WorkMode.Continuous(interval, power)
                }
                else -> WorkMode.Disabled
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return WorkMode.Disabled
        }
    }

    private fun parseSensorData(text: String): SensorData? {
        try {
            val content = text.removePrefix("TYPE=SENSORS;")
            val parts = content.split(',')

            // Extrae cada valor, con un valor por defecto (0 o 0.0f) si no se encuentra
            val di = parts.find { it.startsWith("DI=") }?.removePrefix("DI=")?.toIntOrNull() ?: 0
            val ai0 = parts.find { it.startsWith("AI0=") }?.removePrefix("AI0=")?.toFloatOrNull() ?: 0.0f
            val ai1 = parts.find { it.startsWith("AI1=") }?.removePrefix("AI1=")?.toFloatOrNull() ?: 0.0f
            val ai2 = parts.find { it.startsWith("AI2=") }?.removePrefix("AI2=")?.toFloatOrNull() ?: 0.0f
            val ai3 = parts.find { it.startsWith("AI3=") }?.removePrefix("AI3=")?.toFloatOrNull() ?: 0.0f
            val do_ = parts.find { it.startsWith("DO=") }?.removePrefix("DO=")?.toIntOrNull() ?: 0
            val ao0 = parts.find { it.startsWith("AO0=") }?.removePrefix("AO0=")?.toFloatOrNull() ?: 0.0f
            val ao1 = parts.find { it.startsWith("AO1=") }?.removePrefix("AO1=")?.toFloatOrNull() ?: 0.0f
            val batt = parts.find { it.startsWith("BATT=") }?.removePrefix("BATT=")?.toFloatOrNull() ?: 0.0f

            return SensorData(
                digitalInputs = di,
                analogInputs = listOf(ai0, ai1, ai2, ai3),
                digitalOutputs = do_,
                analogOutputs = listOf(ao0, ao1),
                batteryVoltage = batt
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Parsea la respuesta del escalado de TODOS los canales
     */
    private fun parseScalingDataAll(text: String): ScalingData? {
        try {
            val content = text.removePrefix("TYPE=SCALE;")
            val parts = content.split(',')

            return ScalingData(
                ai0 = ChannelScaling(
                    rawMin = getIntVal(parts, "AI0RMIN=", defaultScaling.rawMin),
                    rawMax = getIntVal(parts, "AI0RMAX=", defaultScaling.rawMax),
                    zero = getFloatVal(parts, "AI0Z=", defaultScaling.zero),
                    full = getFloatVal(parts, "AI0F=", defaultScaling.full)
                ),
                ai1 = ChannelScaling(rawMin = getIntVal(parts, "AI1RMIN=", defaultScaling.rawMin), /*...etc...*/),
                ai2 = ChannelScaling(rawMin = getIntVal(parts, "AI2RMIN=", defaultScaling.rawMin), /*...etc...*/),
                ai3 = ChannelScaling(rawMin = getIntVal(parts, "AI3RMIN=", defaultScaling.rawMin), /*...etc...*/),
                ao0 = ChannelScaling(rawMin = getIntVal(parts, "AO0RMIN=", defaultScaling.rawMin), /*...etc...*/),
                ao1 = ChannelScaling(rawMin = getIntVal(parts, "AO1RMIN=", defaultScaling.rawMin), /*...etc...*/)
            )
        } catch (e: Exception) { e.printStackTrace(); return null }
    }

    /**
     * Parsea la respuesta del escalado de UN SOLO canal
     */
    private fun parseScalingDataSingle(text: String): SingleChannelScalingUpdate? {
        try {
            val content = text.removePrefix("TYPE=SCALE_CH;")
            val parts = content.split(',')

            val channel = parts.find { it.startsWith("CH=") }?.removePrefix("CH=") ?: return null

            val rMin = getIntVal(parts, "RMIN=", defaultScaling.rawMin)
            val rMax = getIntVal(parts, "RMAX=", defaultScaling.rawMax)
            val zero = getFloatVal(parts, "ZERO=", defaultScaling.zero)
            val full = getFloatVal(parts, "FULL=", defaultScaling.full)

            // --- AÑADE ESTOS LOGS DE DEPURACIÓN ---
            Log.d("DataParser", "Canal parseado: $channel")
            Log.d("DataParser", "Valores rMin/rMax: $rMin / $rMax")
            Log.d("DataParser", "Valores zero/full: $zero / $full")
            // ------------------------------------

            return SingleChannelScalingUpdate(
                channelName = channel,
                scaling = ChannelScaling(rMin, rMax, zero, full)
            )
        } catch (e: Exception) {
            // --- AÑADE ESTE LOG DE ERROR ---
            Log.e("DataParser", "Error al parsear canal individual: ${e.message}")
            e.printStackTrace(); return null
        }
    }

    private fun getLongVal(parts: List<String>, prefix: String, default: Long) = parts.find { it.startsWith(prefix) }
        ?.removePrefix(prefix)?.toLongOrNull() ?: default

    private fun parseGpsData(text: String): GpsData? {
        try {
            // Log: TYPE=GPS;LAT=-3284068,LON=-6887323,ALT=-3284068,SPD=-6887323,FIX=-3284068,SATS=-6887323
            val content = text.removePrefix("TYPE=GPS;")
            val parts = content.split(',')

            // 1. Parsea Lat/Lon (Long) y conviértelos a Double
            // Asumimos 6 decimales. (ej: -3284068 -> -3.284068)
            val latDouble = getLongVal(parts, "LAT=", 0L) / 100_000.0
            val lonDouble = getLongVal(parts, "LON=", 0L) / 100_000.0

            // 2. Parsea Alt/Spd (que vienen corruptos como Long) y conviértelos.
            // ¡DEBES AJUSTAR EL DIVISOR! Asumo 100.0 para altitud (metros)
            // y 100.0 para velocidad (km/h), pero el firmware está mal.
            // Por ahora, solo los parseamos para que no crasheen.
            val altFloat = getLongVal(parts, "ALT=", 0L).toFloat() // O divídelo si sabes el factor
            val spdFloat = getLongVal(parts, "SPD=", 0L).toFloat() // O divídelo si sabes el factor

            // 3. Parsea Fix/Sats (vienen corruptos, pero los lee como Int)
            val fixInt = getIntVal(parts, "FIX=", 0)
            val satsInt = getIntVal(parts, "SATS=", 0)

            return GpsData(
                latitude = latDouble,
                longitude = lonDouble,
                altitude = altFloat, // <-- Pasa el Float
                speed = spdFloat,   // <-- Pasa el Float
                fixQuality = fixInt,
                satellites = satsInt
            )
        } catch (e: Exception) {
            Log.e("DataParser", "Error parseando GpsData: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

}
