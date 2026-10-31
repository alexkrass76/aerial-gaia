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
