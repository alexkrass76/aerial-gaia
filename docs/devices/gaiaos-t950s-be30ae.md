# GaiaOS / CVTE porting notes

Scope: LVG 32LM57, TP.T950ST.PB752, Amlogic t950s_be30ae, Android 14, 32-bit ARM, firmware 20250715_151455, 1 GB RAM / 8 GB storage. Tested 2026-10-24. Findings are device-specific, not a compatibility guarantee for related boards.

## Boot failure and fix

Before 1.41-gaia.3, disabling `com.cvte.tv.launcher.mars` could leave the TV at Wisdom Share after the LVG logo. The captured log reported `No home screen found` for MAIN + HOME + COMMON_HOME while user storage was still locked. Home Guard correctly deferred takeover until unlock. Mars had a direct-boot-aware HOME; the old Aerial MainActivity did not.

`app/src/standard/java/app/orchard/tv/DirectBootHomeActivity.kt` is a minimal framework Activity declared directBootAware in the standard manifest. It draws only an APK resource label, not Compose, preferences, wallpaper, PIN data or network content. It registers USER_UNLOCKED before checking UserManager, starts MainActivity once unlocked, and unregisters onStop to avoid pulling the user out of another app/input. MainActivity remains credential-storage-only. Do not mark the entire application directBootAware as a shortcut.

Successful boot sequence with Mars hidden/disabled: startup Activity logged userUnlocked=false; SurfaceFlinger reported boot finished around 19.7 seconds from boot; the Activity opened MainActivity about six seconds after its creation. sys.boot_completed=1 and service.bootanim.exit=1; no Mars process. This supports the missing Direct Boot HOME diagnosis; it is not proof that every GaiaOS firmware has no other Mars dependencies.

## HOME resolution

The standard HOME filter includes MAIN, HOME, COMMON_HOME and DEFAULT. Package is `app.orchard.tv.gaia`; component class namespace remains `app.orchard.tv`.

Setting the ordinary default with cmd package set-home-activity alone did not persist the combined HOME+COMMON_HOME selection on this firmware. Selecting Aerial and Always in the system resolver was necessary. Home Guard intercepted the chooser during setup; temporarily pause only its service, preserve other accessibility services, select Always, then restore it. Verify resolution with MATCH_DIRECT_BOOT_AWARE (524288) and both categories before a reboot. A boot through ResolverActivity is not evidence that the bootstrap worked.

## HDMI and Home Guard

`CvteSources.kt` detects Droidlogic HDMI HW5–HW8 and the exported/enabled native component:

`com.cvte.tv.api.impl/com.cvte.tv.api.vendor.common.base.tif.TvSupportActivity`

Use action `com.cvte.intent.ACTION_TIF_PLAYER`, sourceId equal to HW number, tune=true, fullscreen bounds and target `com.cvte.tv.setting/com.cvte.tv.setting.TvSettingActivity` with TV_HOME. The sourceId is not CVTE's EnumVendorInputSource ordinal. Nonmatching inputs keep the original routes. Only HDMI1/HW5 was physically verified.

The input screen advertises TV_HOME. Home Guard must exclude `com.cvte.tv.setting` from launcher detection; otherwise it replaces HDMI with Aerial. Native TV API/settings services must remain installed and enabled.

## Mars persistence and recovery

Plain disable-user was reset to enabled=1 during a reboot. On this stock userdebug build, adb root allowed pm hide plus disable-user. After unroot and reboot, the active package stayed installed=true, hidden=true, enabled=3. The factory fallback entry in dumpsys can still show different state; distinguish it from the active updated package. Mars skin was untouched.

Unhiding and enabling are both required to undo this state. No APK or user data was deleted. Stock root availability must not be assumed on other devices; see [recovery procedure](../GAIAOS.md).

## Validation and limits

The exact port's release build and signature verification passed. After reboot without Mars, the owner confirmed HDMI1 picture/audio, physical Home, arrows, OK, Back and AirPlay. Home Guard remained enabled. AirPlay depends on enabled `com.maxhub.screenshare.server.tv` and `com.screenshare.platform`; it is not implemented by Aerial.

Not verified: other models/firmware, HDMI2–4, CEC, long-term stability, standby/wake without Mars. No proprietary APKs or decompiled vendor code are redistributed.
