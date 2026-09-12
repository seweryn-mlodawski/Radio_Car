package com.seweryn.radiocar.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothDeviceTracker(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private var a2dpProfile: BluetoothA2dp? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = proxy as? BluetoothA2dp
                updateConnectedDevice()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProfile = null
                updateConnectedDevice()
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateConnectedDevice()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateConnectedDevice()
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateConnectedDevice()
        }
    }

    fun start() {
        // Register Audio Device Callback
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))

        // Register Bluetooth BroadcastReceiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        ContextCompat.registerReceiver(
            context,
            btReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        // Setup A2DP profile proxy
        try {
            bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        } catch (_: Exception) {}

        updateConnectedDevice()
    }

    fun stop() {
        try {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {}

        try {
            context.unregisterReceiver(btReceiver)
        } catch (_: Exception) {}

        try {
            a2dpProfile?.let {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it)
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun updateConnectedDevice() {
        var rawName: String? = null

        // 1. Try A2DP proxy if permission is available
        if (hasBluetoothPermission()) {
            try {
                val connectedA2dp = a2dpProfile?.connectedDevices
                val firstDev = connectedA2dp?.firstOrNull()
                if (firstDev != null) {
                    rawName = firstDev.name
                }
            } catch (_: Exception) {}
        }

        // 2. Try AudioManager devices
        if (rawName.isNullOrBlank() && audioManager != null) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (isBtAudioType(device.type)) {
                    val pName = device.productName?.toString()?.trim()
                    if (!pName.isNullOrBlank() && !pName.equals("Bluetooth", ignoreCase = true)) {
                        rawName = pName
                        break
                    }
                }
            }
        }

        val cleaned = formatDeviceName(rawName)
        _connectedDeviceName.value = cleaned
    }

    private fun isBtAudioType(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_SPEAKER) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        /**
         * Cleans awkward technical Bluetooth names like:
         * "BMW - 6784 ghy", "BMW_12345", "JBL 678857", "Audi MMI 3241"
         * into clean, premium display names.
         */
        fun formatDeviceName(rawName: String?): String? {
            if (rawName.isNullOrBlank()) return null
            var name = rawName.trim()

            // Remove quotes if present
            name = name.removeSurrounding("\"", "\"").trim()

            // Normalize underscores and hyphens with spaces
            name = name.replace("_", " ")

            val upper = name.uppercase()

            // 1. BMW cars (e.g. BMW X3, BMW-6784 ghy, BMW 12345)
            if (upper.startsWith("BMW")) {
                val modelMatch = Regex("(?i)\\bBMW\\s+(X[1-7]|M[2-8]|[1-8]\\s*Series|[1-8]\\d{2}[a-z]?)\\b").find(name)
                return if (modelMatch != null) {
                    modelMatch.value.replace(Regex("\\s+"), " ").trim()
                } else {
                    "BMW"
                }
            }

            // 2. Audi (e.g. Audi MMI 3412, Audi_BT)
            if (upper.startsWith("AUDI")) {
                return if (upper.contains("MMI")) "Audi MMI" else "Audi"
            }

            // 3. Mercedes / MB (e.g. MB Bluetooth 9823)
            if (upper.startsWith("MB ") || upper.startsWith("MERCEDES")) {
                return "Mercedes-Benz"
            }

            // 4. Volkswagen / VW
            if (upper.startsWith("VW") || upper.startsWith("VOLKSWAGEN")) {
                return "Volkswagen"
            }

            // 5. Volvo
            if (upper.startsWith("VOLVO")) {
                return "Volvo"
            }

            // 6. Ford
            if (upper.startsWith("FORD")) {
                return if (upper.contains("SYNC")) "Ford SYNC" else "Ford"
            }

            // 7. Toyota
            if (upper.startsWith("TOYOTA")) {
                return "Toyota"
            }

            // 8. Skoda
            if (upper.startsWith("SKODA") || upper.startsWith("ŠKODA")) {
                return "Škoda"
            }

            // 9. JBL speakers/headphones (e.g. JBL 678857, JBL Flip 5)
            if (upper.startsWith("JBL")) {
                val jblModel = Regex("(?i)JBL\\s+(Flip\\s*\\d*|Charge\\s*\\d*|Go\\s*\\d*|Boombox\\s*\\d*|Extreme\\s*\\d*|Clip\\s*\\d*|Tune\\s*\\d*|Live\\s*\\d*)").find(name)
                return jblModel?.value?.trim() ?: "JBL Audio"
            }

            // 10. Sony / Bose / Sennheiser / Marshall / Jabra / Galaxy Buds / AirPods
            if (upper.contains("AIRPODS")) {
                return if (upper.contains("PRO")) "AirPods Pro" else if (upper.contains("MAX")) "AirPods Max" else "AirPods"
            }
            if (upper.contains("GALAXY BUDS")) {
                val budsMatch = Regex("(?i)Galaxy Buds\\w*(\\s*(Pro|Live|FE|\\d+))*").find(name)
                return budsMatch?.value?.trim() ?: "Galaxy Buds"
            }
            if (upper.startsWith("SONY")) {
                val sonyMatch = Regex("(?i)SONY\\s+(WH|WF)-[A-Za-z0-9]+").find(name)
                return sonyMatch?.value?.trim() ?: "Sony Audio"
            }
            if (upper.startsWith("BOSE")) {
                return "Bose Audio"
            }
            if (upper.startsWith("MARSHALL")) {
                return "Marshall Audio"
            }

            // Generic clean up: remove trailing random numbers or hex strings like " - 6784 ghy", " 4829", " #1"
            name = name.replace(Regex("[-–—]\\s*[0-9A-Za-z]{3,}$"), "").trim()
            name = name.replace(Regex("\\b[0-9A-Fa-f]{4,}\\b"), "").trim()
            name = name.replace(Regex("\\s+"), " ").trim()

            return name.ifBlank { "Urządzenie Bluetooth" }
        }
    }
}
