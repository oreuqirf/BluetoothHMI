package com.example.bluetoothhmi.data

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.contentValuesOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothhmi.connection.BluetoothService
import com.example.bluetoothhmi.connection.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

// --- Funciones Helper ---

private fun formatEpochSeconds(epochSeconds: Long?): String {
    if (epochSeconds == null) return "Presione la tarjeta para cargar la hora."
    if (epochSeconds == 0L) return "Hora no configurada."
    try {
        val instant = Instant.ofEpochSecond(epochSeconds)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        return localDateTime.format(formatter)
    } catch (e: Exception) {
        Log.e("FormatEpoch", "Error al formatear epoch: $epochSeconds", e)
        return "Hora inválida"
    }
}

data class ModoFuncionamientoUiState(
    val horas: String = "0",
    val minutos: String = "0"
) {
    val isMinutosEnabled: Boolean
        get() = (horas.toIntOrNull() ?: 0) != 24
}


class MyBluetoothViewModel(application: Application) : AndroidViewModel(application) {

    // --- Referencia al Servicio Vinculado ---
    private val _service = MutableStateFlow<BluetoothService?>(null)

    // Expone si el servicio está vinculado y listo.
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    // Almacena si la UI ya nos dijo que los permisos están OK
    private var hasUiReportedPermissions = false

    // --- Flujos Públicos (Espejos del Servicio) ---
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // --- Diálogo de advertencia de MIUI ---
    private val _showMiuiWarningDialog = MutableStateFlow(false)
    val showMiuiWarningDialog = _showMiuiWarningDialog.asStateFlow()
    private var miuiWarningShownThisSession = false

    // --- Flujos de Datos Parseados ---
    private val _identificationData = MutableStateFlow<DeviceIdentification?>(null)
    val identificationData: StateFlow<DeviceIdentification?> = _identificationData.asStateFlow()
    private val _workModeData = MutableStateFlow<WorkMode?>(null)
    val workModeData: StateFlow<WorkMode?> = _workModeData.asStateFlow()
    private val _sensorData = MutableStateFlow<SensorData?>(null)
    val sensorData: StateFlow<SensorData?> = _sensorData.asStateFlow()
    private val _scalingData = MutableStateFlow<ScalingData?>(null)
    val scalingData: StateFlow<ScalingData?> = _scalingData.asStateFlow()
    private val _gpsData = MutableStateFlow<GpsData?>(null)
    val gpsData: StateFlow<GpsData?> = _gpsData.asStateFlow()
    private val _deviceTime = MutableStateFlow<DeviceTime?>(null)
    val deviceTime: StateFlow<DeviceTime?> = _deviceTime.asStateFlow()
    private val _isEditingWorkMode = MutableStateFlow(false)
    val isEditingWorkMode: StateFlow<Boolean> = _isEditingWorkMode.asStateFlow()
    private val _modoFuncionamientoState = MutableStateFlow(ModoFuncionamientoUiState())
    val modoFuncionamientoState = _modoFuncionamientoState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    // Flujo para notificar a la UI de errores de escaneo
    private val _scanErrorEvent = MutableSharedFlow<String>()
    val scanErrorEvent = _scanErrorEvent.asSharedFlow()


    // --- Lógica de Vinculación al Servicio ---
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.LocalBinder
            val serviceInstance = binder.getService()
            _service.value = serviceInstance // 1. El servicio se vincula
            _isServiceBound.value = true      // 2. ¡Notificamos a la UI!
            Log.d("MyViewModel", "Servicio Bluetooth VINCULADO")

            // 3. Nos suscribimos y comprobamos permisos
            subscribeToServiceFlows(serviceInstance)
            checkPermissionsAndPromoteService(serviceInstance)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("MyViewModel", "Servicio Bluetooth DESVINCULADO")
            _service.value = null
            _isServiceBound.value = false // ¡Notificamos a la UI!
        }
    }

    init {
        // --- VINCULARSE AL SERVICIO ---
        Intent(application, BluetoothService::class.java).also { intent ->
            application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun subscribeToServiceFlows(service: BluetoothService) {
        viewModelScope.launch {
            service.connectionState.collect { _connectionState.value = it }
        }
        viewModelScope.launch {
            service.discoveredDevices.collect { _discoveredDevices.value = it }
        }
        viewModelScope.launch {
            service.isScanning.collect { _isScanning.value = it }
        }

        // Suscríbete al nuevo flujo de errores
        viewModelScope.launch {
            service.scanError.collect {
                _scanErrorEvent.emit(it)
            }
        }

        viewModelScope.launch {
            val parsedDataFlow = service.receivedData.map { DataParser.parse(it) }

            parsedDataFlow.collect { parsedObject ->
                when (parsedObject) {
                    is DeviceIdentification -> _identificationData.value = parsedObject
                    is WorkMode -> _workModeData.value = parsedObject
                    is SensorData -> _sensorData.value = parsedObject
                    is DeviceTime -> _deviceTime.value = parsedObject
                    is GpsData -> _gpsData.value = parsedObject
                    is ScalingData -> _scalingData.value = parsedObject
                    is SingleChannelScalingUpdate -> {
                        val currentData = _scalingData.value ?: ScalingData()
                        _scalingData.value = currentData.updateChannel(
                            parsedObject.channelName,
                            parsedObject.scaling
                        )
                    }
                }
            }
        }
    }

    /**
     * Comprueba si los permisos están concedidos y, de ser así,
     * promueve el servicio a primer plano.
     */
    private fun checkPermissionsAndPromoteService(service: BluetoothService) {
        // Solo promovemos si la UI ya nos dijo que los permisos están OK
        if (hasUiReportedPermissions) {
            Log.d("MyViewModel", "Permisos OK. Promoviendo servicio a FG.")
            service.startAsForeground()
        } else {
            Log.w("MyViewModel", "Servicio vinculado, pero la UI no ha reportado permisos. Esperando...")
        }
    }

    // --- Métodos de la UI ---

    fun hasPermissions(): Boolean {
        // Usamos el contexto de la aplicación
        return BluetoothService.getRequiredPermissions().all { permission ->
            ActivityCompat.checkSelfPermission(
                getApplication(),
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isMiui(): Boolean {
        val context = getApplication<Application>().applicationContext
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                !context.packageManager.queryIntentActivities(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    ), 0
                ).isEmpty()
    }

    /**
     * Llama a esta función desde la UI una vez que los permisos
     * han sido concedidos para promover el servicio.
     */
    fun onPermissionsGranted() {
        Log.d("MyViewModel", "UI informa que los permisos fueron concedidos.")
        hasUiReportedPermissions = true // <-- 1. Marcamos que tenemos permisos

        // 2. Si el servicio ya está vinculado, lo promovemos AHORA.
        // Si no, 'onServiceConnected' se encargará.
        _service.value?.let { service ->
            Log.d("MyViewModel", "Permisos concedidos, y el servicio ya está vinculado. Promoviendo a FG...")
            service.startAsForeground()
        }
    }

    fun startScan() {
        if (isMiui() && !miuiWarningShownThisSession) {
            _showMiuiWarningDialog.value = true
            miuiWarningShownThisSession = true
            return
        }
        Log.d("MyViewModel", "Llamando a service.startScan()...")

        if (_service.value == null) {
            Log.e("MyViewModel", "¡startScan() falló! El servicio es nulo.")
            viewModelScope.launch { _scanErrorEvent.emit("Error: El servicio de Bluetooth no está listo. Intente de nuevo.") }
            return
        }

        _service.value?.startScan()
    }

    fun stopScan() {
        _service.value?.stopScan()
    }

    fun onDismissMiuiWarning() {
        _showMiuiWarningDialog.value = false
        Log.d("MyViewModel", "Diálogo MIUI cerrado, iniciando escaneo...")
        _service.value?.startScan()
    }

    fun connectToDevice(device: BluetoothDevice) {
        _service.value?.connect(device)
    }

    fun sendData(message: String) {
        _service.value?.send(message.toByteArray())
    }

    fun disconnectDevice() {
        // (Ya no detenemos el 'polling' aquí, porque el Dashboard
        // lo hará en su 'onDispose' antes de navegar)
        viewModelScope.launch {
            try {
                _service.value?.send(CommandProtocol.SET_LOGOUT.toByteArray())
                delay(500)
            } finally {
                _service.value?.disconnect()
            }
        }
    }

    override fun onCleared() {
        Log.d("MyViewModel", "ViewModel Limpiado. Desvinculando y deteniendo servicio.")
        _service.value?.stopService() // Detiene el servicio
        getApplication<Application>().unbindService(serviceConnection) // Desvincula
        super.onCleared()
    }

    // --- Lógica de Negocio y UI ---

    fun readDeviceTime() {
        _service.value?.send(CommandProtocol.GET_DEVICE_TIME.toByteArray())
    }

    fun setDeviceTime() {
        val currentEpochSeconds = System.currentTimeMillis() / 1000
        val commandString = CommandProtocol.setDeviceTime(currentEpochSeconds)
        _service.value?.send(commandString.toByteArray())
        _deviceTime.value = DeviceTime(currentEpochSeconds)
    }

    fun guardarHorario() {
        val horasFinal = _modoFuncionamientoState.value.horas.toIntOrNull() ?: 0
        val minutosFinal = _modoFuncionamientoState.value.minutos.toIntOrNull() ?: 0
        Log.d("ViewModel", "Guardando Horario: $horasFinal:$minutosFinal")
    }

    fun onRefreshWorkMode() {
        _service.value?.send(CommandProtocol.GET_WORK_MODE.toByteArray())
    }

    fun onStartEditingWorkMode() {
        if (_workModeData.value != null) _isEditingWorkMode.value = true
    }

    fun onCancelEditWorkMode() {
        _isEditingWorkMode.value = false
    }

    fun onSaveWorkMode(newMode: WorkMode) {
        viewModelScope.launch {
            val commandString = when (newMode) {
                is WorkMode.Periodic -> CommandProtocol.setWorkModePeriodic(newMode.intervalMinutes, newMode.powerOnTime)
                is WorkMode.Fixed -> CommandProtocol.setWorkModeFixed(newMode.times, newMode.powerOnTime)
                is WorkMode.Continuous -> CommandProtocol.setWorkModeContinuous(newMode.intervalMinutes, newMode.powerOnTime)
                is WorkMode.Disabled -> CommandProtocol.setWorkModeDisabled()
            }
            _service.value?.send(commandString.toByteArray())
            _workModeData.value = newMode
            _isEditingWorkMode.value = false
            delay(200)
            _service.value?.send(CommandProtocol.GET_WORK_MODE.toByteArray())
        }
    }

    fun onSetDigitalOutput(channel: Int, state: Boolean) {
        viewModelScope.launch {
            // Ya no necesitamos la actualización optimista,
            // el bucle de sondeo (polling) lo actualizará en ~1 seg.
            val commandString = CommandProtocol.setDigitalOutput(channel, state)
            _service.value?.send(commandString.toByteArray())
        }
    }

    private fun setBit(value: Int, bitIndex: Int): Int = value or (1 shl bitIndex)
    private fun clearBit(value: Int, bitIndex: Int): Int = value and (1 shl bitIndex).inv()

    fun onSaveChannelScaling(
        channelName: String, rawMin: Int, rawMax: Int, zero: Float, full: Float
    ) {
        viewModelScope.launch {
            val newChannelData = ChannelScaling(rawMin, rawMax, zero, full)
            if(_scalingData.value != null) {
                _scalingData.value = _scalingData.value!!.updateChannel(channelName, newChannelData)
            }
            val setCommand = CommandProtocol.setChannelScaling(channelName, rawMin, rawMax, zero, full)
            _service.value?.send(setCommand.toByteArray())
            delay(200)
            val getCommand = CommandProtocol.getChannelScaling(channelName)
            _service.value?.send(getCommand.toByteArray())
        }
    }

    fun readGpsData() {
        _service.value?.send(CommandProtocol.GET_GPS.toByteArray())
    }

    // --- Lógica de Reporte PDF (Sin cambios) ---

    fun generatePdfReport(
        identification: DeviceIdentification?,
        workMode: WorkMode?,
        sensorData: SensorData?,
        scaling: ScalingData?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (identification == null || workMode == null || scaling == null || sensorData == null) {
                _uiEvents.emit("Faltan datos para generar el reporte. Refresque todas las tarjetas.")
                return@launch
            }

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true }
            val headerPaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
            val textPaint = Paint().apply { textSize = 14f }
            var y = 60f

            canvas.drawText("Reporte de Configuración de Dispositivo", 40f, y, titlePaint); y += 30
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            canvas.drawText("Generado: ${sdf.format(Date())}", 40f, y, textPaint); y += 40

            y = drawIdentificationBlock(canvas, headerPaint, textPaint, y, identification)
            y = drawWorkModeBlock(canvas, headerPaint, textPaint, y, workMode)
            y = drawSensorBlock(canvas, headerPaint, textPaint, y, sensorData)
            drawScalingBlock(canvas, headerPaint, textPaint, y, scaling)

            document.finishPage(page)

            val fileName = "ConfigReport_${System.currentTimeMillis()}.pdf"
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentResolver = getApplication<Application>().contentResolver
                    val contentValues = contentValuesOf(
                        MediaStore.MediaColumns.DISPLAY_NAME to fileName,
                        MediaStore.MediaColumns.MIME_TYPE to "application/pdf",
                        MediaStore.MediaColumns.RELATIVE_PATH to Environment.DIRECTORY_DOWNLOADS
                    )
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    uri?.let {
                        contentResolver.openOutputStream(it).use { outputStream ->
                            document.writeTo(outputStream)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val pdfFile = java.io.File(file, fileName)
                    document.writeTo(FileOutputStream(pdfFile))
                }
                document.close()
                _uiEvents.emit("Reporte guardado en Descargas: $fileName")
            } catch (e: Exception) {
                document.close()
                _uiEvents.emit("Error al guardar el PDF: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun drawIdentificationBlock(canvas: android.graphics.Canvas, header: Paint, text: Paint, startY: Float, data: DeviceIdentification): Float {
        var y = startY
        canvas.drawText("1. Identificación", 40f, y, header); y += 25
        canvas.drawText("  Device ID: ${data.deviceID}", 50f, y, text); y += 20
        canvas.drawText("  Versión Firmware: ${data.firmwareVersion}", 50f, y, text); y += 20
        canvas.drawText("  Versión Hardware: ${data.hardwareVersion}", 50f, y, text); y += 20
        canvas.drawText("  Última Config: ${data.lastConfigurationDate}", 50f, y, text); y += 30
        return y
    }

    private fun drawWorkModeBlock(canvas: android.graphics.Canvas, header: Paint, text: Paint, startY: Float, data: WorkMode): Float {
        var y = startY
        canvas.drawText("2. Modo de Trabajo", 40f, y, header); y += 25
        when (data) {
            is WorkMode.Periodic -> {
                canvas.drawText("  Modo: Periódico", 50f, y, text); y += 20
                canvas.drawText("  Intervalo: ${data.intervalMinutes} minutos", 50f, y, text); y += 20
                canvas.drawText("  T. Encendido: ${data.powerOnTime} seg", 50f, y, text); y += 20
            }
            is WorkMode.Fixed -> {
                canvas.drawText("  Modo: Horas Fijas", 50f, y, text); y += 20
                data.times.forEachIndexed { i, t ->
                    if(t.isNotBlank()) {
                        canvas.drawText("    Hora ${i + 1}: $t", 60f, y, text); y += 20
                    }
                }
                canvas.drawText("  T. Encendido: ${data.powerOnTime} seg", 50f, y, text); y += 20
            }
            is WorkMode.Continuous -> {
                canvas.drawText("  Modo: Continuo", 50f, y, text); y += 20
                canvas.drawText("  Intervalo: ${data.intervalMinutes} minutos", 50f, y, text); y += 20
                canvas.drawText("  T. Encendido: ${data.powerOnTime} seg", 50f, y, text); y += 20
            }
            is WorkMode.Disabled -> {
                canvas.drawText("  Modo: Deshabilitado", 50f, y, text); y += 20
            }
        }
        y += 10
        return y
    }

    private fun drawSensorBlock(canvas: android.graphics.Canvas, header: Paint, text: Paint, startY: Float, data: SensorData): Float {
        var y = startY
        canvas.drawText("3. Valores de Sensores", 40f, y, header); y += 25
        canvas.drawText("  Entradas Digitales: ${data.digitalInputs}", 50f, y, text); y += 20
        canvas.drawText("  Salidas Digitales: ${data.digitalOutputs}", 50f, y, text); y += 20
        canvas.drawText("  Voltaje Batería: ${data.batteryVoltage} V", 50f, y, text); y += 20
        y += 10
        canvas.drawText("  Entradas Analógicas:", 50f, y, text); y += 20
        data.analogInputs.forEachIndexed { i, v ->
            canvas.drawText("    Canal ${i + 1}: $v", 60f, y, text); y += 20
        }
        y += 10
        canvas.drawText("  Salidas Analógicas:", 50f, y, text); y += 20
        data.analogOutputs.forEachIndexed { i, v ->
            canvas.drawText("    Canal ${i + 1}: $v", 60f, y, text); y += 20
        }
        y += 10
        return y
    }

    private fun drawScalingBlock(canvas: android.graphics.Canvas, header: Paint, text: Paint, startY: Float, data: ScalingData): Float {
        var y = startY
        canvas.drawText("4. Escalado de Canales", 40f, y, header); y += 25
        val smallText = Paint(text).apply { textSize = 12f }
        y = drawChannelLine(canvas, smallText, y, "Canal", "Raw Min", "Raw Max", "Cero", "Full")
        y = drawChannelLine(canvas, smallText, y, "AI-0", data.ai0)
        y = drawChannelLine(canvas, smallText, y, "AI-1", data.ai1)
        y = drawChannelLine(canvas, smallText, y, "AI-2", data.ai2)
        y = drawChannelLine(canvas, smallText, y, "AI-3", data.ai3)
        y = drawChannelLine(canvas, smallText, y, "AO-0", data.ao0)
        y = drawChannelLine(canvas, smallText, y, "AO-1", data.ao1)
        return y
    }

    private fun drawChannelLine(canvas: android.graphics.Canvas, p: Paint, y: Float, name: String, ch: ChannelScaling): Float {
        return drawChannelLine(canvas, p, y, name, ch.rawMin.toString(), ch.rawMax.toString(), ch.zero.toString(), ch.full.toString())
    }

    private fun drawChannelLine(canvas: android.graphics.Canvas, p: Paint, y: Float, c1: String, c2: String, c3: String, c4: String, c5: String): Float {
        canvas.drawText(c1, 50f, y, p)
        canvas.drawText(c2, 120f, y, p)
        canvas.drawText(c3, 190f, y, p)
        canvas.drawText(c4, 260f, y, p)
        canvas.drawText(c5, 330f, y, p)
        return y + 20
    }
}
