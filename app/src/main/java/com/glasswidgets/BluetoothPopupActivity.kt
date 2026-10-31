package com.glasswidgets

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Small glass pop-up over the home screen: Bluetooth switch plus the paired devices, like the
 * system Quick Settings panel. It only listens for Bluetooth changes while it is on screen,
 * so it costs nothing when closed.
 */
class BluetoothPopupActivity : Activity() {

    private var adapter: BluetoothAdapter? = null
    private lateinit var subtitle: TextView
    private lateinit var toggle: MaterialSwitch
    private lateinit var devices: android.widget.LinearLayout
    private var updating = false
    private val proxies = HashMap<Int, BluetoothProfile>()

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (isFinishing || isDestroyed || adapter?.state != BluetoothAdapter.STATE_ON) {
                try {
                    adapter?.closeProfileProxy(profile, proxy)
                } catch (_: Exception) {
                }
                return
            }
            proxies[profile] = proxy
            render()
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
            render()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    if (adapter?.state == BluetoothAdapter.STATE_ON) openProxies() else closeProxies()
                }
                render()
            } catch (e: Exception) {
                android.util.Log.e("GlassWidgets", "Bluetooth update failed", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_popup)
        adapter = getSystemService(BluetoothManager::class.java)?.adapter

        subtitle = findViewById(R.id.bt_subtitle)
        toggle = findViewById(R.id.bt_switch)
        devices = findViewById(R.id.bt_devices)

        findViewById<View>(R.id.popup_root).setOnClickListener { finish() }
        findViewById<View>(R.id.bt_done).setOnClickListener { finish() }
        toggle.setOnCheckedChangeListener { _, checked ->
            if (!updating) setBluetooth(checked)
        }

        if (!hasPermission()) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 7)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(receiver, filter)
        if (adapter?.state == BluetoothAdapter.STATE_ON) openProxies()
        render()
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        closeProxies()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (adapter?.state == BluetoothAdapter.STATE_ON) openProxies()
        render()
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun setBluetooth(on: Boolean) {
        val bt = adapter ?: return
        val done = try {
            if (on) bt.enable() else bt.disable()
        } catch (e: Exception) {
            false
        }
        if (!done) {
            // Phone refused (permission missing): let the system handle it
            try {
                startActivity(
                    Intent(if (on) BluetoothAdapter.ACTION_REQUEST_ENABLE else Settings.ACTION_BLUETOOTH_SETTINGS)
                )
            } catch (_: Exception) {
            }
        }
        render()
    }

    private fun openProxies() {
        val bt = adapter ?: return
        if (!hasPermission()) return
        for (profile in intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            if (proxies.containsKey(profile)) continue
            try {
                bt.getProfileProxy(this, listener, profile)
            } catch (_: Exception) {
            }
        }
    }

    private fun closeProxies() {
        val bt = adapter
        // Android removes entries from `proxies` while they close, so walk a copy
        val open = proxies.entries.map { it.key to it.value }
        proxies.clear()
        for ((profile, proxy) in open) {
            try {
                bt?.closeProfileProxy(profile, proxy)
            } catch (_: Exception) {
            }
        }
    }

    private fun connectedAddresses(): Set<String> {
        val out = HashSet<String>()
        for (proxy in proxies.values.toList()) {
            try {
                proxy.connectedDevices.forEach { out.add(it.address) }
            } catch (_: Exception) {
                // Bluetooth is switching off or the service has gone away
            }
        }
        return out
    }

    private fun openBluetoothSettings() {
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            finish()
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun render() {
        if (isFinishing || isDestroyed) return
        try {
            draw()
        } catch (e: Exception) {
            android.util.Log.e("GlassWidgets", "Bluetooth pop-up draw failed", e)
        }
    }

    private fun draw() {
        val bt = adapter
        devices.removeAllViews()
        if (bt == null) {
            subtitle.setText(R.string.bt_unavailable)
            toggle.isEnabled = false
            return
        }
        val state = bt.state
        val on = state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_TURNING_ON
        updating = true
        toggle.isChecked = on
        updating = false

        subtitle.setText(
            when (state) {
                BluetoothAdapter.STATE_TURNING_ON -> R.string.bt_turning_on
                BluetoothAdapter.STATE_TURNING_OFF -> R.string.bt_turning_off
                BluetoothAdapter.STATE_ON -> R.string.bt_subtitle_on
                else -> R.string.bt_off
            }
        )
        if (state != BluetoothAdapter.STATE_ON) return

        val inflater = LayoutInflater.from(this)

        if (!hasPermission()) {
            addRow(inflater, getString(R.string.bt_allow), null, R.drawable.ic_bluetooth, false) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 7)
            }
        } else {
            val connected = connectedAddresses()
            val bonded = try {
                bt.bondedDevices.toList()
            } catch (e: SecurityException) {
                emptyList()
            }
            val sorted = bonded.sortedByDescending { it.address in connected }.take(4)
            if (sorted.isEmpty()) {
                subtitle.setText(R.string.bt_no_devices)
            }
            for (device in sorted) {
                val isConnected = device.address in connected
                val name = try {
                    device.name ?: device.address
                } catch (e: SecurityException) {
                    device.address
                }
                val audio = try {
                    device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
                } catch (e: SecurityException) {
                    false
                }
                addRow(
                    inflater, name,
                    getString(if (isConnected) R.string.bt_connected else R.string.bt_saved),
                    if (audio) R.drawable.ic_headphones else R.drawable.ic_bluetooth,
                    isConnected
                ) { openBluetoothSettings() }
            }
        }
        addRow(inflater, getString(R.string.bt_pair_new), null, R.drawable.ic_add, false) {
            openBluetoothSettings()
        }
    }

    private fun addRow(
        inflater: LayoutInflater,
        name: String,
        status: String?,
        icon: Int,
        highlight: Boolean,
        onClick: () -> Unit
    ) {
        val row = inflater.inflate(R.layout.item_bt_device, devices, false)
        row.findViewById<ImageView>(R.id.dev_icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.dev_name).text = name
        val st = row.findViewById<TextView>(R.id.dev_status)
        if (status == null) st.visibility = View.GONE else st.text = status
        if (highlight) row.setBackgroundResource(R.drawable.popup_row_on)
        row.setOnClickListener { onClick() }
        devices.addView(row)
    }
}
