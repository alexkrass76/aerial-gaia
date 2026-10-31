# MediaTek scalar helper

Fixed getter allowlist plus seven validated scalar setters for the verified YaOS
MediaTek/Cultraview firmware. No arbitrary method or shell command dispatch.
No factory HAL, CEC writes, profile selection or output routing. CEC uses a separate Android settings backend.
The Java entry point is a disposable diagnostic, not an automatic launcher service.
Do not bundle proprietary firmware APKs/libraries.

Compile with javac against Android's android.jar and dex with d8 (min-api 30).
Push classes.dex to a task-specific /data/local/tmp file, and run via app_process
with that dex in CLASSPATH, passing the installed Yandex Settings APK path.
The shell entry point uses ClassLoaderFactory's shared namespace only inside its
own process; it does not change the system linker configuration. It exits after
printing JSON, including when the native library starts background threads.

For application-UID diagnosis on a Gaia-flavor build, use the existing
WRITE_SECURE_SETTINGS-protected ConfigReceiver with --ez mediatek_read_probe true.
It invokes the same loopback shell backend as the settings page and writes
external-files/mediatek-read-probe.json. The initial ordinary app PathClassLoader
experiment failed native-library isolation and is not the production read path.
The runtime entry point is kept in our installed APK and runs with an 8-second
process timeout. No probe runs during normal startup.

Fields raw and rangeRaw are uninterpreted vendor results. Negative values may
be legitimate signed ranges (e.g. balance), while zero ranges may indicate an
unsupported key. Neither is sufficient to enable a control. Values are scoped
to the current vendor input context (-1); enum meanings and SDR/HDR/source
behavior require further validation. Writes are limited separately by MediaTekWritePolicy and owner authorization.

## Writes and rollback

An optional second argument is a JSON request (key, value, expected snapshot).
It accepts only brightness, contrast, saturation, sharpness, hue, balance and backlight (5..100).
Input, picture profile, audio output, SPDIF, old value and range are re-read and
compared in the same shell process immediately before the fixed setter call.
The setter uses the ordinary per-context setConfigValue(-1, key, value, 3), never
setConfigValueForAllElements. Status code 0 and read-back are both required.

Aerial commits its own before/old/new journal before dispatch. A second snapshot
confirms the result. A failed or timed-out write leaves a pending rollback and
blocks further writes; successful writes retain the last Undo across restart.
Undo refuses changed inputs/profiles/routes or externally modified values, and
clears its journal only after a verified restore. The user must return to the
original context if it changed. This is not a firmware-level atomic transaction;
external changes in the short check/write interval cannot be ruled out. HDR
sub-contexts are not yet independently identified, so HDR/source transition
testing remains required before claiming complete HDMI coverage.

The protected ConfigReceiver mediatek_roundtrip_test diagnostic changes each
allowlisted scalar by one step and immediately undoes it, refusing to overwrite
an existing Undo record. Use only with owner authorization for these writes.
