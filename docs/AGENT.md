# Aerial UI: shell interface

Adapted from upstream [PR #821](https://github.com/pengchujin/aerial-tv/pull/821).
No network server or new permission is added. ConfigReceiver requires the sender
to hold WRITE_SECURE_SETTINGS; ordinary apps cannot call it.

With the project's permanent adb on PATH:

```sh
TV='TV_IP' tools/agent.sh state
TV='TV_IP' tools/agent.sh settings
TV='TV_IP' tools/agent.sh set clock_weight light
TV='TV_IP' tools/agent.sh open settings
TV='TV_IP' tools/agent.sh focus com.example.app
```

TV may include the port. Omit TV to use the single connected adb device. ADB may
name the adb executable explicitly. The helper targets app.orchard.tv.gaia and
prints am broadcast's raw result. Run `help` to list commands. No credentials or
household addresses belong in this file or committed scripts.

- state: stable overlay name, focused tile, ordered tile keys/labels and exposed
  settings, plus screensaver_visible and night_backlight_failed. This is app
  state, not a claim that the physical display accepted a hardware setting.
- settings: each exposed key, its kind, valid choices/range and current value.
- set: explicit allowlist; invalid values leave the setting unchanged. Name mode,
  clock weight and night mode use this fork's existing preferences. Switching
  night_mode while the screensaver is active can start/stop native dimming.
- open: none, control_center, settings, personalize or hidden_apps.
- focus: a visible Home tile key returned by state.

Before user unlock, commands return an error without reading credential-encrypted
preferences. Mutations are refused while the parental lock is engaged. open/focus
require foreground Home with its screensaver dismissed via Back. They do not
silently end a night session or interfere with backlight recovery. PIN values,
ADB grants, boot targets, arbitrary intents and firmware writes are not exposed.
The new interface is covered by JVM/Robolectric tests; physical ADB/remote
acceptance of .54 remains pending on both TVs.
