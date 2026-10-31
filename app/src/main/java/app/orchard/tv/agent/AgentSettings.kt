package app.orchard.tv.agent

import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.Effects
import app.orchard.tv.settings.FocusStyle
import app.orchard.tv.settings.UiSounds
import app.orchard.tv.wallpaper.AerialQuality
import app.orchard.tv.wallpaper.ScreensaverMode
import app.orchard.tv.wallpaper.Wallpapers

/**
 * The settings of Personalize an agent can read and change by key (see [AgentApi]). The parental PIN,
 * the adb access and what the TV boots into are left out on purpose: they guard the owner's
 * choices, and an agent that can reach them can undo the guard.
 */
object AgentSettings {
    fun all(): List<AgentSetting> = listOf(
        AgentSetting.bool("one_row_home", { AerialSettings.oneRowHome }, AerialSettings::chooseOneRowHome),
        AgentSetting.bool("peek_next_row", { AerialSettings.peekNextRow }, AerialSettings::choosePeekNextRow),
        AgentSetting.bool("home_plate", { AerialSettings.homePlate }, AerialSettings::chooseHomePlate),
        AgentSetting.choice("name_mode", app.orchard.tv.settings.NameMode.entries.map { it.key }, { AerialSettings.nameMode.key }) { AerialSettings.chooseNameMode(requireNotNull(app.orchard.tv.settings.NameMode.of(it))) },
        AgentSetting.choice("clock_weight", app.orchard.tv.settings.ClockWeight.entries.map { it.key }, { AerialSettings.clockWeight.key }) { AerialSettings.chooseClockWeight(app.orchard.tv.settings.ClockWeight.of(it)) },
        AgentSetting.bool("night_mode", { AerialSettings.screensaverNight }, AerialSettings::chooseScreensaverNight),
        AgentSetting.choice("night_minutes", listOf("5","15","30","60"), { AerialSettings.screensaverNightMinutes.toString() }) { AerialSettings.chooseScreensaverNightMinutes(it.toInt()) },
        AgentSetting.bool("clock_right", { AerialSettings.clockRight }, AerialSettings::chooseClockRight),
        AgentSetting.bool("large_clock", { AerialSettings.largeClock }, AerialSettings::chooseLargeClock),
        AgentSetting.choice("focus_style", FocusStyle.entries.map { it.key }, { AerialSettings.focusStyle.key }) { AerialSettings.chooseFocusStyle(FocusStyle.of(it)) },
        AgentSetting.choice("screensaver", ScreensaverMode.entries.map { it.key }, { Wallpapers.screensaver.key }) { Wallpapers.chooseScreensaver(ScreensaverMode.of(it)) },
        AgentSetting.choice("aerial_quality", AerialQuality.entries.map { it.key }, { AerialSettings.aerialQuality.key }) { AerialSettings.chooseAerialQuality(AerialQuality.of(it)) },
        AgentSetting.choice("screensaver_minutes", AerialSettings.screensaverChoices.map { it.toString() }, { AerialSettings.screensaverMinutes.toString() }) { AerialSettings.chooseScreensaverMinutes(it.toInt()) },
        AgentSetting.bool("back_starts_screensaver", { AerialSettings.backStartsScreensaver }, AerialSettings::chooseBackStartsScreensaver),
        AgentSetting.choice("ui_sounds", UiSounds.entries.map { it.key }, { AerialSettings.uiSounds.key }) { AerialSettings.chooseUiSounds(UiSounds.of(it) ?: UiSounds.Tv) },
        AgentSetting.choice("effects", Effects.entries.map { it.key }, { AerialSettings.effects.key }) { AerialSettings.chooseEffects(Effects.of(it)) },
    )
}
