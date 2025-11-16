Bluetooth HMI Android App
	
	A robust Human-Machine Interface (HMI) application for Android, built with 100% Kotlin and Jetpack Compose. This app is designed to monitor and control custom hardware (such as PLCs, Arduinos, or industrial sensors) in real-time using Bluetooth Classic (SPP).
	The primary challenge of this project was overcoming the aggressive battery optimizations and permission restrictions imposed by modern Android versions (especially on OEM skins like Xiaomi's MIUI and Samsung's One UI), which make reliable Bluetooth Classic scanning (startDiscovery()) nearly impossible for standard applications.
	This app solves these problems by implementing a robust ForegroundService architecture.

Features

	Reliable Bluetooth Classic Scanning: Uses a ForegroundService to run a continuous startDiscovery() scan-loop, bypassing Android's 12-second scan limit and OS battery optimizations.

Real-time Sensor Dashboard: A live-updating dashboard featuring:

	A line chart that plots historical sensor data (e.g., "AI-0") with a 60-sample history.
	Channel selection (AI-0 to AI-3) with a seamless TabRow.
	Live timestamp and sample counter.
	Interactive controls for Digital Outputs (DOs) with Switch.
	Live readouts for Digital Inputs (DIs) and Analog Inputs (AIs).
	On-Demand Data Cards: Battery voltage and GPS data cards that update only when tapped by the user (GET_GPS).
	Full Device Configuration: A separate "Settings" screen (DeviceDataScreen) to configure all device parameters (Work Mode, GPS, Time Sync, I/O Scaling).
	Robust Navigation: A clean navigation flow (Splash -> Connection -> Dashboard <-> Configuration) using NavHost that automatically routes the user based on connection state.
	PDF Report Generation: Exports the device's complete configuration to a shareable PDF file.


Smart Permission Handling:

	Politely requests all necessary permissions (BLUETOOTH_SCAN, CONNECT, POST_NOTIFICATIONS, ACCESS_FINE_LOCATION).
	Includes a special helper dialogue to guide Xiaomi (MIUI) users to enable "Autostart" permissions.

Screenshots

<img width="691" height="820" alt="image" src="https://github.com/user-attachments/assets/5adab3e7-d0ee-4d6b-9025-74a7c15ec147" />
<img width="808" height="541" alt="image" src="https://github.com/user-attachments/assets/d5f0bb59-7ff8-46e0-8b30-c918f00d033f" />
<img width="369" height="777" alt="image" src="https://github.com/user-attachments/assets/215e5ecb-3c77-4a83-a701-4bb9063c8cbf" />
<img width="382" height="780" alt="image" src="https://github.com/user-attachments/assets/6a3b0c63-c62f-4589-b31b-c468db7d516f" />
<img width="390" height="787" alt="image" src="https://github.com/user-attachments/assets/6213e4ab-1ff9-4f17-8c5b-d41cbbcc96bc" />




The ForegroundService finds devices that standard apps miss.

	Live-updating chart and I/O control.
	Full device parameter configuration.

Technical Deep Dive: "How it Works"

	The core of this app is its architecture, designed specifically to solve the "My Bluetooth Scan Doesn't Find Devices" problem that plagues modern Android.

The Problem

	startDiscovery() (Classic Scan) is Unreliable: Android automatically stops this high-power scan after ~12 seconds.
	OS Battery Optimizations (MIUI, etc.): Modern Android systems will silently kill any app performing background work (like a Bluetooth scan) if it's not on a "whitelist".
	UI Freezing (StateFlow Issue): A simple delay(1000) polling loop can cause data congestion. Furthermore, if StateFlow receives two identical SensorData objects, it won't emit an update, freezing the UI clock.

The Solution: A Service-Based Architecture

BluetoothService (The "Engine"):

	A ForegroundService is used to host all Bluetooth logic. This tells the OS, "I am doing critical work, do not kill me." This requires the POST_NOTIFICATIONS permission to show a persistent notification.
	Continuous Scan Loop: The service's discoveryReceiver listens for the ACTION_DISCOVERY_FINISHED (when Android kills the scan). When received, it immediately calls startDiscovery() again, creating a perpetual scan loop.
	Connection Logic: The service manages the BluetoothSocket (SPP) and all I/O streams in a dedicated CoroutineScope.

MyBluetoothViewModel (The "Bridge"):

	The ViewModel binds to the BluetoothService on creation. It does not own the connection logic; it only observes it.

	Fixing the "Race Condition": The ConnectionScreen cannot scan until the ViewModel is successfully bound to the Service. A isServiceBound StateFlow is used to disable the "Scan" button until the onServiceConnected callback is complete.

	Data Parsing: The ViewModel collects the raw ByteArray from the service's receivedData flow and passes it to the static DataParser object.

DashboardScreen (The "Consumer"):

	Reliable Polling: A simple LaunchedEffect loop calls viewModel.sendData(GET_SENSORS) every 1 second. This is more reliable than a complex request/response model for a single-channel HMI.

	Fixing the "Frozen Graph": The SensorData data class was modified to include a unique ID (val id: Long = System.currentTimeMillis()). When the DataParser creates a new SensorData object, it gets a new timestamp. This forces the _sensorData StateFlow in the ViewModel to emit an update every time, even if the sensor values themselves haven't changed, keeping the graph's timestamp and counter alive.

How to Build

	Clone the repository.
	Open the project in a recent version of Android Studio (e.g., Iguana or later).
	Let Gradle sync the dependencies (including accompanist-permissions and material-icons-extended).
	Build and run the app.

Note: For the app to function, you must grant all requested permissions (Bluetooth, Location, Notifications) and must have your phone's Location/GPS service turned ON.
