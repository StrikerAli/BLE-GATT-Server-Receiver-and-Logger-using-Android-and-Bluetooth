package com.example.blereceiver

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.OutputStreamWriter
import java.net.URL
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var filenameEditText: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var logTextView: TextView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanning = false
    private var loggingEnabled = false
    private var outputFileUri: android.net.Uri? = null

    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val CHARACTERISTIC_UUID = UUID.fromString("abcdef01-1234-5678-1234-56789abcdef0")

    @SuppressLint("MissingPermission")
    private val createFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                outputFileUri = uri
                log("Log file created: $uri")
                loggingEnabled = true
                startScan()
            } else {
                toast("File creation cancelled.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        filenameEditText = findViewById(R.id.filenameEditText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        logTextView = findViewById(R.id.logTextView)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ensureLocationEnabled()
        }

        startButton.setOnClickListener {
            val filename = filenameEditText.text.toString().trim()
            if (filename.isEmpty()) {
                toast("Please enter a filename")
                return@setOnClickListener
            }
            createFileLauncher.launch(filename)
        }

        stopButton.setOnClickListener {
            loggingEnabled = false
            log("Logging stopped.")

            if (outputFileUri != null) {
                sendFileToServer(outputFileUri!!)
            } else {
                toast("No log file to send.")
            }
        }

        requestPermissions()
    }
    private fun ensureLocationEnabled() {
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val gpsEnabled =
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val networkEnabled =
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            toast("Please enable Location Services for BLE to work on Android 10 and below.")
            startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan() {
        if (scanning) return
        log("Scanning Started")
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().build()
        scanner?.startScan(listOf(filter), settings, scanCallback)
        scanning = true

        Handler(Looper.getMainLooper()).postDelayed({
            if (scanning) {
                stopScan()
                toast("BLE device not found.")
            }
        }, 10000)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScan() {
        scanner?.stopScan(scanCallback)
        scanning = false
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) return
            log("Found device: ${result.device.name ?: "Unnamed"}")
            connectToDevice(result.device)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                log("All permissions granted.")
            } else {
                toast("Please grant all permissions for BLE to work")
                log("Missing one or more permissions.")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice(device: BluetoothDevice) {
        log("Connecting to device: ${device.address}")
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Connected to GATT server")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Disconnected from GATT server")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val characteristic =
                gatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
            if (characteristic != null) {
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor =
                    characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                descriptor?.let { gatt.writeDescriptor(it) }
                log("Notifications enabled")
            } else {
                log("Sine wave characteristic not found")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                val bytes = characteristic.value
                if (bytes.size == 4) {
                    val sineValue = ByteBuffer.wrap(bytes).float
                    val timestamp =
                        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                    val line = "$timestamp, $sineValue"
                    runOnUiThread {
                        log(line, isDataLine = true)
                        if (loggingEnabled && outputFileUri != null) {
                            try {
                                contentResolver.openOutputStream(outputFileUri!!, "wa")
                                    ?.bufferedWriter()?.use {
                                    it.appendLine(line)
                                }
                            } catch (e: Exception) {
                                log("Write failed: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun log(msg: String, isDataLine: Boolean = false) {
        Log.d("BLE", msg)
        runOnUiThread {
            if (!isDataLine || loggingEnabled) {
                logTextView.append("$msg\n")
                (logTextView.parent as? ScrollView)?.post {
                    (logTextView.parent as ScrollView).fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        bluetoothGatt?.close()
        scanner?.stopScan(scanCallback)
    }

    private fun sendFileToServer(uri: android.net.Uri) {
        Thread {
            try {
                val boundary = "Boundary-${UUID.randomUUID()}"
                val serverIp = getString(R.string.server_ip)
                val url = URL("http://$serverIp:5000/upload")

                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.doOutput = true
                connection.requestMethod = "POST"
                connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=$boundary"
                )

                val outputStream = connection.outputStream
                val writer = outputStream.bufferedWriter()

                // Multipart header
                writer.write("--$boundary\r\n")
                writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"log.txt\"\r\n")
                writer.write("Content-Type: text/plain\r\n\r\n")
                writer.flush()

                // File content
                contentResolver.openInputStream(uri)?.use { input ->
                    input.copyTo(outputStream)
                }
                outputStream.flush()

                // Multipart footer
                writer.write("\r\n--$boundary--\r\n")
                writer.flush()

                writer.close()
                outputStream.close()

                val responseCode = connection.responseCode
                val message = "File sent to server. Response code: $responseCode"
                runOnUiThread {
                    toast(message)
                    log(message)
                }
            } catch (e: Exception) {
                val errorMsg = "Failed to send file: ${e.message}"
                runOnUiThread {
                    toast(errorMsg)
                    log(errorMsg)
                }
            }
        }.start()
    }

}
