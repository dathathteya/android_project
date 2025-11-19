package com.example.smartplant

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.*

/**
 * Minimal BLE repository that scans for devices, connects, and listens for notifications on a
 * characteristic that contains JSON text representing moisture readings.
 *
 * This is intentionally small/simple for an 8th grade project. For production, add better
 * lifecycle handling, error propagation, DI, and thorough permission checks.
 */
class BluetoothRepository(private val context: Context) {
    private fun hasBlePermissions(): Boolean {
        val perms = listOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        return perms.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        // Replace these UUIDs with the actual UUIDs used by your hardware if different.
        val SERVICE_UUID: UUID = UUID.fromString("0000feed-0000-1000-8000-00805f9b34fb")
        val MOISTURE_CHAR_UUID: UUID = UUID.fromString("0000beef-0000-1000-8000-00805f9b34fb")
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var gatt: BluetoothGatt? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _latest = MutableStateFlow(MoistureReading(0, "", "unknown"))
    val latest = _latest.asStateFlow()

    // runtime logs exposed to UI for debugging (keeps latest entries)
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                // Auto-connect to first device that advertises our service (best-effort)
                val record = result.scanRecord
                val uuids = record?.serviceUuids
                if (uuids != null) {
                    uuids.forEach { parcelUuid ->
                        if (parcelUuid.uuid == SERVICE_UUID) {
                            appendLog("Discovered matching device ${device.address} (${device.name}) advertising service")
                            stopScan()
                            connect(device)
                        }
                    }
                }
            }
        }
    }

    // Non-filtering scan callback: connects to the first discovered device (useful for testing
    // or when you want to connect to arbitrary devices such as an iPhone peripheral).
    @SuppressLint("MissingPermission")
    private val scanCallbackAny = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                // best-effort: auto-connect to first discovered device
                appendLog("Discovered device (any) ${device.address} (${device.name})")
                try {
                    // stop the any-callback specifically to avoid leaving the scanner running
                    adapter?.bluetoothLeScanner?.stopScan(this)
                } catch (_: Exception) {
                }
                connect(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBlePermissions()) {
            appendLog("Missing BLE permissions, cannot start scan")
            return
        }
        adapter?.bluetoothLeScanner?.startScan(scanCallback)
    }

    /**
     * Start scanning without filtering by service UUID. Useful for testing or connecting to
     * arbitrary peripherals (for example an iPhone running a peripheral app).
     */
    @SuppressLint("MissingPermission")
    fun startScanAny() {
        if (!hasBlePermissions()) {
            appendLog("Missing BLE permissions, cannot start scanAny")
            return
        }
        adapter?.bluetoothLeScanner?.startScan(scanCallbackAny)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!hasBlePermissions()) {
            appendLog("Missing BLE permissions, cannot stop scan")
            return
        }
        try {
            // stop both callbacks if running to be safe
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            adapter?.bluetoothLeScanner?.stopScan(scanCallbackAny)
            appendLog("Stopped scan")
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!hasBlePermissions()) {
            appendLog("Missing BLE permissions, cannot connect")
            return
        }
        disconnect()
        try {
            appendLog("Connecting to ${device.address} (${device.name})")
            gatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            // best-effort logging for connection failures
            appendLog("connectGatt failed: ${e.message}")
            gatt = null
        }
    }

    /** Clear in-app logs */
    fun clearLogs() {
        mainHandler.post { _logs.value = emptyList() }
    }

    /**
     * Convenience: connect to a device by its Bluetooth MAC/address string.
     */
    @SuppressLint("MissingPermission")
    fun connectByAddress(address: String) {
        if (!hasBlePermissions()) {
            appendLog("Missing BLE permissions, cannot connectByAddress")
            return
        }
        try {
            val d = adapter?.getRemoteDevice(address)
            d?.let { connect(it) }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!hasBlePermissions()) {
            appendLog("Missing BLE permissions, cannot disconnect")
            return
        }
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(g, status, newState)
            appendLog("onConnectionStateChange status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                appendLog("Connected to GATT, discovering services")
                g?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                appendLog("Disconnected from GATT (status=$status)")
                // optionally emit a disconnected state so UI can update
                mainHandler.post {
                    _latest.value = MoistureReading(0, "", "disconnected")
                }
            }
        }
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(g, status)
            appendLog("onServicesDiscovered status=$status")
            val svc = g?.getService(SERVICE_UUID)
            val char = svc?.getCharacteristic(MOISTURE_CHAR_UUID)
            if (char != null) {
                g.setCharacteristicNotification(char, true)
                // enable notifications on the descriptor if required (best-effort)
                val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val ok = g.writeDescriptor(it)
                    appendLog("writeDescriptor requested: $ok")
                }
            }
        }
        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(g: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(g, characteristic)
            characteristic?.value?.let { bytes ->
                // Device is expected to send JSON as UTF-8 text
                val text = try {
                    String(bytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    null
                }
                text?.let {
                    appendLog("Received characteristic: $it")
                    parseAndEmit(it)
                }
            }
        }
    }

    private fun parseAndEmit(jsonText: String) {
        try {
            val obj = JSONObject(jsonText)
            val percentage = obj.optInt("moisture_percentage", -1)
            val timestamp = obj.optString("timestamp", "")
            val status = obj.optString("sensor_status", "")
            appendLog("Parsed JSON -> percent=$percentage ts=$timestamp status=$status")
            if (percentage in 0..100) {
                // ensure updates happen on main thread
                mainHandler.post {
                    _latest.value = MoistureReading(percentage, timestamp, status)
                }
            }
        } catch (e: Exception) {
            appendLog("JSON parse error: ${e.message}")
        }
    }

    private fun appendLog(msg: String) {
        // write to Android log as well for adb debugging
        try {
            android.util.Log.d("BluetoothRepo", msg)
        } catch (_: Exception) {
        }
        val entry = "${java.time.Instant.now()} - $msg"
        mainHandler.post {
            val updated = (_logs.value + entry).takeLast(200)
            _logs.value = updated
        }
    }

}
