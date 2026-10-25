package app.orchard.tv.ui.settings

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.orchard.tv.HomeViewModel
import app.orchard.tv.R
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.tasks.TaskBridgeClient
import app.orchard.tv.ui.theme.*
import gaia.bridge.WifiInputPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WifiJoinForm(ssid: String, security: String, savedId: Int, vm: HomeViewModel, onBack: () -> Unit, onAccepted: () -> Unit) {
    var password by remember(ssid, security) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    DisposableEffect(context) {
        var owner = context
        while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
        val window = (owner as? Activity)?.window
        val alreadySecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    BackHandler { password = ""; onBack() }
    BasicText(ssid, style = Type.Headline)
    BasicText(stringResource(R.string.gs_connect_confirm, ssid), style = Type.Caption)
    if (security == "wpa2") {
        BasicText(stringResource(R.string.gs_wifi_password), style = Type.Body)
        BasicTextField(password, { if (it.length <= 64) password = it },
            textStyle = Type.Body, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().background(Palette.Fill).padding(16.dp))
        BasicText(stringResource(R.string.gs_wifi_password_hint), style = Type.Caption)
    }
    if (failed) BasicText(stringResource(R.string.gs_connections_failed), style = Type.Caption)
    if (busy) BasicText(stringResource(R.string.tasks_loading), style = Type.Caption)
    val valid = runCatching { WifiInputPolicy.validate(ssid, security, password) }.isSuccess
    SettingRow(Glyph.Wifi, stringResource(R.string.gs_connect), detail = if (valid) null else stringResource(R.string.gs_wifi_invalid)) {
        if (!busy && valid) vm.whenUnlocked {
            busy = true
            val secret = password
            password = ""
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    check(!AerialSettings.locked)
                    TaskBridgeClient.addWifi(ssid, security, secret, savedId)
                } }
                busy = false
                failed = result.isFailure
                if (result.isSuccess) onAccepted()
            }
        }
    }
    SettingRow(Glyph.ChevronLeft, stringResource(R.string.gs_back)) { password = ""; onBack() }
}
