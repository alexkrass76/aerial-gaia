# Experimental Gaia task bridge

This prototype lists existing activity stacks and resumes them through
`startActivityFromRecents`. It can remove eligible tasks using Android removeTask. It does not capture previews, force-stop apps, keep
apps alive or modify system packages. Android may reclaim background apps.

## Architecture and trust boundary

A separate Java helper runs as ADB shell (uid 2000; root is unnecessary on the
Gaia Android 14 firmware tested here). It resolves the launcher UID through
PackageManager Binder without initializing ActivityThread, a system Context or
graphics. It attaches a Binder through a provider protected by DUMP plus an
explicit shell/root UID check. Task operations accept only the Gaia launcher UID;
the protocol exposes only list, resume and removeTask, with task IDs checked against the
current eligible list. There is no TCP listener or arbitrary command endpoint.

The app returns a lifetime/callback Binder. Its callback also checks shell/root
UID and its interface token. Launcher death clears the callback and schedules a fresh attachment with exponential
retry (1–30 seconds). The helper survives launcher updates/restarts within the same
boot. An opt-in launcher bootstrap can restart it after reboot (see below). A shell-owned file
lock prevents duplicate helper/input-reader processes.

## Why Home Guard alone did not work

`dumpsys window policy` on this Gaia firmware reports
`mDoubleTapOnHomeBehavior=DOUBLE_TAP_HOME_PIP_MENU`. The second fast Home is
consumed before accessibility key filtering. Starting the already-open MainActivity
on the first Home also briefly pauses it. Both issues were observed during tests.
The attempted Home Guard and DirectBoot gesture changes have been reverted.

The helper can optionally observe explicitly mapped input-device names and Linux HOME codes using
`getevent -t`, available to the shell's input group. Only HOME matches are retained
in memory; other event contents are immediately discarded and input is not logged
or saved. The reader does not grab devices or consume any keys. Pairing is per
input device: two short releases 80–650 ms apart; held keys do not count.

The callback opens cards only while MainActivity is resumed and the screensaver
is absent. A brief HOME transition may temporarily pause MainActivity, so the
request waits 180 ms and can be delivered after resume for at most one second.
It does not bring the switcher over an unrelated foreground app. A single Home,
Direct Boot, HDMI and accessibility filtering retain their original code paths.

## Build and run

```sh
JAVA_HOME=/path/to/jdk ANDROID_HOME=/path/to/android-sdk \
  tools/task-bridge/build.sh /path/to/output
./gradlew -Pgaia assembleStandardRelease
```

Install the signed Gaia APK in place. Create a shell-owned mode-700 directory
`/data/local/tmp/aerial-task-bridge`, push `output/dex/classes.dex` there as
`bridge.dex`, and push `tools/task-bridge/start.sh` alongside it. The dex should be
mode 600. Start explicitly via ADB:

```sh
adb shell sh /data/local/tmp/aerial-task-bridge/start.sh
```

With no mapping arguments this enables task switching but not Home observation.
Append quoted `name=Input device name:DECIMAL_LINUX_KEYCODE` arguments for devices
whose Home mapping you have verified. For example, the tested Bluetooth remote
is named `Viettronics Keyboard` and emits Linux code 172. Some IR remotes use 102.
Inspect `getevent -lp`, `dumpsys input` and the actual key-layout files; verify a
physical Home press before adding a mapping. Names are matched exactly against
`/sys/class/input/eventN/device/name` when a candidate key arrives, so event-node
renumbering does not require editing the configuration. Unknown names fail closed.

The script uses nohup and redirection so an open Mac/ADB terminal is unnecessary.
It does not register a boot service. Diagnostics remain in the private helper
folder, without input-event contents. A second invocation exits without launching
another reader. To stop, identify this helper PID and send TERM; its shutdown hook
attempts to terminate its getevent child, but TERM cleanup was not reliable on the tested firmware: identify and stop that owned child separately if necessary. Do not kill unrelated app_process instances.

The switcher can also be opened explicitly:

```sh
adb shell am start -n app.orchard.tv.gaia/app.orchard.tv.tasks.TaskSwitcherActivity
```

Left/Right chooses a card, OK resumes the task and Back returns. Double Home works
while the named-input helper is running. A launcher restart triggers reattachment;
a TV reboot requires either an explicit start or the opt-in bootstrap below. No root, system-image edits or boot
scripts have been installed.

## Validation

- First prototype: Fluffy returned to the same Downloads page and process;
  LocalSend selection, arrows, OK and Home confirmed by the owner.
- Native double-Home replay through Android uinput opened the switcher, including
  the physical remote's approximately 190 ms release interval.
- The owner subsequently confirmed physical double Home and ordinary Home work.
- Regression tests cover a request arriving during pause, single delivery after
  resume, and expiry rather than opening over a much later app transition.
- Existing boot, launcher and screensaver tests remain in the suite.
- Initial helper memory was about 46.5 MiB PSS + 1.4 MiB swap; lean-bootstrap
  samples ranged about 36.6–42.3 MiB PSS. These are not a controlled benchmark;
  input-reader overhead has not yet been separately characterized.

Current-boot resilience validation:

- Stopped and reopened Aerial: its PID changed, while the helper PID and input-reader
  PID stayed the same. The helper reattached and double Home opened the switcher.
- A virtual input device added after helper startup was recognized by name.
- A duplicate helper invocation exited with “already running”.
- Pure Java timing tests cover fast presses, held/orphan releases, distinct devices,
  duplicate-node suppression and clearing stale state while disconnected.
- Current helper + input-reader memory was approximately 43.1 + 0.55 MiB PSS.
  This remains substantial for a 1 GiB television.

## Opt-in local ADB bootstrap

Gaia builds compile and bundle the helper dex from source automatically. After
user unlock, MainActivity can connect exclusively to `127.0.0.1:5555`, verify
shell uid 2000, upload the asset to a private shell directory, verify SHA-256,
and launch it with nohup/setsid. A short launch grace period lets the child detach
before the shell stream closes. Binder attachment, not shell output, confirms
success. Helper death also triggers bounded exponential retries.

This is disabled by default. Enable, inspect or disable future starts with:

```sh
adb shell content call --uri content://app.orchard.tv.gaia.tasks --method set_autostart --arg true
adb shell content call --uri content://app.orchard.tv.gaia.tasks --method status
adb shell content call --uri content://app.orchard.tv.gaia.tasks --method set_autostart --arg false
```

Disabling does not terminate an already running helper. No system startup scripts,
ADB authentication settings or system packages are modified. The current bootstrap
requires Android 12+ and the tested firmware's existing local shell ADB access;
it stops on an AUTH request rather than supplying keys or weakening authentication.
There is no configurable remote endpoint. The local ADB transport tests cover
AUTH rejection, checksum and size validation, fragmented shell traffic and SYNC
uploads. Protocol references: Android's [ADB protocol](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/protocol.md)
and [SYNC protocol](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/sync.md).

Validation: automatic startup and recovery after stopping the owned helper and
reader both succeeded. Following an authorized TV reboot, Direct Boot progressed
from locked to unlocked, the launcher logged local bootstrap attachment, and one
helper plus one input reader were observed without starting them from the computer.
The owner confirmed double Home, task selection and single Home after reboot,
but reported multitasking only became available after Wi-Fi connected. Loopback
transport therefore does not establish network-independent startup on this firmware;
ADB service availability during network initialization remains to be investigated.

Still pending: independent third-party UID-denial tests, prolonged use,
video/DRM restoration, reliable child cleanup after abrupt helper termination,
helper version replacement during in-place updates, and broader memory optimization.
The launcher release includes this opt-in experimental feature; it is not a general-purpose or fully hardened daemon.

## Dock-style switcher presentation

The switcher reuses Home's cached tile artwork (including custom
covers), the shared AppTile component, Inter typography and dock spring focus. A
centered horizontal row replaces placeholder window cards; labels remain visible,
and Home's prepared wallpaper is shared as the background with a 28% black veil.
A static dark gradient is the fallback while no wallpaper is available; no second
video player or screenshot capture is started. Unknown catalog entries
use the same icon-to-tile factory as Home. No window snapshots are captured.

The release build was installed in place. Device screenshot inspection confirmed
layout and artwork, and remote-key injection verified Right/OK returns to Fluffy
and Home returns to Aerial. Physical remote and visual acceptance of this revision
remain for the owner to confirm.

Wallpaper revision: release build installed in place; device screenshot confirmed
the chosen wallpaper behind the task tiles with readable labels.

The focus revision uses Home AppTile directly: configured focus style, lift, rim,
shadow, edge nudge and label visibility match the dock. Long-press editing is not
available in the task switcher; OK still resumes the existing task.

## Closing tasks and on-demand help

Up closes the focused task through ActivityTaskManager.removeTask, after the
helper revalidates both its live task ID and package policy. It does not force-stop
the package: Android manages process reclamation and foreground services can
continue. Launcher, Android/CVTE/Droidlogic components, WireGuard and the Gaia
AirPlay/MAXHUB packages are protected; this policy is checked by the helper, not
only by the UI. Policy tests cover protected packages and ordinary applications.

Down focuses the small circular info button. OK toggles localized help; Back
first hides help/errors, then closes the switcher. Closing a task moves focus to
the first remaining tile, or the info button when the list is empty.

On-device verification: closing AIDA64 removed its activity stack and process;
the info button revealed the help text without leaving the switcher.

Final UI: both title and help start hidden and toggle through the info button.
Empty-list focus, closing the last task and Home were checked on the device.
