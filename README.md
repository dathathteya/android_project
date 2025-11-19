# Smart Plant Watering — Android App (Kotlin + Jetpack Compose)

This is a simple Android app skeleton for an 8th-grade project that demonstrates reading a JSON payload from a Bluetooth LE device and displaying soil moisture updates in the UI.

What this delivers
- A minimal Compose-based UI showing moisture percentage, status and timestamp.
- A small `BluetoothRepository` that scans for BLE devices, connects, subscribes to a characteristic, parses JSON notifications and exposes the latest reading as a StateFlow.
- Gradle project you can open in Android Studio and build an APK.

Assumptions and notes
- The BLE peripheral must advertise a custom service UUID and send UTF-8 JSON on a characteristic notification. Update the UUIDs in `BluetoothRepository.kt` if your hardware uses different ones.
- For simplicity this starter app does not implement full runtime permission flows for Android 12+. You should grant BLUETOOTH and location permissions from the device settings or add a permission-request UI if needed.
- This skeleton focuses on the core requirement (read JSON from BLE and refresh the UI every time data arrives). It intentionally keeps code simple and readable for an educational project.

How to build the APK
1. Open this folder in Android Studio (recommended). Let it sync and install required SDK components.
2. Connect a physical Android device (BLE required) or use an emulator with BLE support (real device recommended).
3. Build → Build Bundle(s) / APK(s) → Build APK(s) or Run the app.

Or from the terminal (macOS zsh):

```bash
# from project root (this folder)
./gradlew :app:assembleDebug
```

APK path: `app/build/outputs/apk/debug/app-debug.apk`

Simple documentation (for 8th grade)
1. Goal
   - The app connects to the smart plant watering hardware and shows soil moisture percentage in real-time.

2. How it works (high level)
   - The phone scans for BLE devices advertising the specified service UUID.
   - When it finds a device, it connects and subscribes to a characteristic that sends JSON notifications every few seconds.
   - The app parses JSON messages like:

```json
{
  "moisture_percentage": 45,
  "timestamp": "2025-11-17T14:30:00Z",
  "sensor_status": "active"
}
```

   - The UI updates the displayed percentage and status immediately when a new notification arrives.

3. Files to look at
   - `app/src/main/java/com/example/smartplant/BluetoothRepository.kt` — BLE scanning/connection and JSON parsing.
   - `app/src/main/java/com/example/smartplant/MainActivity.kt` — Compose UI and wiring to repository.
   - `app/src/main/java/com/example/smartplant/MoistureModels.kt` — data models and helper function.

4. Limitations & next steps (optional improvements)
- Add runtime permission request flows (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION) with user-friendly explanations.
- Add a device list screen and manual connect UI instead of auto-connecting.
- Implement reconnection logic, error messages, and connection status display.
- Persist readings/history (Room DB) and add settings for update frequency.
- Add pump control commands (write characteristic) when hardware supports it.

If you want, I can now:
- Add a simple permission request flow and an explicit device list UI (recommended), or
- Wire pump start/stop write commands and a manual control button.
