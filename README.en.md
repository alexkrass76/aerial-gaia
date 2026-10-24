# Aerial Gaia

GaiaOS / CVTE port of [Aerial by pengchujin](https://github.com/pengchujin/aerial-tv), based on v1.41. MIT license retained.

[Russian README](README.md) · [Installation and rollback (Russian)](docs/GAIAOS.md) · [Porting notes](docs/devices/gaiaos-t950s-be30ae.md)

Verified build: **1.47-gaia.5**, versionCode 44, package `app.orchard.tv.gaia`.

- Minimal Direct Boot HOME Activity waits for user unlock before opening the regular UI.
- HOME + CVTE COMMON_HOME categories support physical Home.
- Native CVTE TIF routing for Droidlogic HDMI HW5–HW8.
- Home Guard excludes the TV input screen (`com.cvte.tv.setting`).
- Separate package and revival account identity from upstream Aerial.

Verified on one LVG 32LM57 / TP.T950ST.PB752 / t950s_be30ae, Android 14, firmware 20250715_151455. On 2026-10-24 a reboot completed with Mars hidden and disabled, no Mars process. The owner confirmed HDMI1 picture/audio, physical Home and navigation buttons, and AirPlay after reboot. Other models, HDMI2–4, CEC and long-term operation without Mars are untested.

This is an application, not firmware. It never automatically disables system packages. AirPlay still requires the stock receiver components. Do not disable Mars before testing Direct Boot and establishing Ethernet ADB recovery: an earlier build stalled at Wisdom Share and Wi-Fi did not recover.

Build with JDK 17 and Android SDK 36:

```sh
./gradlew -Pgaia assembleStandardRelease
```

The `-Pgaia` flag and standard variant are required. Output: `app/build/outputs/apk/standard/release/app-standard-release.apk`.

Signing uses private `local/signing.properties` and a keystore in `local/`; without these upstream falls back to a machine-local debug key. Updates require the same signing key as the installed copy. Never uninstall to work around a signature mismatch if you need to preserve data.

Upstream install/restore scripts, website and update notifications are **not Gaia deployment tools**. No Gaia automatic update channel is implemented. Preserve all Gaia patches and re-test boot, HOME and HDMI before adopting newer upstream code. No signing keys, household configuration, proprietary Mars APKs or raw device logs are included.
