package app.orchard.tv.system

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import app.orchard.tv.settings.AerialSettings

/** Page-scoped discovery. Never enables the adapter or removes existing bonds. */
internal class GaiaBluetoothDiscovery(context: Context, private val changed: () -> Unit) : AutoCloseable {
    private val app = context.applicationContext
    private val adapter get() = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val handler = Handler(Looper.getMainLooper())
    private val found = linkedMapOf<String, BluetoothDevice>()
    val devices get() = found.values.toList()
    var scanning = false; private set
    var failed = false; private set
    var pairing: String? = null; private set
    private val stop = Runnable { stopScan() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runCatching {
                @Suppress("DEPRECATION")
                val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> if (scanning && device != null && device.bondState != BluetoothDevice.BOND_BONDED && found.size < 32) found[device.address] = device
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { scanning = false; handler.removeCallbacks(stop) }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> if (device != null) {
                        val state = device.bondState
                        if (state == BluetoothDevice.BOND_BONDED) found.remove(device.address)
                        if (device.address == pairing && state != BluetoothDevice.BOND_BONDING) {
                            failed = state != BluetoothDevice.BOND_BONDED
                            pairing = null
                        }
                    }
                }
            }.onFailure { failed = true }
            changed()
        }
    }
    fun register() {
        ContextCompat.registerReceiver(app, receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }, ContextCompat.RECEIVER_EXPORTED)
    }
    fun startScan() {
        check(!AerialSettings.locked)
        failed = false
        runCatching {
            check(adapter != null && adapter.isEnabled && !adapter.isDiscovering)
            found.clear()
            check(adapter.startDiscovery())
            scanning = true
            handler.postDelayed(stop, 20_000)
        }.onFailure { failed = true }
        changed()
    }
    fun stopScan() {
        handler.removeCallbacks(stop)
        if (scanning) runCatching { adapter?.cancelDiscovery() }
        scanning = false
        changed()
    }
    fun pair(address: String) {
        check(!AerialSettings.locked)
        val device = found[address] ?: error("Device no longer available")
        check(device.bondState == BluetoothDevice.BOND_NONE)
        stopScan()
        failed = false
        pairing = address
        runCatching { check(device.createBond()) }.onFailure { pairing = null; failed = true }
        changed()
    }
    override fun close() {
        handler.removeCallbacks(stop)
        if (scanning) runCatching { adapter?.cancelDiscovery() }
        scanning = false
        app.unregisterReceiver(receiver)
    }
}
