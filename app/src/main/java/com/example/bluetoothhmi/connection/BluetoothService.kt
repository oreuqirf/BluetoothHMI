package com.example.bluetoothhmi.connection

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
// ¡Importaciones de BLE eliminadas!
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager // ¡Este es importante!
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothService : Service() {

    // --- Lógica del Servicio y Binder ---
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private lateinit var locationManager: LocationManager // Para la comprobación de GPS

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onBind(intent: Intent): IBinder = binder

    // --- Lógica de Bluetooth (Clásico) ---
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    // (Escáner BLE eliminado)

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // (Filtros y Ajustes BLE eliminados)

    // Sockets, streams, etc.
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var communicationJob: Job? = null
    private var targetDevice: BluetoothDevice? = null

    // --- Flujos (Públicos) ---
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()
    private val _receivedData = MutableSharedFlow<ByteArray>()
    val receivedData: SharedFlow<ByteArray> = _receivedData.asSharedFlow()
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private val _scanError = MutableSharedFlow<String>() // Para errores de escaneo
    val scanError = _scanError.asSharedFlow()


    // --- Ciclo de Vida del Servicio ---

    override fun onCreate() {
        super.onCreate()
        Log.d("BluetoothService", "Servicio Creado.")

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationHelper.createNotificationChannel(this)

        // --- ¡CAMBIO! ---
        // Registramos AMBOS receivers
        val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(bondStateReceiver, bondFilter)

        val discoveryFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        discoveryFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(discoveryReceiver, discoveryFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BluetoothService", "Servicio Iniciado (onStartCommand).")
        return START_STICKY
    }

    fun startAsForeground() {
        Log.d("BluetoothService", "Promoviendo servicio a Primer Plano...")
        try {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                NotificationHelper.createNotification(this, "Servicio iniciado. Esperando...")
            )
        } catch (e: Exception) {
            Log.e("BT_SERVICE", "Error al iniciar en primer plano: ${e.message}")
        }
    }

    fun stopService() {
        Log.d("BluetoothService", "Deteniendo servicio...")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d("BluetoothService", "Servicio Destruido.")
        serviceScope.cancel()
        // --- ¡CAMBIO! ---
        // Desregistramos AMBOS
        unregisterReceiver(bondStateReceiver)
        unregisterReceiver(discoveryReceiver)
        stopScan() // stopScan ahora usa Clásico
        disconnect()
        super.onDestroy()
    }

    // --- Permisos (Sin cambios, ya está correcto con Ubicación) ---
    companion object {
        fun getRequiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            ActivityCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // --- Callbacks de Escaneo y Vinculación ---

    // --- ¡CAMBIO! ---
    // Volvemos a usar 'discoveryReceiver' para el escaneo Clásico
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        // Filtramos por nombre y duplicados
                        if (it.name != null && !_discoveredDevices.value.any { d -> d.address == it.address }) {
                            Log.d("BT_SERVICE", "Dispositivo Clásico encontrado: ${it.name} [${it.address}]")
                            _discoveredDevices.value += it
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d("BT_SERVICE", "Escaneo Clásico finalizado.")
                    _isScanning.value = false
                    updateNotification("Escaneo finalizado.")
                }
            }
        }
    }

    // (bondStateReceiver sin cambios)
    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val state =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                if (device?.address != targetDevice?.address) return

                when (state) {
                    BluetoothDevice.BOND_BONDED -> {
                        Log.d("BT_SERVICE", "Vinculación exitosa con ${device?.name}. Ahora conectando...")
                        device?.let { launchConnection(it) }
                    }
                    BluetoothDevice.BOND_NONE -> {
                        Log.w("BT_SERVICE", "Vinculación fallida o rechazada por el usuario.")
                        _connectionState.value = ConnectionState.Error("Vinculación fallida")
                        targetDevice = null
                    }
                    BluetoothDevice.BOND_BONDING -> {
                        Log.d("BT_SERVICE", "Vinculando...")
                    }
                }
            }
        }
    }

    // --- API Pública del Servicio ---

    fun getBondedDevicesCount(): Int {
        return bluetoothAdapter?.bondedDevices?.size ?: 0
    }

    // --- ¡CAMBIO! ---
    // 'startScan' vuelve a usar 'startDiscovery()' (Clásico)
    fun startScan() {
        if (!hasRequiredPermissions() || bluetoothAdapter?.isEnabled != true) {
            Log.w("BT_SERVICE", "No se puede escanear: Faltan permisos o BT apagado.")
            return
        }
        if (_isScanning.value) return

        // Comprobación de GPS (sigue siendo necesaria para Clásico en < S)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (!isGpsEnabled) {
                Log.e("BT_SERVICE", "¡FALLO DE ESCANEO! El SERVICIO DE UBICACIÓN (GPS) del teléfono está apagado.")
                serviceScope.launch {
                    _scanError.emit("Error: El servicio de Ubicación (GPS) del teléfono debe estar encendido.")
                }
                return
            }
        }

        Log.d("BT_SERVICE", "Iniciando escaneo Clásico (startDiscovery)...")
        _discoveredDevices.value = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

        // ¡Esta es la API Clásica!
        bluetoothAdapter?.startDiscovery()

        _isScanning.value = true
        updateNotification("Escaneando dispositivos...")
    }

    // --- ¡CAMBIO! ---
    // 'stopScan' vuelve a usar 'cancelDiscovery()' (Clásico)
    fun stopScan() {
        if (!hasRequiredPermissions() || !(_isScanning.value)) return

        Log.d("BT_SERVICE", "Escaneo Clásico detenido.")
        bluetoothAdapter?.cancelDiscovery()
        _isScanning.value = false
        updateNotification("Conexión inactiva.")
    }

    fun connect(device: BluetoothDevice) {
        if (!hasRequiredPermissions() || bluetoothAdapter?.isEnabled != true) {
            _connectionState.value = ConnectionState.Error("Permisos o Bluetooth desactivado")
            return
        }
        stopScan() // Detiene el escaneo Clásico
        _connectionState.value = ConnectionState.Connecting
        updateNotification("Conectando a ${device.name ?: device.address}...")
        targetDevice = device

        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                Log.d("BT_SERVICE", "Dispositivo ya vinculado. Conectando...")
                launchConnection(device)
            }
            BluetoothDevice.BOND_NONE -> {
                Log.d("BT_SERVICE", "Dispositivo no vinculado. Iniciando 'createBond()'...")
                device.createBond()
            }
            BluetoothDevice.BOND_BONDING -> Log.d("BT_SERVICE", "Vinculación ya en progreso...")
        }
    }

    fun disconnect() {
        Log.d("BT_SERVICE", "Desconectando...")
        communicationJob?.cancel()
        communicationJob = null
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) { /* Ignorar */ }

        inputStream = null
        outputStream = null
        bluetoothSocket = null
        if (_connectionState.value == ConnectionState.Connected || _connectionState.value == ConnectionState.Connecting) {
            _connectionState.value = ConnectionState.Idle
        }
        targetDevice = null
        updateNotification("Conexión finalizada.")
    }

    fun send(data: ByteArray) {
        if (_connectionState.value != ConnectionState.Connected || outputStream == null) {
            Log.w("BT_SERVICE", "No se pueden enviar datos: no conectado.")
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(data)
            } catch (e: IOException) {
                _connectionState.value = ConnectionState.Error("Error al enviar datos")
                disconnect()
            }
        }
    }

    // --- Lógica Interna ---

    private fun launchConnection(device: BluetoothDevice) {
        disconnect() // Cierra conexión anterior
        serviceScope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                bluetoothSocket?.connect()

                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream

                _connectionState.value = ConnectionState.Connected
                updateNotification("Conectado a ${device.name ?: device.address}")
                startDataListener()

            } catch (e: IOException) {
                e.printStackTrace()
                disconnect()
                _connectionState.value = ConnectionState.Error("Error al conectar: ${e.message}")
                updateNotification("Error de conexión.")
            }
        }
    }

    private fun startDataListener() {
        communicationJob = serviceScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (isActive) {
                try {
                    bytes = inputStream?.read(buffer) ?: break
                    if (bytes > 0) {
                        val received = buffer.copyOf(bytes)
                        Log.d("BT_SERVICE", "Datos Recibidos: ${String(received)}")
                        _receivedData.emit(received)
                    }
                } catch (e: IOException) {
                    if (isActive) {
                        Log.w("BT_SERVICE", "Conexión perdida: ${e.message}")
                        _connectionState.value = ConnectionState.Error("Conexión perdida")
                        disconnect()
                    }
                    break
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        try {
            notificationManager.notify(
                NotificationHelper.NOTIFICATION_ID,
                NotificationHelper.createNotification(this, text)
            )
        } catch (e: Exception) {
            Log.e("BT_SERVICE", "Error al actualizar la notificación: ${e.message}")
        }
    }
}
