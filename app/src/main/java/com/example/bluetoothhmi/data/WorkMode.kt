package com.example.bluetoothhmi.data

sealed class WorkMode {
    /**
     * El dispositivo transmite cada 'x' minutos.
     */
    data class Periodic(
        val intervalMinutes: Int,
        val powerOnTime: Int
    ) : WorkMode()

    /**
     * El dispositivo transmite en horas fijas.
     * La lista puede tener de 0 a 5 horarios (ej: "08:00", "14:30").
     */
    data class Fixed(
        val times: List<String>,
        val powerOnTime: Int
    ) : WorkMode()

    data class Continuous(
        val intervalMinutes: Int,
        val powerOnTime: Int
    ) : WorkMode()

    /**
     * El modo de trabajo está deshabilitado o es desconocido.
     */
    object Disabled : WorkMode()
}
