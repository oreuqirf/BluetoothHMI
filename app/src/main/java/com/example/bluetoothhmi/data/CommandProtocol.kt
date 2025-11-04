package com.example.bluetoothhmi.data

object CommandProtocol {

    // --- Comandos de Solicitud (App -> Dispositivo) ---

    /**
     * Solicita el bloque de datos de identificación.
     * Respuesta esperada: "TYPE=ID;DID=...,FW=...,HW=...,CFG=..."
     */
    const val GET_IDENTIFICATION = "GET_ID;\n"

    /**
     * Solicita el bloque de datos de TIEMPO actual.
     * Respuesta esperada: "TYPE=TIME;TIME=..."
     */
    const val GET_DEVICE_TIME = "GET_TIME;\n"

    /**
     * Solicita el bloque de datos de los sensores.
     * (Lo usaremos en el futuro)
     * Respuesta esperada: "TYPE=SENSORS;DI=5,AI1=1.2,AI2=3.4,AI3=0.5,AI4=9.9,DO=1,AO1=4.5,AO2=2.1,BATT=3.7\n"
     */
    const val GET_SENSORS = "GET_SENSORS;\n"

    /**
     * Solicita el bloque de Modo de Trabajo.
     * Respuestas esperadas:
     * "TYPE=MODE;MODE=PERIODIC,INT=15\n"
     * "TYPE=MODE;MODE=FIXED,T1=08:00,T2=12:00,T3=18:00\n"
     */
    const val GET_WORK_MODE = "GET_MODE;\n"

    // --- Comandos de Configuración (App -> Dispositivo) ---
    // (Aquí podrías añadir comandos futuros)
    // fun setConfiguration(config: String) = "SET_CONFIG=$config\n"

    /**
     * Solicita el bloque de datos de escalado.
     * Respuesta esperada: "TYPE=SCALE;AI1Z=0,AI1F=100,AI2Z=0,AI2F=50,...\n"
     */
    const val GET_SCALING = "GET_SCALE;\n"

    const val GET_GPS = "GET_GPS;\n"

    const val SET_LOGOUT = "SET_LOGOUT;\n"


    /**
     * Genera el comando para ESTABLECER la hora del dispositivo.
     * Asume el formato: "SET_TIME;EPOCH=1730260779"
     */
    fun setDeviceTime(epochSeconds: Long): String {
        return "SET_TIME;EPOCH=$epochSeconds\n"
    }

    /**
     * Genera el comando para cambiar el estado de una Salida Digital.
     * @param channel El índice de la salida (ej: 0 para DO1, 1 para DO2, etc.)
     * @param state El estado (true para 1/ON, false para 0/OFF)
     */
    fun setDigitalOutput(channel: Int, state: Boolean): String {
        val stateValue = if (state) 1 else 0
        // Asume un comando como: "SET_DO;CH=0,STATE=1\n"
        // Ajusta este string al comando real que espera tu dispositivo.
        return "SET_DO;CH=$channel,STATE=$stateValue\n"
    }

    /**
     * Solicita los datos de escalado de UN SOLO canal.
     * Respuesta esperada: "TYPE=SCALE_CH;CH=AI0,RMIN=0,RMAX=4000,ZERO=0,FULL=100\n"
     */
    fun getChannelScaling(channel: String): String {
        return "GET_SCALE_CH;CH=$channel\n"
    }
    /**
     * Genera el comando para establecer el escalado de un canal específico.
     */
    fun setChannelScaling(
        channel: String,
        rawMin: Int,
        rawMax: Int,
        zero: Float,
        full: Float
    ): String {
        // Asegúrate de que este string coincida con lo que espera tu dispositivo
        return "SET_SCALE;CH=$channel,RMIN=$rawMin,RMAX=$rawMax,ZERO=$zero,FULL=$full\n"
    }

    fun setWorkModePeriodic(
        intervalMinutes: Int,
        powerTimeSeconds: Int): String {
        return "SET_MODE;MODE=PERIODIC,INT=$intervalMinutes,POWER=$powerTimeSeconds\n"
    }

    /**
     * Envía el comando para configurar el modo de Horas Fijas.
     * Siempre enviará 5 campos (T1 a T5), rellenando con vacío si es necesario.
     */
    fun setWorkModeFixed(times: List<String>,powerTimeSeconds: Int): String {

        // 1. Asegurarse de que tengamos exactamente 5 elementos
        // Rellena con "" si la lista original es más corta.
        val fixedSizeTimes = (0 until 5).map { index ->
            times.getOrNull(index) ?: ""
        }

        // 2. Construye el string de horas (ej: "T1=08:00,T2=,T3=12:00,T4=,T5=")
        val timesString = fixedSizeTimes
            // .filter { it.isNotBlank() } // <-- ¡ASEGÚRATE DE QUITAR ESTA LÍNEA!
            .mapIndexed { index, time -> "T${index + 1}=$time" }
            .joinToString(separator = ",")

        // El comando final incluirá todos los campos, ej:
        // "SET_MODE;MODE=FIXED,T1=08:00,T2=,T3=12:00,T4=,T5=\n"
        return "SET_MODE;MODE=FIXED,$timesString,POWER=$powerTimeSeconds\n"
    }

    /**
     * Envía el comando para habilitar el modo de trabajo continuo.
     */
    fun setWorkModeContinuous(intervalMinutes: Int,powerTimeSeconds: Int): String {
        return "SET_MODE;MODE=CONTINUOUS,INT=$intervalMinutes,POWER=$powerTimeSeconds\n"
    }

    fun setWorkModeDisabled(): String {
        return "SET_MODE;MODE=DISABLED\n"
    }

}
