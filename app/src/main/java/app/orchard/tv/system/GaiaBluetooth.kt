package app.orchard.tv.system

import android.bluetooth.BluetoothManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ordinary app attribution for Bluetooth; errors never masquerade as an empty bonded list. */
internal object GaiaBluetooth {
    fun snapshot(context: Context): JSONObject {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: error("Bluetooth unavailable")
        val enabled = adapter.isEnabled
        val devices = JSONArray()
        if (enabled) adapter.bondedDevices.sortedBy { it.name ?: it.address }.forEach {
            devices.put(JSONObject().put("name", it.name.orEmpty()).put("address", it.address))
        }
        return JSONObject().put("enabled", enabled).put("devices", devices)
    }
}
