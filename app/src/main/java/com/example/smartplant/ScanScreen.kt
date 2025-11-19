package com.example.smartplant

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@SuppressLint("MissingPermission")
@Composable
fun ScanScreen(repo: BluetoothRepository, onClose: () -> Unit) {
    val adapter = BluetoothAdapter.getDefaultAdapter()
    val scanner = adapter?.bluetoothLeScanner

    val devices = remember { mutableStateListOf<ScanResult>() }
    var scanning by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    // collect logs from repository into a local list for display
    LaunchedEffect(repo) {
        repo.logs.collectLatest { list ->
            logs.clear()
            logs.addAll(list)
        }
    }

    val mainHandler = Handler(Looper.getMainLooper())

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { r ->
                    // deduplicate by address
                    if (devices.none { it.device.address == r.device.address }) {
                        mainHandler.post { devices.add(r) }
                    }
                }
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Scan Devices") }, actions = {
            TextButton(onClick = { onClose() }) { Text("Close") }
        })
    }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (scanning) "Scanning..." else "Idle", fontSize = 16.sp)
                Button(onClick = {
                    if (!scanning) {
                        devices.clear()
                        try { scanner?.startScan(scanCallback) } catch (_: Exception) {}
                        scanning = true
                    } else {
                        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
                        scanning = false
                    }
                }) {
                    Text(if (!scanning) "Start" else "Stop")
                }
            }

            Divider()

            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(devices) { result ->
                    val name = result.device.name ?: result.scanRecord?.deviceName ?: "Unknown"
                    val addr = result.device.address
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                        // stop scan and connect via repo
                        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
                        repo.connect(result.device)
                    }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = name, style = MaterialTheme.typography.h6)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = addr, fontSize = 12.sp)
                        }
                    }
                }
            }

            Divider()

            // Log panel
            Column(modifier = Modifier.fillMaxWidth().height(160.dp).padding(8.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Logs", style = MaterialTheme.typography.h6)
                    Row {
                        TextButton(onClick = { repo.clearLogs() }) { Text("Clear") }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { /* no-op: keep for future */ }) { Text("Copy") }
                    }
                }

                Divider()

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs) { line ->
                        Text(text = line, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
