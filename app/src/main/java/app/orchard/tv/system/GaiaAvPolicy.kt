package app.orchard.tv.system

import org.json.JSONObject

/** Firmware values are data, never reflection or shell instructions. */
internal object GaiaAvPolicy {
    val pictureKeys = setOf("PICTURE_BRIGHTNESS", "PICTURE_CONTRAST", "PICTURE_COLOR", "PICTURE_SHARPNESS", "PICTURE_TINT")
    val pictureModes = setOf("OPTION_PICTURE_MODE_STANDARD", "OPTION_PICTURE_MODE_DYNAMIC", "OPTION_PICTURE_MODE_SOFT", "OPTION_PICTURE_MODE_ENERGY_SAVING", "OPTION_PICTURE_MODE_USER")
    private fun item(data: JSONObject, key: String): JSONObject {
        val items = data.getJSONObject("picture").getJSONArray("items")
        return (0 until items.length()).map { items.getJSONObject(it) }.first { it.getString("key") == key }
    }
    fun value(data: JSONObject, key: String): String = when (key) {
        "cec_device_off", "cec_wakeup" -> data.getJSONObject("cec").getBoolean(key).toString()
        "speaker_balance" -> data.getInt(key).toString()
        "backlight" -> data.getJSONObject(key).getInt("value").toString()
        "picture_mode" -> data.getJSONObject("picture").getString("mode")
        "digital" -> data.getJSONObject(key).getString("mode")
        in pictureKeys -> item(data, key).getInt("value").toString()
        else -> error("Unsupported setting")
    }
    fun validate(current: JSONObject, expected: JSONObject, key: String, value: String, restoring: Boolean = false) {
        check(!current.getString("source").endsWith(":null") && current.getString("source").isNotBlank() && current.getString("source") == expected.getString("source")) { "Input changed" }
        check(value(current, key) == value(expected, key)) { "Setting changed" }
        when (key) {
            "speaker_balance" -> {
                require(value.toInt() in -50..50)
                check(current.getInt(key) in -50..50)
                check(current.getJSONObject("sound").getString("mode") == "OPTION_SOUND_OUT_SPEAKER")
                check(expected.getJSONObject("sound").getString("mode") == "OPTION_SOUND_OUT_SPEAKER")
                check(!current.getBoolean("arc") && !expected.getBoolean("arc"))
            }
            "cec_device_off", "cec_wakeup" -> {
                require(value == "true" || value == "false")
                // With CEC off firmware getters mask stored flags as false. Never write that state.
                check(current.getJSONObject("cec").getBoolean("enabled") && expected.getJSONObject("cec").getBoolean("enabled"))
                check(current.getBoolean("arc") == expected.getBoolean("arc"))
                check(current.getJSONObject("sound").getString("mode") == expected.getJSONObject("sound").getString("mode"))
            }
            "backlight" -> {
                // Avoid a black panel. Undo alone may restore an original value below this floor.
                require(value.toInt() in (if (restoring) 1 else 5)..100)
                check(current.getJSONObject(key).getInt("value") in 1..100)
                check(current.getJSONObject("picture").getString("mode") == expected.getJSONObject("picture").getString("mode"))
                check(current.getJSONObject(key).getString("status") == "ITEM_STATUS_ENABLE")
            }
            "picture_mode", "digital" -> {
                val group = if (key == "picture_mode") "picture" else key
                val modes = current.getJSONObject(group).getJSONArray("modes")
                val allowed = if (key == "picture_mode") pictureModes else setOf("SPDIF_OUT_PCM", "SPDIF_OUT_RAW")
                require(value in allowed && (0 until modes.length()).any { modes.getString(it) == value })
            }
            in pictureKeys -> {
                require(value.toInt() in 0..100)
                check(current.getJSONObject("picture").getString("mode") == "OPTION_PICTURE_MODE_USER")
                check(current.getJSONObject("picture").getString("mode") == expected.getJSONObject("picture").getString("mode"))
                check(item(current, key).getString("status") == "ITEM_STATUS_ENABLE")
            }
            else -> error("Unsupported setting")
        }
    }
}
