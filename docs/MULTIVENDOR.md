# Aerial UI: platforms and compatibility

Aerial UI is the product name. This is an in-place evolution of Aerial Gaia,
not a new Android application. Keep applicationId app.orchard.tv.gaia, the local
release signing key, namespace app.orchard.tv, component class names, account type,
provider authorities, preference filenames/keys and source IDs. Keep -Pgaia and
the -gaia.N version suffix for build/update compatibility. The repository and
release feed remain alexkrass76/aerial-gaia until an explicit hosting migration.
Never bundle local signing material or proprietary vendor APKs.

## Layers

- Shared UI: Home, settings, task switcher, channel grid, wallpapers, screensaver,
  weather, customization and update checks. No hardware selection by product name.
- platform/TvPlatforms.kt: ordered hardware adapters. CVTE delegates unchanged
  to CvteSources (Droidlogic physical input + exported CVTE TIF player). MediaTek
  uses the verified physical input + MediaTek TV Center route on YaOS firmware.
  Null leaves upstream Xiaomi/TCL/Konka/other and generic routes intact.
- platform/YaosContent.kt: optional Yandex channel content, independent of board.
  Requires an enabled/exported stock player. ChannelsActivity owns presentation
  and private favorites; YaosChannels reads the provider and delegates playback.
- Existing privileged settings/volume/task bridge remains capability checked.
  CVTE-specific settings and global UI integrations are not MediaTek APIs. No
  unverified MediaTek picture/audio writes are introduced by the rename.

## Regression contract

Preserve DirectBootHomeActivity, COMMON_HOME, the TV-player Home Guard exclusions,
CVTE HDMI sourceId/geometry extras, MediaTek physical passthrough IDs, the configured
AirPlay receiver and VPN/LAN behavior. Do not reset settings or re-enable disabled
packages. Do not change user-chosen receiver names as part of branding.

Validation: release signature and package identity, existing Direct Boot tests,
HDMI route and YaOS capability tests, upgrades without clearing data on both
available TVs. Physical HDMI and AirPlay require owner verification; never reboot
without separate authorization. A platform adapter is not a promise that every
firmware with the same chipset supports every settings API.

## Verified migration build

1.48-gaia.45 / versionCode 95 upgraded LVG from .39 and YaOS from .44 in place.
Both kept their CE data directory identity and DirectBootHomeActivity default HOME.
Device diagnostics confirmed CVTE TIF routing on LVG, MediaTek TV Center routing
on YaOS, and the independent Yandex channel source on YaOS. 132 unit tests passed.
No reboot, package disable/enable, preference reset, repository rename or release
publication was performed. Physical feature verification remains an owner check.
