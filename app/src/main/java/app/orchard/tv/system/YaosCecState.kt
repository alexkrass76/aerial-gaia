package app.orchard.tv.system

import org.json.JSONArray
import org.json.JSONObject

/** Firmware-specific runtime dump parser. Missing/conflicting fields remain unknown. */
internal object YaosCecState {
    private val fields = mapOf("enabled" to "mHdmiControlEnabled", "auto_off" to "mAutoDeviceOff",
        "auto_wake" to "mAutoWakeup", "volume" to "mHdmiCecVolumeControlEnabled",
        "system_audio" to "mSystemAudioActivated", "arc_established" to "mArcEstablished")
    fun parse(dump: String): JSONObject {
        val prefix = dump.substringBefore("CEC message history:").take(32_768)
        val root = JSONObject()
        for ((key, field) in fields) {
            val matches = Regex("(?m)^\\s*" + field + "\\s*[:=]?\\s+(true|false)\\s*$")
                .findAll(prefix).map { it.groupValues[1] }.toList()
            if (matches.size == 1) root.put(key, matches.single() == "true")
        }
        val ports = Regex("(?m)^\\s*port_id: ([0-9]+), type: HDMI_IN, address: 0x[0-9a-fA-F]+, cec: (true|false), arc: (true|false), mhl: (true|false)\\s*$")
            .findAll(prefix).toList()
        // No ports found is unknown, not proof that no ARC port exists.
        if (ports.isNotEmpty()) root.put("arc_ports", JSONArray(ports.filter { it.groupValues[3] == "true" }
            .map { it.groupValues[1].toInt() }.distinct()))
        return root
    }
}
