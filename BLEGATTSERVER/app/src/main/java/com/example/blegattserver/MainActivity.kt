package com.example.blegattserver

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var sineCharacteristic: BluetoothGattCharacteristic? = null

    private lateinit var logTextView: TextView

    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val CHARACTERISTIC_UUID = UUID.fromString("abcdef01-1234-5678-1234-56789abcdef0")

    private var timer: Timer? = null
    private var sampleIndex = 0
    private val sampleRate = 100 // Hz
    private val sineFrequency = 2.0 // Hz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logTextView = findViewById(R.id.logTextView)
        log("BLE", "onCreate started")

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        log("BLE", "BluetoothAdapter status: ${bluetoothAdapter.isEnabled}")

        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            log("BLE", "BLE advertising not supported on this device")
            return
        }

        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        log("BLE", "BluetoothLeAdvertiser initialized")

        // Only ACCESS_FINE_LOCATION is needed on Android 11
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    ) != PackageManager.PERMISSION_GRANTED)
        ) {
            log("BLE", "Requesting Bluetooth permissions for Android 12+")
            requestPermissions()
            return
        }


        startGattServer()
        startAdvertising()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startGattServer() {
        log("BLE", "Starting GATT server")
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)

        sineCharacteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(sineCharacteristic)
        gattServer?.addService(service)
        log("BLE", "GATT service added")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun startAdvertising() {
        log("BLE", "Starting BLE advertising")
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            101
        )
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("BLE", "Device connected: ${device?.address}")
                startSendingSineData(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("BLE", "Device disconnected")
                timer?.cancel()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSendingSineData(device: BluetoothDevice?) {
        log("BLE", "Starting sine wave data transmission")
        timer?.cancel()
        timer = fixedRateTimer("sine_timer", false, 0L, (1000L / sampleRate)) {
            val time = sampleIndex.toDouble() / sampleRate
            val sineValue = sin(2.0 * Math.PI * sineFrequency * time)
            val sineBytes = sineValue.toFloat().toRawBits().let {
                ByteBuffer.allocate(4).putInt(it).array()
            }
            sineCharacteristic?.value = sineBytes
            device?.let {
                gattServer?.notifyCharacteristicChanged(it, sineCharacteristic, false)
                log("BLE", "Sent sample: $sineValue")
            }
            sampleIndex++
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            log("BLE", "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            log("BLE", "Advertising failed with code: $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun onDestroy() {
        super.onDestroy()
        log("BLE", "onDestroy called - cleaning up")
        timer?.cancel()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        gattServer?.close()
        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        log("BLE", "Stopped advertising")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {

            log("BLE", "Permission granted")
             if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            startGattServer()
            startAdvertising()
        } else {
            log("BLE", "Permission denied by user")
        }
    }


    /** Logs to Logcat + app screen */
    private fun log(tag: String, message: String) {
        Log.d(tag, message)
        runOnUiThread {
            logTextView.append("[$tag] $message\n")
            val scrollView = logTextView.parent as? ScrollView
            scrollView?.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
}
