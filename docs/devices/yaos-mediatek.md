# YaOS / MediaTek (HI VHIX-65U169MSY)

## HDMI and channel list (1.48-gaia.40)

The generic TV passthrough route did not open HDMI on this Android 11 firmware.
The owner confirmed picture and sound from a PS5 on HDMI2 when ACTION_VIEW with
TvContract.buildChannelUriForPassthroughInput(physicalInputId) explicitly targets
com.mediatek.wwtv.tvcenter/.nav.TurnkeyUiMainActivity. Home returned to Aerial.

The routing override requires both the MediaTek input service and Yandex Live TV,
and an enabled, exported MediaTek activity. It uses the physical input rather
than the CEC child. CVTE routing keeps priority and other platforms keep their
existing routing. HDMI1/HDMI3 and CEC wake are not verified.

## Native channel browser (1.48-gaia.41)

Sources has a separate Yandex channels entry opening Aerial's ChannelsActivity.
The browser reads the Efir catalog through
`content://com.yandex.tv.channels_redirect_proxy/channel?original_authority=android.media.tv`.
The firmware declares `yandex.tv.permission.ACCESS_TV_SOURCE` as normal; Aerial
requests it in its manifest. ADB shell lacks this permission, but reading from
Aerial's own process was verified: 246 channels and 9 groups, without root.

Only channel names, numbers, identifiers and category metadata are used. Raw
provider metadata is neither exposed nor logged. Favorites are private Aerial
preferences keyed by input/content ID (channel ID fallback), independent of the
stock player's favorites. Groups and favorite persistence were tested on-device.
The source entry can be pinned with the existing customization controls.

Playback uses explicit `yandex.intent.action.TV_CHANNEL` targeting
`com.yandex.tv.live/.playback.ChannelPlaybackActivity`, with `input_id`, a typed
`channel_uri` Parcelable and optional `current_category_id`. The stock player
retains responsibility for streams, authentication, DRM, EPG and subscriptions.
A bare rubricator intent is insufficient: the stock parser also requires input
and channel URI. No provider writes or private network API are used.

The First Channel launched from Aerial and its stock media session reported
PLAYING without an error. The owner confirmed picture, sound and Home return.
Unavailable providers show an error; empty catalogs/favorites show an empty state.

Do not disable MediaTek TV services, TV boot, Yandex core/platform services,
keyboard or WebView. The latter is the only valid WebView on the tested firmware.
Cold boot with the disabled stock launcher has not yet been verified.

Release 1.48-gaia.41 (versionCode 91) installs over .40 without clearing data.
Parser and playback intent tests supplement the existing suite. HDMI routing,
Direct Boot and the default HOME configuration are unchanged by the browser.

Build environment note: the old output folder contained duplicate generated
`gradleResValues N.xml` resources. This build used clean temporary Gradle cache
and build outputs rather than modifying those generated copies or source resources.

## Channel cards (1.48-gaia.42)

The browser now displays channel artwork, names without numeric prefixes, and
current EPG titles/descriptions when supplied by the provider. The read-only
program query uses the same redirect provider with a current time window;
243 current programs and 246 artwork references were observed on this firmware.
EPG refresh runs once per minute while the screen is started, and on return from
playback. Missing EPG does not prevent browsing or playback.

Artwork accepts HTTPS URLs from the known channel metadata fields, preferring
logo_with_bg_url, then channel_thumbnail, then icon_url. Images use the bounded
shared image cache and fit without cropping; only composed tiles request images.
A separate focusable star toggles the same local favorites. All five locales
include a fallback for unavailable program information. Playback routing is unchanged.

## Three-column browser (1.48-gaia.43)

Channel cards use three columns. A short OK opens the stock player; holding OK
opens a focused context menu to add/remove the channel from local favorites.
The favorite star is now a status mark, not a separate grid control. Back dismisses
the menu. The optional Pressable long-click handler cancels on focus loss/disposal
and consumes the original release, so it cannot activate the newly opened menu.
Removing an item from the Favorites group returns focus to the screen's Back
control, including when removing the final item.

## Home responsiveness (1.48-gaia.44)

The resume path called reloadIfPackagesChanged and then unconditionally reload,
cancelling a cold load already started by the ViewModel and rebuilding the catalog
on every warm return. The unconditional call is removed. Package broadcasts,
Android's change counter, configuration changes and custom icon observers retain
refresh behavior; failed initial loads can retry. The package counter is captured
before enumeration and only acknowledged after successful publication.
Network callbacks are coalesced for 100 ms and read on IO; the default HOME check
also runs off the UI thread. System refreshes cancel their obsolete predecessor.
No visual effects or user settings were changed.

On-device ADB navigation (same six left/right presses, 103 rendered frames):
.43: 5 janky frames (4.85%), p50 7 ms, p95 15 ms, p99 81 ms.
.44: 2 janky frames (1.94%), p50 7 ms, p95 9 ms, p99 89 ms.
These are short samples, not evidence that every stall is eliminated. The catalog
retention diagnostic confirms warm returns without enumeration. First launch
after an APK update still rebuilds version-keyed tile caches (about 2.4 seconds
of background catalog work here); warm/cold results must not be conflated.
129 unit tests and the release build passed. Physical remote responsiveness
remains an owner check.

## Platform identification (read-only audit)

The software stack identifies this device as Cultraview + MediaTek/MStar + YaOS,
not the CVTE stack used by the LVG GaiaOS port:

- Brand HI, manufacturer BBK, model VHIX-65U169MSY; Android 11 / API 30.
- ro.product.board=m7332_eu; ro.product.boardid=7.T9632AH00000.000;
  ro.yndx.build.board=mt9632_11; ro.yndx.build.platform=cv. The short `cv`
  property alone is not evidence for CVTE.
- Factory APK: /system/app/CtvFactoryMenu/CtvFactoryMenu.apk, package
  mediatek.factorymenu.ui, DesignMenuActivity.
- Local inspection of this APK found Cultraview URI/configuration names and
  com.mediatek.twoworlds.tv.MtkTvConfig / MtkTvFactoryMode dependencies.
- Installed components include com.ctv.android.tvboot, com.ctv.autotest.helper,
  com.cultraview.screensaver and com.cultraview.newfilemanager.
- Native factory/TV HALs use vendor.mediatek.tv.mtktvfactory and mtktvapi;
  physical sources use com.mediatek.tvinput and the MediaTek TV center.
- No CVTE-named package was found, including the uninstalled-package listing.
  This is not an exhaustive provenance audit of every binary or physical board.

A shared remote shortcut for the factory menu does not establish API compatibility.
Treat the device as a separate backend. YaOS/Efir channels are an independent
capability, not a synonym for either CVTE or MediaTek hardware. Proprietary APKs
and decompiled sources remain local and are not part of this repository.

For a future Aerial UI identity, keep the installed application ID and signing key
for in-place upgrades. Separate capability adapters for CVTE, MediaTek/Cultraview
and YaOS content, retaining existing upstream vendor fallbacks. Product display
name and repository naming can evolve without changing the installed package.

## Initial parity probe (1.48-gaia.46)

Protection now uses ProtectedPackages shared by app-management and task-close
policies. It adds MediaTek/Cultraview boot/input dependencies and explicit YaOS
platform, playback, WebView, keyboard and updater dependencies. It changes no
package state. Ordinary Kinopoisk and Yandex Music remain manageable.

A fixed read-only MediaTek probe in tools/mediatek-probe uses the installed
Yandex Settings implementation and firmware JNI; no proprietary code is bundled.
Under shell UID 2000 / API30, ordinary PathClassLoader failed to load libdl_android
through vndksupport. ClassLoaderFactory with a shared namespace in the disposable
shell process allowed reads without root or changes to linker/system policy.

Observed raw results: picture mode 9, brightness 50, contrast 45, saturation 45,
sharpness 40, hue 50, speaker balance 0, output port 1, SPDIF 2. Picture scalar
packed ranges decoded to 0..100; balance packed range to -50..50. These are
current-context observations, not proof of enum semantics or writable controls.
CEC getters and the candidate fusion backlight key returned 0 with range 0;
treat support as unresolved. Do not display those as confirmed disabled states.
No settings writes, root, reboot or package disabling were performed.

The same getters were explicitly tested from the installed Aerial UID 10073:
ordinary app loading fails with the same native namespace error. A shell helper
with its own shared linker namespace is therefore the verified read path; no
claim is made that the app can grant itself a shared privileged namespace.
The final .46 build is installed on YaOS with preserved CE inode and HOME.
132 application tests and the standalone task-close policy test passed. Aerial
MainActivity resumed after the probe. No reboot or physical HDMI retest occurred.
The diagnostic receiver is opt-in and permission guarded; no polling is added.
Next: a bounded, authenticated, getter-only helper endpoint with timeouts and
capability flags before any UI controls or setter implementation.

## On-demand read backend (1.48-gaia.47)

MediaTekAvReader runs the fixed getter entry point from our installed APK through
loopback ADB. R8 keeps the entry class, and the command uses only PackageManager
paths with shell quoting. The helper has an 8-second process timeout and a
12-second ADB exchange deadline; it never listens for incoming clients. There is
no boot/resume polling. Explicit page opening and Refresh request a snapshot.
Failures clear old values, and the UI provides no MediaTek write callbacks.

The YaOS picture, speaker, CEC and output pages use this read-only branch; CVTE
keeps the existing backend. SDK30 + installed MediaTek input + system Yandex
Settings gate the branch. Picture scalar values and signed balance are validated
against packed vendor ranges. Unmapped enums, backlight and CEC remain unconfirmed
rather than showing false Off values. No claim of general MediaTek compatibility.

The installed .47 Aerial successfully invoked the helper under shell UID2000.
UI inspection confirmed brightness 50, contrast 45, saturation 45, sharpness 40,
hue 50 and unconfirmed backlight on the native picture settings page.
134 unit tests passed. Physical remote verification is still an owner check.
No system settings, package state or boot configuration changed.

## Scalar writes and undo (1.48-gaia.48)

Only brightness, contrast, saturation, sharpness, hue (0..100) and speaker balance
(-50..50) are writable. The shared MediaTekWritePolicy rejects other keys, stale
values/ranges and changed input, picture mode or audio route. The helper rechecks
in its own process before setConfigValue(-1,key,value,3), requires return 0 and
read-back. No all-elements, CEC, backlight, profile or output setter is exposed.

App preferences commit before/old/new before dispatch. A failed/uncertain write
retains an unresolved rollback and prevents another write. Successful writes keep
last-change Undo across app recreation; Undo checks context and refuses values
changed externally, then reads back before clearing the record. External input/HDR
races are not fully atomic: HDR sub-contexts still need dedicated validation.

On the test YaOS, all six getters/setters completed one-step changes and immediate
undo, restoring picture values 50/45/45/40/50 and balance 0. An initial cold read
exceeded the legacy socket's 4-second idle timeout; the custom 12-second transport
now waits up to its own deadline (legacy callers retain their old timeout).
136 tests passed including stale input/profile/route/range/value refusal cases.
UI navigation changed brightness 50 to 55, displayed Undo, and restored 50 via
that button. During verification the controls stay mounted and reject new input,
so slider focus is not discarded. Final APK installed; no root/reboot/system
package state or CEC changes. Physical remote/audio/HDMI-HDR verification remains
an owner check, beyond the tested app/API roundtrips.

## Backlight and CEC support audit (read-only)

The correct readable backlight key is g_disp__disp_back_light: observed value100,
range0..100, visible/enabled. MediaTek visibility/enabled flags use0 for success,
1 for hidden/disabled. The previous fusion backlight key is hidden/disabled.
Adaptive backlight is readable with range0..2, but enum labels are unconfirmed.
Sysfs backlight only exposes max1; Settings.System brightness102 is a different
store and must not be presented as the MediaTek percentage.

Android hdmi_control runtime reports CEC enabled, auto-device-off false,
auto-wakeup true, CEC volume-control true and ARC not established. Vendor g_cec
keys returning0 are not authoritative. Missing persisted auto-wakeup settings
also do not mean disabled. ARC port capability, feature enable and session state
must be separate. The device list is cached, not proof of live connectivity.

Direct CEC Binder getters deny shell UID2000 without HDMI_CEC permission;
read-only dumpsys succeeds. The next read UI can parse a bounded, explicit dump
with Unknown for missing fields and no full-history logging. No root, grants,
CEC traffic, settings writes or firmware changes were used in this audit.
Backlight remains outside the scalar write allowlist pending a separate test.

## Read-only backlight/CEC UI (1.48-gaia.49)

The picture page now reads g_disp__disp_back_light with its visibility/enabled
metadata and displays the percentage as a non-editable value. The six-scalar
write allowlist is unchanged and rejects backlight explicitly in policy tests.
CEC uses a separate on-demand, bounded dumpsys command through local ADB; sed
removes history before returning output to Aerial. YaosCecState accepts only
known boolean fields and ARC-capable port records. Missing, malformed, duplicate
or conflicting booleans remain Unknown, never Off. No device cache is treated
as a live connection. Refresh is explicit; there is no polling or CEC traffic.

The .49 build was installed in place on YaOS. The app's own diagnostic returned
backlight100 with range0..100 and visible/enabled0, plus CEC enabled, auto-off
false, auto-wake true, CEC volume true, system audio false, ARC established false
and ARC-capable port2. UI inspection confirmed the CEC fields match the runtime
snapshot. 138 unit tests passed; signature remained unchanged. No root,
permission grants, reboot or backlight/CEC writes occurred. CVTE keeps its backend.


## Rootless backlight and CEC writes (1.48-gaia.50)

The scalar allowlist now includes g_disp__disp_back_light, limited to 5..100.
Both settings and Control Center use the same context guards, verified setter
and persistent Undo journal. Hidden/disabled metadata blocks the slider.

CEC uses four fixed Settings.Global keys observed by Android HdmiControlService:
hdmi_control_enabled, hdmi_control_auto_device_off_enabled,
hdmi_control_auto_wakeup_enabled and hdmi_control_volume_control_enabled.
Each confirmed write checks stored and runtime state, commits a separate journal,
uses a guarded shell write through authorized local ADB, then verifies both views.
Originally absent keys are restored by deletion. Pending writes block subsequent
changes until resolved. Active or unknown ARC/system audio blocks edits while CEC
is enabled; re-enabling the disabled master remains possible for rollback.
No root, HDMI_CEC grant, vendor g_cec writes, ARC setter or reboot is required.

On the verified TV, all four CEC changes and exact stored/runtime restores passed.
Backlight changed 100 to 99 and returned to 100. All original values were restored.
142 unit tests passed. This confirms parameter state, not physical HDMI device
power/wake/volume behavior; that requires a compatible connected device. Other
firmware, HDR/source transitions and active ARC remain outside this validation.


## Native night backlight (1.48-gaia.52)

The optional screensaver night mode uses the same fixed native backlight setter,
without replacing the manual AV Undo. The target is 5 on the verified 0..100 scale.
A separate night_backlight/restore journal commits the original value, context,
last verified value and next intended value before dispatch. Both last/next are
accepted during recovery to handle an interrupted write, never arbitrary values.
Input/profile/route guards remain enforced. Pending recovery blocks a new night
session. A settings action retries recovery after returning to the original context.

A serialized IO coroutine writes only changed integer levels over 5/15/30/60
minutes, then waits without polling. Ordinary cancellation restores in steps of
at most three units. Process restart recovers the saved original before a new
session. No guaranteed restoration while Aerial is not running is implied.
External changes or a changed input/profile must be resolved rather than overwritten.
The warm SurfaceView-safe overlay remains independent of the hardware backlight;
no screenshots, image blur, new decoder or system colour-temperature setting is used.

Hardware validation: 145 tests passed. A full five-minute run was independently
read at 92, 67, 8 and 5; Back restored the original 100. A second run was
force-stopped at 95 and relaunched; recovery read 100, with HOME unchanged.
The native layer is invisible to screenshots, so physical warmth/brightness and
perceived smoothness still require owner acceptance. No reboot was performed.

Owner correction: no visible dimming was observed on the YaOS TV. The above
roundtrips prove configuration/readback and recovery only, not physical panel
brightness. Stock TvSettingsPlus uses Global picture_backlight observed by
TVMenuSettingsService; that system process delegates to the same native key.
A shell-versus-system UID difference is a hypothesis, not an established cause.
Sysfs 1/1 cannot independently verify percentage changes.

## Stock service night route correction (.53)

A bounded comparison through Global picture_backlight produced visible dimming
confirmed by the owner, unlike direct shell-native writes. The stock system
TVMenuSettingsService observes this key and delegates to the native config API.
The observed difference does not establish precisely where UID/driver handling
diverges. Night mode now uses this fixed service route via YaosNightPanel, checking
both stored and native values, active context and adaptive-backlight-off state.
The journal original is the stock setting (99 in the test), not stale native cache.
An absent stock key is rejected rather than created. Legacy .52 journals use
their old native-only recovery before a new session. Manual scalar controls
retain their existing backend; this correction is scoped to night mode.

.53 validation: 145 tests passed; in-place release signature preserved. New app
session recorded stock baseline 74, reduced it, then restored 74 on Back.
The earlier standalone 5% stock-service test was visibly confirmed by the owner.
The owner subsequently confirmed the full night mode and return on Back work.
