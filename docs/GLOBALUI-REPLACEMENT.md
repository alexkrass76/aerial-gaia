# Gaia system overlays: investigation and replacement design

Status: investigation, 2026-10-25. No vendor component disabled or replaced.
The experimental accessibility volume interceptor in gaia.35 failed the physical
remote test (stock circle remained); its switch was turned off and verified.

## Evidence and limits

Inspected the installed LiteTvGlobalUI 6.0.25.11-HOTFIX (60025011), its local
decompilation, the device configuration and previously extracted CVTE framework
code. Proprietary binaries, source extracts and raw device records remain local
and are not part of this document. Decompiled control flow is evidence for a
design, not proof that every branch executes on this firmware.

Package `com.cvte.tv.globalui` is a persistent vendor system application sharing
`android.uid.system` (1000), with Direct Boot flags, target SDK 23. The installed
permission set includes shared-UID privileges; it must not be interpreted as a
list of privileges our replacement needs. An ordinary signed Aerial APK cannot
replace it in place while preserving its shared UID/signature relationship.

## Architecture found

`TvApplication` starts `OptionService`. That service loads configuration, binds
the TV API, registers global key handlers and notification listeners and starts
conditional ancillary features. `GlobalUiService` separately receives explicit
SHOW_UI / SHOW_UI_WITHOUT_VALUE / SHOW_UI_NOT_SUPPORT intents. Its function
registry includes volume, picture, sound, aspect/zoom, sleep, freeze, eco,
HDR, dynamic backlight and enhancement modes. Some requests also close the
current CVTE settings fragment. Reimplementing only the volume view does not
replace the entire package contract.

Other code paths cover source/signal changes, ARC amplifier volume/mute,
sleep/no-signal/no-action shutdown warnings, keypad lock warnings, USB/storage
handling, physical one/five-key menus, store mode and optional network alerts.
These are capabilities found in code, not all enabled on this TV.

## Actual configuration

`ro.cvte.config.path` points at `/system/etc/menu_config_xml`. The loader reads
`globalui.properties`, then `setting.properties` into the same Properties map;
each file falls back to APK assets if absent. The first file is absent both at
that device path and in the inspected APK; the second was retrieved successfully.

Relevant values in the device's setting.properties:

| Setting | Value | Implication |
|---|---|---|
| enable_function_center_control | true | volume uses CtvManager callback path |
| enable_function_use_linear_volume_style | false | linear style not selected |
| enable_function_use_cvte_volume_style | false | alternate CVTE style not selected |
| config_function_volume_custom_location | CENTER | centered indicator |
| enable_function_alone_volume_window | false | shared overlay host |
| enable_function_show_mute_icon | true | separate mute indication |
| delay_auto_dismiss_volume_bar | 3000 | default holder dismiss interval |
| open_hdmi_autoswitch_function | 0 | optional HDMI auto-switch handler off |
| enable_global_input | 0 | optional global input handler off |
| en_one_key_to_seven_key / en_five_key_menu | 0 / 0 | these key-menu variants off |
| enable_auto_reboot_on_specified_time | 0 | optional scheduled reboot off |
| en_auto_screen_off / en_show_lower_memory_tip | 0 / 0 | optional handlers off |

Do not change the shared configuration path to redirect one service: other CVTE
components consume it. No proven volume-only disable flag has been identified.

## Correct volume event source

With center_control enabled, the volume handler subscribes via
`com.cvte.os.CtvManager.registerTaskCallback(int[], ICtvTaskCallback)`:

* message 1000: Bundle integer `volume`;
* message 1001: Bundle boolean `mute`.

The corresponding unregister method is `unRegisterTaskCallback`.
The framework's CvteHotkeyManager processes volume keys and dispatches those
messages; CtvTaskCallbackManager supports multiple listeners. Its inspected
registration entry point contains no explicit permission check, but class
visibility, Binder access and SELinux still need live verification. Do not
assume an ordinary application can subscribe until tested.

This explains why key interception is the wrong primary architecture. The
replacement should observe final sound state without generating another volume
adjustment. ARC notifications additionally use the TV API path and must not be
silently treated as speaker volume.

## Hiding stock UI is a separate problem

The receiver accepts `com.cvte.tv.globalui.close_win`, optionally with
`closeWinLocation`. A CENTER request removes all views in that location; without
a location it closes the shared host. Neither operation selectively suppresses
future volume windows. Repeated close broadcasts could flicker and dismiss
unrelated CVTE dialogs, so they are not a production replacement strategy.
Disabling GlobalUiService alone also leaves OptionService's callback-driven
volume display alive. Disabling the package would remove both and more.

## Proposed Aerial implementation

1. Add a passive diagnostic subscription to the existing privileged helper.
   Subscribe only to 1000/1001, expose bounded volume/mute state to Aerial, clean
   up on disconnect, and verify real remote press/hold/release. No suppression,
   adjustment, synthetic key events or constant polling in this phase.
2. Implement a dedicated Aerial overlay module that draws the iOS-like meter
   from observed state, does not take focus and has bounded display lifetime.
   Keep system events in the helper and rendering in the application; do not run
   the launcher UI as root or copy vendor implementation/resources into it.
3. Inventory remaining active GlobalUI responsibilities and explicit callers.
   Cover required source, mute and power-warning behavior before any package-wide
   trial. Treat unsupported ARC/Bluetooth paths explicitly.
4. Trial suppression only with a verified restore path. Prefer a genuinely
   isolated stock-volume control if found; otherwise a short package-level
   experiment requires independently scheduled automatic re-enable and working
   ADB recovery. Do not persist suppression during early experiments.
5. Only after parity tests consider an opt-in persistent replacement. Crashing
   Aerial/helper must restore stock presentation, not leave invisible warnings.

## Acceptance before replacing GlobalUI

Single volume steps; hold/release with no runaway changes; mute and unmute;
boundary values; changes from settings; foreground media applications; HDMI
playback; ARC and Bluetooth when hardware is available; source selection;
physical Power behavior; timer warnings; USB insertion; standby/wake; helper
and UI crash recovery; boot before network availability. Reboot requires the
owner's separate consent. Preserve Direct Boot HOME, HDMI and AirPlay.

The replacement is not implemented or validated yet. The immediate next milestone
is receiving the two CtvManager events without changing existing TV behavior.

## Passive probe findings

The standalone `VolumeProbe` checks the boolean registration result, accepts only
system-UID callbacks, subscribes only to 1000/1001 and unregisters after 120s.
Registration and unregistration returned true as shell UID 2000, but no events
were observed during injected or owner-tested physical volume changes. Therefore
center_control configuration alone does not establish the live event source.

A standalone TV API bind probe was rejected by ActivityManager: an app_process
IApplicationThread is not an attached application for bindService. It terminated;
the existing task helper remained connected. The next diagnostic uses Aerial's
existing application binding to TV API. `volume_probe` on the shell-only provider
collects only four allowlisted audio notification names, at most 64 entries, for
20 seconds, then unregisters. No bundle contents or other TV activity are saved.

Gaia.36 release build and all 126 existing unit tests passed; installed and speed
compiled. The in-app TV API probe received two `notifySoundSpeakerOutChange`
notifications for an injected volume-up/down pair. This establishes a working
notification source on the tested firmware. The task helper reattached after
update. The obsolete accessibility overlay remains switched off. No stock UI
suppression, package change, reboot or persistent new listener was performed.
The mute/unmute injected pair also produced four
`notifySoundSpeakerOutMuteChange` notifications. Duplicate notifications must be
coalesced by actual state in a future UI; counting notifications as button presses
would be incorrect. The bounded observer unregisters after each test.

## Gaia.37 passive overlay

The experimental toggle now binds TV API from HomeGuard's application context.
It subscribes only while enabled and unregisters on disable/unbind/destroy; after
user unlock it can start without affecting Direct Boot HOME. Speaker volume/mute
notifications schedule an AudioManager state read and a non-focusable accessibility
overlay, dismissed after 1.8 seconds. The former volume key interception and
AudioManager adjustment code have been removed. No extra key filtering is requested.
ARC/Bluetooth route semantics are still unverified; this is a speaker-only prototype,
not a finished system overlay replacement.

Release build/unit suite passed. Installed and speed compiled. An injected +/−
pair displayed the new meter at 11% alongside the stock circle and restored volume
to 10; the overlay was absent from the window list after timeout. Physical hold,
mute and perception checks are pending owner feedback. Stock UI remains enabled.

## Reversible OptionService experiment

After owner confirmation of the passive overlay, a bounded stop/start trial was
performed. An independent on-device 25-second restoration script was started and
its process checked before stopping `com.cvte.tv.globalui/.OptionService` with
am stopservice (no package/component disable, force-stop, config or image edits).
During a volume-up/down pair, the screenshot showed only Aerial's left meter at
11%; the centered stock circle was absent. OptionService was immediately started
again, before the safety timer. This proves a short-lived visual replacement is
possible; it does not establish parity for power warnings, inputs, USB or ARC.

Do not convert this experiment into persistent suppression yet. The next stage
needs an explicit capability inventory and a lease/watchdog that restores the
stock service if Aerial's event subscription or overlay fails. Stock service
restoration should also be possible without the launcher. No boot suppression
has been added.

## Gaia.38 experimental lease

A separate opt-in setting requests replacement while the passive overlay is enabled.
The launcher renews a fixed helper operation every 3 seconds only while TV API is
registered, the screen is interactive and overlay attachment succeeds. The helper
writes an atomic sequence token in its private directory and starts a separate
shell watchdog. The watchdog stops OptionService once, checks token changes every
4 seconds and restores after three missed updates (roughly 12–16 seconds). Normal
disable also requests immediate restoration. It does not disable packages, edit
vendor files or add a system boot hook. On boot stock UI is available until the
existing helper bootstrap and the new subscription become ready.

Binder operations 14/15 are restricted by the existing exact launcher UID and
interface-token checks. They accept no commands, paths or package names. Only
OptionService can be stopped/restored. The watchdog remains independent of the
launcher and helper, but is not protection against its own SIGKILL or a complete
OS failure. Service start/stop completion and actual windows must be verified;
Android's am stopservice returns a nonzero value even for a successful stop here.

ARC amplifier volume/mute, source-change and active shutdown countdown events
release the lease and latch replacement off for the current overlay lifetime.
This does not guarantee replay of the first warning or cover USB prompts. Full
GlobalUI parity, ARC, Bluetooth, USB and boot/standby acceptance remain outstanding.
The experimental preference indicates intent, not a verified current suppression
state. No public release should claim full system UI replacement yet.

Live failure test: stopping the launcher was not conclusive because the system
restarted it. SIGSTOP of the launcher was denied to shell. Instead the verified
shell-owned task helper was paused, with an independent 25-second resume timer.
At 17 seconds, OptionService was running with a creation age of about 5 seconds:
the separate watchdog had restored it in approximately 12 seconds. The helper was
then resumed. No reboot was used. All 126 existing unit tests pass; they are not
coverage of vendor lifecycle behavior, which was tested on the device as above.

## Gaia.39: remove heartbeat window flash

The owner observed transient 0% meters during UI navigation. The lease health
check called show()/hide() every three seconds, attaching a default-zero meter.
Removed all window creation from lease heartbeats. Replacement now waits for the
first successful real volume-event display in each overlay instance; until then
stock UI remains available. The real meter's fraction is initialized from current
audio state before attachment, so its first frame is no longer default zero.
Build and the existing unit suite pass; installed and speed compiled.
