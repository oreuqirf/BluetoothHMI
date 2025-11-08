package com.example.bluetoothhmi.data

import android.util.Log

object DataParser {

    // --- Helpers (getFloatVal, getIntVal, getLongVal) ---
    private fun getFloatVal(parts: List<String>, prefix: String, default: Float) = parts.find { it.startsWith(prefix) }
        ?.removePrefix(prefix)?.toFloatOrNull() ?: default
    private fun getIntVal(parts: List<String>, prefix: String, default: Int) = parts.find { it.startsWith(prefix) }
        ?.removePrefix(prefix)?.toIntOrNull() ?: default
    private fun getLongVal(parts: List<String>, prefix: String, default: Long) = parts.find { it.startsWith(prefix) }
        ?.removePrefix(prefix)?.toLongOrNull() ?: default

    // --- Helper para Strings ---
    private fun getStringVal(parts: List<String>, prefix: String, default: String) = parts.find { it.startsWith(prefix) }
        ?.removePrefix(prefix) ?: default

    fun parse(data: ByteArray): Any? {
        val text = String(data).trim()

        if (text.startsWith("TYPE=ID;")) {
            return parseIdentification(text)
        }
        if (text.startsWith("TYPE=SENSORS;")) {
            Log.d("DataParser", "Parseando SENSORS: $text")
            return parseSensorData(text)
        }
        if (text.startsWith("TYPE=MODE;")) {
            return parseWorkMode(text)
        }
        if (text.startsWith("TYPE=TIME;")) {
            return parseDeviceTime(text)
        }
        if (text.startsWith("TYPE=GPS;")) {
            Log.d("DataParser", "Parseando GPS Data: $text")
            return parseGpsData(text)
        }
        if (text.startsWith("TYPE=SCALE;")) {
            return parseScalingDataAll(text)
        }
        if (text.startsWith("TYPE=SCALE_CH;")) {
            return parseScalingDataSingle(text)
        }
        return null
    }

    private fun parseIdentification(text: String): DeviceIdentification? {
        try {
            val content = text.removePrefix("TYPE=ID;")
            val parts = content.split(',')
            return DeviceIdentification(
                deviceID = getStringVal(parts, "DID=", "N/A"),
                firmwareVersion = getStringVal(parts, "FW=", "N/A"),
                hardwareVersion = getStringVal(parts, "HW=", "N/A"),
                lastConfigurationDate = getStringVal(parts, "CFG=", "N/A")
            )
        } catch (e: Exception) { e.printStackTrace(); return null }
    }

    private fun parseWorkMode(text: String): WorkMode? {
        try {
            val content = text.removePrefix("TYPE=MODE;")
            val parts = content.split(',')
            val mode = getStringVal(parts, "MODE=", "DISABLED")
            val power = getIntVal(parts, "POWER=", 0) // Leído para todos los modos que lo usan

            return when (mode) {
                "PERIODIC" -> {
                    val interval = getIntVal(parts, "INT=", 0)
                    WorkMode.Periodic(interval, power)
                }
                "FIXED" -> {
                    val times = (1..5).map { getStringVal(parts, "T$it=", "") }
                    WorkMode.Fixed(times, power)
                }
                "CONTINUOUS" -> {
                    val interval = getIntVal(parts, "INT=", 0)
                    WorkMode.Continuous(interval, power) // Asumiendo que Continuous también lo usa
                }
                else -> WorkMode.Disabled
            }
        } catch (e: Exception) { e.printStackTrace(); return WorkMode.Disabled }
    }

    private fun parseSensorData(text: String): SensorData? {
        try {
            val content = text.removePrefix("TYPE=SENSORS;")
            val parts = content.split(',')

            val di = getIntVal(parts, "DI=", 0)
            val ai0 = getFloatVal(parts, "AI0=", 0.0f)
            val ai1 = getFloatVal(parts, "AI1=", 0.0f)
            val ai2 = getFloatVal(parts, "AI2=", 0.0f)
            val ai3 = getFloatVal(parts, "AI3=", 0.0f)
            val do_ = getIntVal(parts, "DO=", 0)
            val ao0 = getFloatVal(parts, "AO0=", 0.0f)
            val ao1 = getFloatVal(parts, "AO1=", 0.0f)
            val batt = getFloatVal(parts, "BATT=", 0.0f)

            return SensorData(
                digitalInputs = di,
                analogInputs = listOf(ai0, ai1, ai2, ai3),
                digitalOutputs = do_,
                analogOutputs = listOf(ao0, ao1),
                batteryVoltage = batt
                // El 'id' (timestamp) se añade automáticamente en la data class
            )
        } catch (e: Exception) { e.printStackTrace(); return null }
    }

    private fun parseDeviceTime(text: String): DeviceTime? {
        try {
            val content = text.removePrefix("TYPE=TIME;")
            val parts = content.split(',')
            val epoch = getLongVal(parts, "EPOCH=", 0L)
            return DeviceTime(epoch)
        } catch (e: Exception) { e.printStackTrace(); return null }
    }

    /**
     * Parsea la respuesta del GPS y del Módem (nueva definición).
     */
    private fun parseGpsData(text: String): GpsData? {
        try {
            // Asumo el nuevo formato:
            // "TYPE=GPS;LAT=-32890123;LON=-68840000;MODEL=XYZ;SREV=1.0;MID=12345"
            val content = text.removePrefix("TYPE=GPS;")
            val parts = content.split(',')

            val latInt = getLongVal(parts, "LAT=", 0L)
            val lonInt = getLongVal(parts, "LON=", 0L)
            val latDouble = latInt / 100_000.0
            val lonDouble = lonInt / 100_000.0

            val model = getStringVal(parts, "MOD=", "N/A")
            val softRevision = getStringVal(parts, "SOF=", "N/A")
            val mobileID = getStringVal(parts, "MID=", "N/A")

            return GpsData(
                latitude = latDouble,
                longitude = lonDouble,
                model = model,
                softRevision = softRevision,
                mobileID = mobileID
            )
        } catch (e: Exception) {
            Log.e("DataParser", "Error parseando GpsData: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun parseScalingDataAll(text: String): ScalingData? {
        try {
            val content = text.removePrefix("TYPE=SCALE;")
            val parts = content.split(',')
            val defaultScaling = ChannelScaling() // Asumiendo valores por defecto

            return ScalingData(
                ai0 = ChannelScaling(
                    rawMin = getIntVal(parts, "AI0RMIN=", defaultScaling.rawMin),
                    rawMax = getIntVal(parts, "AI0RMAX=", defaultScaling.rawMax),
                    zero = getFloatVal(parts, "AI0Z=", defaultScaling.zero),
                    full = getFloatVal(parts, "AI0F=", defaultScaling.full)
                ),
                ai1 = ChannelScaling(
                    rawMin = getIntVal(parts, "AI1RMIN=", defaultScaling.rawMin),
                    rawMax = getIntVal(parts, "AI1RMAX=", defaultScaling.rawMax),
                    zero = getFloatVal(parts, "AI1Z=", defaultScaling.zero),
                    full = getFloatVal(parts, "AI1F=", defaultScaling.full)
                ),
                ai2 = ChannelScaling(
                    rawMin = getIntVal(parts, "AI2RMIN=", defaultScaling.rawMin),
                    rawMax = getIntVal(parts, "AI2RMAX=", defaultScaling.rawMax),
                    zero = getFloatVal(parts, "AI2Z=", defaultScaling.zero),
                    full = getFloatVal(parts, "AI2F=", defaultScaling.full)
                ),
                ai3 = ChannelScaling(
                    rawMin = getIntVal(parts, "AI3RMIN=", defaultScaling.rawMin),
                    rawMax = getIntVal(parts, "AI3RMAX=", defaultScaling.rawMax),
                    zero = getFloatVal(parts, "AI3Z=", defaultScaling.zero),
                    full = getFloatVal(parts, "AI3F=", defaultScaling.full)
                ),
                ao0 = ChannelScaling(
                    rawMin = getIntVal(parts, "AO0RMIN=", defaultScaling.rawMin),
                    rawMax = getIntVal(parts, "AO0RMAX=", defaultScaling.rawMax),
                    zero = getFloatVal(parts, "AO0Z=", defaultScaling.zero),
                    full = getFloatVal(parts, "AO0F=", defaultScaling.full)
                ),
                ao1 = ChannelScaling(
                    rawMin = getIntVal(parts, "AO1RMIN=", defaultScaling.rawMin),
                    rawMax = getIntVal(parts, "AO1RMAX=", defaultScaling.rawMax),
                    zero = getFloatVal(parts, "AO1Z=", defaultScaling.zero),
                    full = getFloatVal(parts, "AO1F=", defaultScaling.full)
                )
            )
        } catch (e: Exception) { e.printStackTrace(); return null }
    }

    private fun parseScalingDataSingle(text: String): SingleChannelScalingUpdate? {
        try {
            val content = text.removePrefix("TYPE=SCALE_CH;")
            val parts = content.split(',')

            val channel = getStringVal(parts, "CH=", "N/A")
            if (channel == "N/A") return null

            val rMin = getIntVal(parts, "RMIN=", ChannelScaling.DEFAULT_RAW_MIN)
            val rMax = getIntVal(parts, "RMAX=", ChannelScaling.DEFAULT_RAW_MAX)
            val zero = getFloatVal(parts, "ZERO=", ChannelScaling.DEFAULT_ZERO)
            val full = getFloatVal(parts, "FULL=", ChannelScaling.DEFAULT_FULL)

            return SingleChannelScalingUpdate(
                channelName = channel,
                scaling = ChannelScaling(rMin, rMax, zero, full)
            )
        } catch (e: Exception) {
            Log.e("DataParser", "Error al parsear canal individual: ${e.message}")
            e.printStackTrace(); return null
        }
    }
}
