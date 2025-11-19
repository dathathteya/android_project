package com.example.smartplant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var repo: BluetoothRepository? = null
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repo = BluetoothRepository(this)

        // Permission launcher: if granted, start scan; otherwise, show default UI
        requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val granted = results.values.all { it }
            if (granted) {
                try {
                    repo?.startScan()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        setContent {
            MaterialTheme {
                var showScanner by remember { mutableStateOf(false) }
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Smart Plant") }, actions = {
                        TextButton(onClick = { showScanner = !showScanner }) {
                            Text(if (!showScanner) "Devices" else "Home")
                        }
                    })
                }) { innerPadding ->
                    Surface(modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)) {
                        if (showScanner) {
                            ScanScreen(repo!!, onClose = { showScanner = false })
                        } else {
                            MainScreen(repo!!)
                        }
                    }
                }
            }
        }

        // Request permissions and start scan only when granted. This prevents crashes
        // from calling BLE APIs without the required runtime permissions.
        if (hasPermissions()) {
            try {
                repo?.startScan()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun hasPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        repo?.disconnect()
    }
}

@Composable
fun MainScreen(repo: BluetoothRepository) {
    val scope = rememberCoroutineScope()
    var percentage by remember { mutableStateOf(0) }
    var ts by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }

    LaunchedEffect(repo) {
        repo.latest.collectLatest { reading ->
            percentage = reading.percentage
            ts = reading.timestamp
            status = reading.sensorStatus
            connected = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "Smart Plant Watering", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))

        // Simple circular indicator and percent
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            CircularProgressIndicator(
                progress = percentage / 100f,
                strokeWidth = 12.dp,
                modifier = Modifier.fillMaxSize()
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "$percentage%", fontSize = 36.sp)
                Text(text = categorizeMoisture(percentage).name)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Sensor: ${if (connected) "Connected" else "Disconnected"}")
        Text(text = "Status: $status")
        Text(text = "Last: $ts")

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { repo.startScanAny() }) { Text("Scan") }
            Button(onClick = { repo.disconnect() }) { Text("Disconnect") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Notes: This app listens for JSON notifications from a BLE characteristic.\nExample JSON:\n{\n  \"moisture_percentage\":45,\n  \"timestamp\":\"2025-11-17T14:30:00Z\",\n  \"sensor_status\":\"active\"\n}")
    }
}
