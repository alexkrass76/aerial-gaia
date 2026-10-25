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
