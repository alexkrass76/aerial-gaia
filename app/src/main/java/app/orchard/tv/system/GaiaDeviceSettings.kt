package app.orchard.tv.system

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import app.orchard.tv.settings.AerialSettings

/** Fixed, reversible Android preferences. Never accepts arbitrary setting names or shell input. */
internal object GaiaDeviceSettings {
    enum class Key(val table: String, val setting: String, val options: List<String>) {
        CAPTIONS_ENABLED("secure", "accessibility_captioning_enabled", listOf("0", "1")),
        CAPTIONS_SCALE("secure", "accessibility_captioning_font_scale", listOf("0.75", "1.0", "1.25", "1.5", "2.0")),
        CAPTIONS_STYLE("secure", "accessibility_captioning_preset", listOf("0", "1", "2", "3", "4", "-1")),
        AUTO_TIME("global", "auto_time", listOf("0", "1")),
        CLOCK_FORMAT("system", "time_12_24", listOf("12", "24")),
        TOUCH_SOUND("system", "sound_effects_enabled", listOf("0", "1")),
        FONT_SCALE("system", "font_scale", listOf("0.85", "1.0", "1.15", "1.3")),
        WINDOW_ANIMATION("global", "window_animation_scale", listOf("0", "0.5", "1")),
        TRANSITION_ANIMATION("global", "transition_animation_scale", listOf("0", "0.5", "1")),
        ANIMATOR_DURATION("global", "animator_duration_scale", listOf("0", "0.5", "1")),
    }

    fun read(context: Context, key: Key): String? = when (key.table) {
        "secure" -> Settings.Secure.getString(context.contentResolver, key.setting)
        "global" -> Settings.Global.getString(context.contentResolver, key.setting)
        else -> Settings.System.getString(context.contentResolver, key.setting)
    }
    private fun put(context: Context, key: Key, value: String?): Boolean = when (key.table) {
        "secure" -> Settings.Secure.putString(context.contentResolver, key.setting, value)
        "global" -> Settings.Global.putString(context.contentResolver, key.setting, value)
        else -> Settings.System.putString(context.contentResolver, key.setting, value)
    }
    fun equivalent(a: String?, b: String?): Boolean = a == b ||
        (a?.toDoubleOrNull() != null && a.toDoubleOrNull() == b?.toDoubleOrNull())

    /** Check stale UI, write, verify. Do not overwrite concurrent changes on mismatch. */
    @Synchronized fun set(context: Context, key: Key, expected: String?, value: String) {
        check(!AerialSettings.locked) { "Locked" }
        require(value in key.options)
        check(read(context, key) == expected) { "Setting changed; refresh required" }
        check(put(context, key, value)) { "Write refused" }
        val actual = read(context, key)
        if (!equivalent(actual, value)) {
            error("Read-back mismatch")
        }
    }

    data class Keyboard(val id: String, val label: String)
    fun keyboards(context: Context): List<Keyboard> {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return manager.enabledInputMethodList.map { Keyboard(it.id, it.loadLabel(context.packageManager).toString()) }
    }
    fun selectedKeyboard(context: Context): String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    @Synchronized fun selectKeyboard(context: Context, id: String, expected: String?) {
        check(!AerialSettings.locked)
        require(keyboards(context).any { it.id == id })
        check(selectedKeyboard(context) == expected)
        check(Settings.Secure.putString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, id))
        check(selectedKeyboard(context) == id)
    }
}
