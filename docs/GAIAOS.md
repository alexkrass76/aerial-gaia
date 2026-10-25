# GaiaOS: установка, проверка и откат

Инструкция для проверенной конфигурации LVG 32LM57 / TP.T950ST.PB752. На других устройствах сначала исследуйте компоненты и сохраните исходное состояние. Никакие команды здесь не требуют удаления APK или очистки данных. Подключение по Ethernet особенно важно: при зависании ранней загрузки Wi-Fi может не запуститься. При переходе с Wi-Fi на кабель IP может измениться — проверьте DHCP-список роутера.

## 1. ADB и исходное состояние

На исследованном телевизоре ADB включали в Factory Menu CVTE: удерживать Menu примерно секунду, затем быстро ← ↑ ← ↑ Back; найти ADB ENABLE и включить. Вход в меню зависит от прошивки. Не трогайте SHIPPING INIT, BOARD INIT, настройки панели и SW UPGRADE.

Подставьте текущий адрес своего телевизора:

```sh
TV='TV_IP:5555'
adb connect "$TV"
adb devices -l
adb -s "$TV" shell id
adb -s "$TV" shell settings get secure enabled_accessibility_services
adb -s "$TV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s "$TV" shell dumpsys package com.cvte.tv.launcher.mars
```

Продолжайте только при статусе `device`. При `unauthorized` подтвердите отладку на телевизоре. Сохраните вывод локально; он может содержать персональные данные. Не публикуйте полные getprop, журналы, MAC, серийные номера или адреса сети.

## 2. Установить Gaia, сохранив Mars

Соберите standard release с `-Pgaia`, проверьте пакет и подпись через Android SDK apkanalyzer/apksigner. Для обновления требуется та же подпись, что у установленной Gaia.

```sh
adb -s "$TV" install -r app/build/outputs/apk/standard/release/app-standard-release.apk
adb -s "$TV" shell cmd package compile -m speed -f app.orchard.tv.gaia
adb -s "$TV" shell pm grant app.orchard.tv.gaia android.permission.WRITE_SECURE_SETTINGS
adb -s "$TV" shell appops set app.orchard.tv.gaia SYSTEM_ALERT_WINDOW allow
adb -s "$TV" shell cmd notification allow_listener app.orchard.tv.gaia/app.orchard.tv.guard.RevivalListener
adb -s "$TV" shell am start -n app.orchard.tv.gaia/app.orchard.tv.MainActivity
```

WRITE_SECURE_SETTINGS позволяет приложению поддерживать Home Guard; уведомления используются механизмом восстановления сервиса. Сохраните исходные разрешения перед выдачей. При ошибке подписи остановитесь, не удаляйте старую Gaia.

Включите Home Guard через настройки приложения. Проверьте `enabled_accessibility_services`: сервис Gaia должен присутствовать, остальные сервисы должны сохраниться. Не заменяйте весь список одной строкой. Если работает оригинальный Aerial, не оставляйте два конкурирующих Home Guard: выключите его Guard через настройки, сохранив данные.

## 3. Назначить ранний HOME

```sh
adb -s "$TV" shell cmd package set-home-activity --user 0 app.orchard.tv.gaia/app.orchard.tv.DirectBootHomeActivity
adb -s "$TV" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.COMMON_HOME
```

Если появился выбор, выберите **Aerial Gaia → Всегда**. На нашей прошивке одного set-home-activity было недостаточно для COMMON_HOME. Если Guard закрывает выбор, временно выключите только Guard Gaia, выберите «Всегда» и включите обратно. Подождите несколько секунд для сохранения.

```sh
adb -s "$TV" shell cmd package resolve-activity --brief --query-flags 524288 -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.COMMON_HOME
```

Ожидаемый компонент: `app.orchard.tv.gaia/app.orchard.tv.DirectBootHomeActivity`. Не продолжайте при ResolverActivity, Mars или отсутствии результата.

Сначала перезагрузите **с включённым Mars**, после отдельного согласия владельца. Не нажимайте Home до появления рабочего стола. Проверьте ранний старт по журналу, затем физические Home, стрелки, OK, Back, настройки и HDMI1 с изображением/звуком.

## 4. Необязательное отключение Mars

Это отдельный эксперимент, не требование для установки Aerial. Подготовьте Ethernet ADB и команды отката ниже. Сначала можно проверить `pm disable-user`; однако на исследованной прошивке это состояние сбросилось после перезагрузки. Постоянное обратимое скрытие потребовало stock userdebug `adb root`:

```sh
adb -s "$TV" root
adb connect "$TV"
adb -s "$TV" shell id
```

Продолжайте только при uid=0. Если root недоступен, остановитесь; не пытайтесь менять системный раздел или прошивать устройство.

```sh
adb -s "$TV" shell pm disable-user --user 0 com.cvte.tv.launcher.mars
adb -s "$TV" shell pm hide --user 0 com.cvte.tv.launcher.mars
adb -s "$TV" shell dumpsys package com.cvte.tv.launcher.mars
adb -s "$TV" unroot
adb connect "$TV"
```

Проверьте `installed=true hidden=true enabled=3` у активного пакета. APK и данные остаются на месте; Mars skin не меняйте. После согласия владельца:

```sh
adb -s "$TV" reboot
```

## 5. Приёмка после перезагрузки

После возвращения Ethernet ADB:

```sh
adb connect "$TV"
adb -s "$TV" shell getprop sys.boot_completed
adb -s "$TV" shell getprop service.bootanim.exit
adb -s "$TV" shell cmd package resolve-activity --brief --query-flags 524288 -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.COMMON_HOME
adb -s "$TV" shell dumpsys window | grep mCurrentFocus
adb -s "$TV" shell dumpsys package com.cvte.tv.launcher.mars
adb -s "$TV" shell pidof com.cvte.tv.launcher.mars
adb -s "$TV" logcat -d -s AerialDirectBoot:I '*:S'
```

Ожидается: оба свойства равны 1; HOME — DirectBootHomeActivity; фокус — MainActivity Gaia; Mars hidden=true/ enabled=3, pidof пустой (ненулевой код выхода здесь нормален). В журнале сначала `created; userUnlocked=false`, затем `user unlocked; opening MainActivity`. Одна только видимая Gaia после нажатия Home не подтверждает правильную раннюю загрузку.

Физически проверьте Home, стрелки, OK, Back, HDMI1 (звук и видео), настройки и AirPlay. На нашем устройстве эти проверки пройдены после перезагрузки без Mars. В ранних проверках AirPlay обеспечивал MAXHUB. В текущей конфигурации используется отдельный `io.github.jqssun.airplay`: MAXHUB скрыт/отключён, `com.screenshare.platform` пока оставлен включённым. Приёмник не входит в APK лаунчера. Не отключайте нативные TV API/settings, Bluetooth и сетевые службы ради уменьшения числа процессов.

## 6. Откат при зависании загрузки

Подключитесь по Ethernet. При скрытом Mars нужно **и снять скрытие, и включить пакет**:

```sh
adb connect "$TV"
adb -s "$TV" root
adb connect "$TV"
adb -s "$TV" shell pm unhide --user 0 com.cvte.tv.launcher.mars
adb -s "$TV" shell pm enable --user 0 com.cvte.tv.launcher.mars
adb -s "$TV" shell am start -n com.cvte.tv.launcher.mars/.ui.home.HomeActivity
adb -s "$TV" unroot
adb connect "$TV"
```

Для полного возврата штатного Home временно выключите Guard Gaia через настройки (не затрагивая другие службы), назначьте Mars и при запросе выберите «Всегда»:

```sh
adb -s "$TV" shell cmd package set-home-activity --user 0 com.cvte.tv.launcher.mars/.ui.home.HomeActivity
```

Проверьте Home до следующей перезагрузки. Aerial и его данные можно оставить установленными. Если ADB не появляется даже по Ethernet, эти команды выполнить невозможно; не подменяйте откат случайной прошивкой от похожей платы.

## Поддержка форка

Сохраняйте Direct Boot Activity, COMMON_HOME, CVTE HDMI route, исключение TV-плеера в Guard и отдельный revival account при обновлении upstream. Повышайте versionCode для новых релизов и сохраняйте ключ подписи. Встроенные upstream-уведомления не означают наличие совместимой Gaia-сборки; публичный автообновлятор Gaia пока не настроен.

## VPN indicator (1.41-gaia.4)

The home status pill and Control Center show VPN when Android reports the app's active network as a VPN. Wi-Fi/Ethernet stays visible alongside it. This reports a tunnel, not a guarantee of handshake, Internet access or coverage of other apps. The launcher does not enable VPN or change routing.

Validated on the GaiaOS device: release build, in-place signed update, no VPN badge with tunnel down, badge on the home screen and Control Center with WireGuard up. Wi-Fi reconnect after boot and VPN autostart remain separate unresolved device issues.

## Upstream 1.47 integration (1.47-gaia.5)

Merged the v1.47 release (versionCode 55), including phone layout management, guard fixes and screensaver updates. Retained the separate Gaia package/signature, DirectBootHomeActivity as HOME + COMMON_HOME, CVTE HDMI route before upstream fallbacks, CVTE TV-player exclusion and the VPN status badge. MainActivity remains credential-storage-only in this port; upstream's locked-wallpaper bootstrap does not replace the verified Gaia bootstrap.

The AirPlay receiver is a separate app; updating this launcher does not replace it or change WireGuard, Wi-Fi, Mars or MAXHUB state. Boot and physical remote/input checks must be repeated for this version before treating it as fully verified.

Validation for 1.47-gaia.5: release build and all 25 upstream unit tests passed. Signing certificate matches the previously installed Gaia build. Physical Home was subsequently confirmed; HDMI picture/audio could not be checked because no source device was connected. For reboot acceptance see 1.47-gaia.6 below.

## Styled early screen (1.47-gaia.6)

DirectBootHomeActivity now uses a framework Canvas view with an APK-backed Inter font, static teal/blue/violet gradients, a translucent rounded card and localized loading text. No Compose, wallpaper/settings reads, network requests or repeating animation are initialized. The unlock transition and HOME declarations are unchanged. Release build and 27 tests passed, including waiting for unlock and opening MainActivity once; the rendered preview was inspected. Cold boot verified on the GaiaOS TV: the owner saw the styled card, the journal recorded userUnlocked=false then the transition to MainActivity 5.66 seconds later, and Aerial was foreground without an injected Home. Mars and MAXHUB remained hidden/disabled; the AirPlay receiver advertised Aerial Gaia. The owner reported Wi-Fi reconnected automatically about a minute after startup. HDMI signal/audio remain untested on this update because no source device was connected.

## Weather city localization (1.47-gaia.7)

The geocoder now uses the device language instead of hard-coded Chinese. The original search query and label language are saved for future relocalization. Cached labels are updated only when the returned location is within 1 km of the saved coordinates; the migration never changes forecast coordinates. Unresolvable old translated labels must be reselected once using the phone settings page. Failed localization leaves the existing label and weather available.

Release build and 29 tests passed. Installed over gaia.6 with the same signature and without clearing data; the existing chosen city was reselected through the launcher settings and returned in Russian. No system-package, VPN, wallpaper or boot changes.

## Phone settings LAN address (1.47-gaia.8)

PhoneServer now selects an IPv4 address from a physical Wi-Fi/Ethernet network instead of the default Internet network. VPN networks are excluded even when their capabilities include an underlying Wi-Fi transport. The default physical network is preferred; otherwise Ethernet takes priority. Internet validation is not required. No usable LAN IPv4 address means no phone URL, rather than a VPN-only URL.

Release build and 33 tests passed. On the GaiaOS TV with WireGuard active, the displayed address and decoded QR used the Wi-Fi LAN; the exact QR URL returned HTTP 200 from a computer on that LAN. VPN settings and routing were not changed. Tested as an in-place signed update without a reboot.

## Quiet screensaver and on-demand information (1.47-gaia.9)

Aerial video descriptions now use Apple's Russian/Ukrainian timed metadata for all 114 catalog films (352 entries per locale). `tools/import-aerial-captions.py` reproduces the metadata from the official resources-16 archive; video files are not bundled. English/Chinese remain available, with English fallback for other locales.

Screensaver information starts hidden. Any D-pad direction reveals the clock/date/weather card and the separate bottom description for 10 seconds; another direction extends that period. Direction keys no longer skip films. Back exits; other delivered keys and touch do not dismiss the screensaver. The same input policy is used by the launcher and DreamService; firmware-level power/input changes are outside its control. The home clock fades away when a live wallpaper continues into the screensaver.

Clock, date and weather are drawn directly over the video with soft text shadows and no panel, border or blur. Video-plane PixelCopy was removed after both repeated and single captures produced visible green artifacts on GaiaOS. Reusing the dock's wallpaper-based glass was stable and its controls were confirmed by the owner, but the unrelated wallpaper colors were visually distracting; the owner requested the unboxed layout instead.

Release build and 37 tests cover key handling, timeout/extension and metadata coverage. Installed in place with the existing signature. Owner confirmed normal video, information timeout and Back-to-home on the glass revision; ADB screenshots of the video plane contain vendor artifacts and cannot establish video quality. The owner also confirmed the final unboxed revision looks good on the TV. No reboot or system-package changes.


## Experimental multitasking (1.47-gaia.10)

Double Home opens existing task cards; arrows choose, OK resumes and Back closes.
A shell-UID helper observes verified Home input mappings without consuming keys.
The launcher can start the bundled helper through loopback ADB after user unlock;
this opt-in setting defaults off and changes no system boot scripts or ADB security
settings. See `tools/task-bridge/README.md` for architecture and enable/status commands.

Release build and 45 unit tests passed. Autonomous helper startup and recovery
were verified, then an authorized reboot completed with Direct Boot and helper
attachment. The owner confirmed double Home, task return and ordinary Home after
reboot, but multitasking became available only after Wi-Fi connected. Startup
without networking remains unverified. HDMI signal and AirPlay were not retested
in this round. Helper plus input reader previously measured about 44 MiB PSS.

To disable future automatic helper launches without changing Home or HDMI:

```sh
adb shell content call --uri content://app.orchard.tv.gaia.tasks --method set_autostart --arg false
```

This leaves a running helper alive until it is stopped or the TV reboots.
The feature is opt-in; see the task bridge guide for setup and current limitations.


## Upstream 1.48 integration (1.48-gaia.11)

Merged v1.48 with staged app artwork loading, background font/tile preparation,
Control Center warmup, memory-pressure cache trimming, animation and phone-page
fixes, and the upstream guard compatibility changes. Gaia initializes an unset
effects preference to Full, preserving the existing dock appearance on low-memory
TVs; explicit Auto and Lite choices remain available and are never overwritten.

Preserved Direct Boot HOME/COMMON_HOME, CVTE HDMI routing and input-player guard
exclusion, separate package/signing identity, VPN badge, physical LAN address for
phone settings, weather localization, and on-demand unboxed screensaver info.
Task switching retains its shell helper and dock tiles; temporary upstream artwork
placeholders fall back to app icons instead of remaining blank in the switcher.

Validation: signed standard release built successfully and 45 unit tests passed.
Installed in place; launched before speed compilation. On-device diagnostics
confirmed Full/lite=false on the 958 MiB low-RAM device. Home, compact Control
Center, weather, VPN badge and physical-LAN phone URL were inspected. Task tiles
retained their dock lighting; OK resumed kino.watch and Home returned to Gaia.
No AndroidRuntime errors were present in the sampled log. Physical double Home
acceptance, a new reboot, HDMI signal and AirPlay remain unverified for this build.
The first interactive frame sample is not a controlled before/after benchmark;
no device-specific performance improvement is claimed yet.


## Selected community features (1.48-gaia.12)

Adapted upstream PRs [#436](https://github.com/pengchujin/aerial-tv/pull/436),
[#400](https://github.com/pengchujin/aerial-tv/pull/400),
[#401](https://github.com/pengchujin/aerial-tv/pull/401),
[#364](https://github.com/pengchujin/aerial-tv/pull/364) (including #296/#348), and
[#356](https://github.com/pengchujin/aerial-tv/pull/356) (including #233).
The port takes their app changes, not upstream installation scripts.

- Wi-Fi, Ethernet and VPN are independent statuses. A tunnel indicator does not
  guarantee Internet access. Physical LAN selection for the phone page is unchanged.
- Bluetooth opens the firmware settings via resolved intents; the checked Gaia
  firmware resolves the standard Bluetooth action to CVTE Settings.
- Personal photos no longer receive the generic photo caption. Gaia's directional
  information overlay, 10-second timeout, weather and Back-only exit remain.
- Optional app content shelf: Off by default on Gaia; On Up / Always available in
  Personalize. Requires READ_TV_LISTINGS and a provider on Android 8+. Apps without
  published programs keep the normal wallpaper. Card images are capped at 4 MiB,
  backdrops at two pairs; caches are released on memory pressure. Programs remain
  scoped to the publishing app when opened. Existing dock focus visuals are kept.
- Optional App Permissions: Off by default. Gaia uses its own app-info sheet from
  the tile menu; exported settings aliases activate only for missing system pages.
  The aliases use the class namespace, and task affinity uses the Gaia application ID.
  Operations reuse LocalAdbTransport, require UID 2000, fail closed on AUTH and never
  generate keys or modify ADB configuration. Only fixed commands with validated
  names run, for user 0, gated by the preference and parental PIN.
- System/updated-system apps, Aerial, CVTE/Droidlogic, WireGuard and AirPlay services
  are protected from app-management mutations. Clear data requires a second press;
  it really removes app accounts/data, not just cache. User apps should be tested
  with a disposable package before destructive operations are considered verified.

Disable App Content and App Permissions in Personalize to turn these additions off.
Neither changes the task helper autostart setting. No system packages are disabled,
removed or re-enabled by this update. Direct Boot/COMMON_HOME, native CVTE inputs,
Guard's TV-player exclusion, signing identity and task helper remain unchanged.

Validation: clean standard release build and 83 unit tests passed. Installed in place
with the existing signature and speed-compiled after launch. The task helper reattached.
Bluetooth opened CVTE Settings and the paired remote remained connected; app info
rendered for Kinopoisk. On-device network summary showed Wi-Fi and VPN connected,
Ethernet disconnected. No runtime crashes were found in the sampled log.

For this TV, READ_TV_LISTINGS was granted (absent before this update) and the shelf was
set to On Up. Kinopoisk, kino.watch, VK Video and Spotify published previews. kino.watch
cards and channel tabs rendered, OK opened its InfoActivity, and Home returned to Gaia.
A single post-use measurement was about 83 MiB PSS, excluding the task helper; this is
not a long-term memory benchmark. App management remains off; permission mutation,
force stop and data clearing have not been exercised on user apps. The owner confirmed physical remote acceptance: double Home, task selection, ordinary
Home, shelf navigation and Back-to-tiles. Boot/HDMI signal/AirPlay have not been
retested in this round.

To revoke the newly added shelf permission after turning App Content off:

```sh
adb shell pm revoke app.orchard.tv.gaia android.permission.READ_TV_LISTINGS
```

The previous APK was saved locally before installation. No reboot or package removal
was performed. Reinstalling an older version may require a build with a higher version
code; do not uninstall or clear data to bypass a downgrade restriction.


## Unified settings, first stage (1.48-gaia.13)

The Gaia Control Center gear opens an in-launcher settings surface with Network,
Bluetooth, Apps and About this TV. Other application IDs retain upstream routing.
Network displays independent transport/VPN states and the physical LAN IP. Memory,
storage, versions and enabled installed apps are read off the UI thread and refreshed
on resume. No persistent polling or shell commands are introduced for this screen.

App Info reuses the existing opt-in management and protected-package policy; Back
returns to the application list and restores the selected row. Settings entry retains
the parental PIN gate. No privileges or permissions are added by this version.

Wi-Fi setup, Bluetooth pairing and Ethernet configuration still open firmware pages.
CVTE wired settings are resolved through com.cvte.intent.action.WIRED_NETWORK;
other firmware keeps existing fallbacks. A clearly labelled TV settings entry retains
access to picture, sound and unported controls. The screen is not yet a replacement
for CVTE services and does not disable or remove them. WireGuard opens its own app;
tunnel configuration and toggling are not implemented in Aerial.

Validation: release build and all 83 unit tests passed. Installed over the previous
version with the existing signature and speed-compiled. Network, Bluetooth, installed
apps and About pages rendered on the TV. Ethernet opened the native wired page;
Back restored the network page and focus. App Info opened for a protected component,
and Back restored the selected app. Bluetooth opened the native page with the remote
still connected. The task helper reported connected with autostart enabled; no
AndroidRuntime errors appeared in the sampled log.

Injected Home returned from native Bluetooth to the still-open Gaia settings page,
rather than dismissing it to the desktop. Physical Home/double Home and remote
navigation acceptance remain pending. No connection settings, permissions or app
state were changed during this validation. Boot, HDMI signal and AirPlay were not
retested; this version does not change their implementations.


## Network policy correction (1.48-gaia.14)

The general cleartext HTTP allowance introduced for shelf artwork is removed.
The policy matches gaia.10: only the pre-existing named weather/location and
screensaver exceptions remain. HTTPS artwork is unaffected; plain-HTTP media-server
artwork outside those exceptions can fail to load. No TLS verification is bypassed.
This changes neither the local phone-settings server nor the ADB task transport.


## Settings: sound and app filtering (1.48-gaia.15, local candidate)

Sound reuses the Control Center volume slider and mute handling. Left/right adjusts
media volume; OK toggles mute. Bluetooth pairing and advanced TV sound controls
retain firmware fallbacks. On the tested CVTE firmware ACTION_SOUND_SETTINGS has
no handler, so the advanced entry opens the general TV settings. No output-device,
ARC, CEC or equalizer settings are written by Aerial.

The app list initially hides packages marked SYSTEM or UPDATED_SYSTEM_APP. A visible
toggle reveals them. Filtering does not change package state or the existing
protected-package policy. Its choice survives App Info navigation within the
launcher process. No permissions, background polling or upstream 1.49 code are added.

Validation: clean standard release build and all 83 unit tests passed. Installed
over gaia.14 and speed-compiled. On-device checks confirmed the Sound page,
D-pad focus, volume 8 -> 9 -> 8 and mute/unmute with OK. AudioManager confirmed
the original unmuted level was restored. The CVTE volume overlay still appears
on changes; this version does not suppress it. Both app filters rendered correctly
and the ordinary-app filter was restored. No AndroidRuntime errors were seen in
the sampled log. Physical remote acceptance, advanced sound navigation and reboot
remain untested in this round.


## App icons and action visibility (1.48-gaia.16, local candidate)

Visible settings rows and App Info load PackageManager icons on a worker thread,
rasterized to at most 128 x 128 pixels. There is no global icon cache or remote
image fetch. Stop and Uninstall remain visible but inactive for protected packages,
with an explanation; the protection policy is unchanged. Ordinary apps retain
force-stop and Android-confirmed uninstall. If local app management is off, a
mutation opens the existing opt-in dialog. Confirmation only enables management;
the original operation must be requested again. It never silently enables ADB or
executes the deferred operation. The parental lock is rechecked on confirmation.

Validation: release build and 83 unit tests passed, installed and speed-compiled
over gaia.15. Settings icons and the Kinopoisk card rendered on the TV. Stop,
Clear Data and Uninstall were visible. Uninstall opened Android confirmation;
Back cancelled it and the package remained installed. No force-stop, data clearing
or permission changes were performed; management opt-in and protected-card visuals
remain untested on-device. No AndroidRuntime errors appeared in the sampled log.

## Settings capability map (1.48-gaia.17, local candidate)

System preferences adds a read-only date, time-zone and language summary, plus an
allowlist of firmware pages for date/time, language, keyboard, accessibility,
accounts, display, storage, notifications and general options. Only installed,
enabled, exported system handlers accessible to the launcher UID are shown.
Targets are pinned to components and checked again before launch. Missing pages
are omitted, not replaced by the general settings screen. This does not grant
permissions, enable components, change settings or invoke arbitrary activities.

On this CVTE image `com.android.settings` is absent. LiteSettings declares separate
DATE_SETTINGS, LOCALE_SETTINGS and vendor InputMethodLanguage,
ACCESSIBILITY_SETTING and ACCOUNT_SETTINGS pages. An Activity Launcher cannot
restore Android pages that are absent from the firmware. A resolved page can still
have additional runtime checks; on-device opening must be tested separately.

Coverage and next migration steps:

| Area | Current Aerial implementation | Still to port or validate |
| --- | --- | --- |
| Network | Independent Wi-Fi/Ethernet/VPN state, physical LAN address | Wi-Fi selection/password, IP/DNS editor; pairing remains native |
| Bluetooth | State and native pairing entry | Device list, connect/disconnect and unpair |
| Sound | Media volume and mute | Output selection, equalizer, digital audio/ARC |
| Applications | Icons, filters, details, guarded stop/data clear, system-confirmed uninstall | Disabled packages and finer special-access controls |
| Time, language, keyboard | Summary and reachable firmware pages | Native Aerial editors with read-back and rollback |
| Accessibility/accounts | Reachable firmware pages | Individual controls and service state; preserve Home Guard |
| Picture, HDMI/CEC, power | Existing native fallback and Gaia source switching | Discover vendor APIs and per-input semantics before writes |
| Storage, notifications | Entries only if this image supplies accessible pages | Native views where firmware pages are missing |
| Factory/developer functions | Not exposed by this catalog | Evaluate individually; reset/flash/ADB toggles are not navigation shortcuts |

No CVTE services can be removed based on this UI inventory. A full visual and
functional replacement is still in progress. Upstream 1.49 remains unmerged.

Validation: standard release build and all 89 unit tests passed (including absent,
private, disabled, third-party and permission-protected page handling). Installed
over gaia.16 and speed-compiled. The System preferences screen rendered on TV;
Date/time, keyboard languages and Accessibility opened from the launcher UID.
Back returned to Aerial. The available list also contained Language, Accounts and
General; those three pages were not opened in this round. No sampled AndroidRuntime
errors. No values, accounts, accessibility services or package states were changed.
No reboot, physical remote, HDMI signal or AirPlay regression test was performed.

## Native Android preferences (1.48-gaia.18, local candidate)

The settings overlay no longer opens or refers to CVTE settings. Native controls
cover automatic network time, 12/24-hour format, system touch sounds, font scale,
three animation scales, and selection among already-enabled input methods.
A fixed typed allowlist prevents arbitrary setting writes. Each operation checks
the parental lock, rejects stale UI state, writes on a worker thread and reads back
the result. Failed operations refresh real state and show an error; concurrent
external values are not overwritten by an automatic rollback. Keyboard IDs must
still be in InputMethodManager's enabled list at the moment of selection.

WRITE_SECURE_SETTINGS was already granted on the test TV. WRITE_SETTINGS app-op
is additionally allowed for the native system-table controls. To undo this new
permission specifically, restore `appops set app.orchard.tv.gaia WRITE_SETTINGS default`.
This is a deployment prerequisite, not a permission silently escalated by the app.
No local shell commands or new permissions are introduced in the control backend.

This is NOT complete settings coverage. Network/Bluetooth currently show status
without native editors. The previous firmware shortcuts were removed at the owner's
request; Wi-Fi setup, pairing, manual time/time-zone, system language, keyboard
languages, accounts, picture/audio outputs, HDMI-CEC and power controls are still
pending. Existing firmware applications remain installed. The old control-center
picture fallback and app-specific special-access flows have not yet been migrated.

Validation: release build and 94 unit tests passed. The TV showed native Time,
Display and Keyboard screens. Clock format changed 24 -> 12 -> 24 from the Aerial
UI with SettingsProvider read-back. Window animation changed 1 -> 0 -> 0.5 -> 1;
its original numeric value was restored. Only the AOSP keyboard was enabled, so
switching between different IMEs remains untested. Automatic clock changes, font
scaling and sound toggles were not exercised on the TV. No reboot or connectivity
changes. The final APK only corrects default-value wording and removes a redundant
migration notice; it was rebuilt and reinstalled with all tests passing.

## Native connectivity lists (1.48-gaia.19, local candidate)

Control Center Network and Bluetooth now open Gaia pages. Wi-Fi lists saved
profiles, the currently associated profile and cached nearby SSIDs/signal levels.
Search requests a scan and refreshes once after three seconds; Refresh reads the
latest result. There is no permanent polling. Duplicate firmware profile IDs are
collapsed. Nearby networks are informational until the credential editor is built.
Selecting another saved profile opens a confirmation explaining the possible
connection interruption. The helper revalidates that ID against the saved list;
a successful request is not presented as proof of connection or Internet access.

The existing private, launcher-UID-only Binder bridge adds only three operations:
Wi-Fi snapshot, scan, and connect-by-saved-ID. Configured credentials never cross
Binder into Aerial. Wi-Fi service calls retain shell UID 2000 and shell package
attribution. A framework context is initialized lazily on the helper main thread;
no root request, arbitrary command or new network endpoint is added. This adapter
is specific to the tested Android 14 API signatures and fails closed elsewhere.
The diagnostic provider is still protected by DUMP and a shell/root UID check;
its device_snapshot method only reads Wi-Fi data. Do not publish its output.

Bluetooth reads bonded devices from the launcher process with BLUETOOTH_CONNECT.
This runtime permission was granted over ADB for the test installation. Without
it, the page reports failure rather than an empty device list. Pairing, unpairing,
audio-profile switching, new Wi-Fi credentials and Ethernet/IP editors remain
unimplemented. No adapter on/off switches are included in this iteration.

When upgrading the helper from pre-gaia.19, an already-running old helper must be
replaced once; changing APK alone does not replace its running dex. The test update
restarted only the identified Gaia helper process. At the next boot, existing
bootstrap uses the bundled new dex. No TV reboot was performed here.

Validation: release build and 96 unit tests passed, including snapshot Binder reply
and saved-network-ID/error propagation. Installed and speed-compiled on TV. Native
Wi-Fi screen and manual scan rendered; firmware duplicates were reduced to two
unique saved profiles. Bluetooth listed the paired remote via the app API. Existing
Wi-Fi, VPN and Bluetooth stayed enabled; no saved-network switch or pairing change
was executed. The updated helper attached with autostart enabled and no sampled
AndroidRuntime failures. Physical double Home acceptance is pending. The final
UI-only correction avoids showing unavailable while status is still loading.

## Wi-Fi credentials and Bluetooth pairing (1.48-gaia.20, uninstalled candidate)

The nearby Wi-Fi list distinguishes open/WPA2-PSK from unsupported WEP, enterprise,
WPA-only, pure SAE and OWE networks. WPA2/SAE transition networks can use WPA2.
The form supports a UTF-8 SSID of at most 32 bytes, WPA2 ASCII passphrases of
8–63 characters or a 64-digit hexadecimal key. Shared Java validation is compiled
into both launcher and helper. Credentials travel as typed private Binder fields,
never shell arguments, logs or launcher preferences. The editor masks input,
disables correction and temporarily sets FLAG_SECURE, preserving an existing flag.
It clears its field on submit/back and does not save state across recreation.

The helper rechecks scan results before applying a profile. New connections refuse
an already-saved SSID. Explicit Change network password targets an existing numeric
ID, verifies its SSID and WPA-PSK type, and copies its configuration before replacing
the key. It does not reveal the old key. A rejected enable request removes only a
profile newly created by this operation; existing profiles are not deleted. A later
authentication failure can leave the new profile saved, so its password can be
corrected through the explicit editor. Request acceptance does not mean successful
association or Internet access. Credentials and connection APIs still need live
validation before this candidate can be described as working on the TV.

Bluetooth discovery is user-triggered, lasts at most 20 seconds, and is cancelled
on page disposal/stop. It does not cancel a scan owned by another caller. Only a
device discovered on this page can be selected for pairing; already-bonded devices
are not offered for re-pairing. A second action initiates createBond. Android owns
any PIN/code confirmation dialog (its presentation depends on firmware); Aerial
neither bypasses nor automatically accepts it. No CVTE settings Activity is launched
by this code. Unpairing and Bluetooth audio-profile routing remain unimplemented.
BLUETOOTH_SCAN is an additional runtime permission required for discovery; location
is not derived from scan results. Existing bonds and adapter state are untouched.

Validation: standard release build and 107 unit tests passed, including Wi-Fi
security/length boundaries, credential Binder framing, discovery timeout/disposal,
and not cancelling another caller's scan. No network or bond was changed on the TV.
Physical acceptance was deferred by the owner. The candidate is saved locally;
TV remains on gaia.19 and the current helper is not restarted in this round.
When testing later, update the bundled helper as well and grant BLUETOOTH_SCAN;
old helpers do not support the new credential transaction.

## System captions (1.48-gaia.21, uninstalled candidate)

System preferences now includes a native Subtitles page: enable system captions,
choose text scale (0.75–2.0), and cycle through Android presets (white/black,
black/white, yellow/black, yellow/blue, unspecified and custom). The approximate
preview illustrates the four explicit presets. For custom/unspecified styles it
explains that rendering belongs to the player rather than inventing a preview.
These preferences apply only to players that honor Android CaptioningManager;
Aerial does not inject subtitles into HDMI, AirPlay or third-party apps.

Only three allowlisted Settings.Secure entries are written, using the existing
WRITE_SECURE_SETTINGS permission, parent lock, stale-state check and read-back.
Custom color/typeface/edge values and subtitle language are untouched. Selecting
Custom again restores use of those existing values; choosing another preset does
not delete them. No accessibility service is enabled or disabled. There is no
CVTE Activity redirect and no helper protocol change in this iteration.

Device acceptance is deferred: this candidate is not installed and no TV settings
were changed. TV remains on gaia.19. The pending gaia.20 Wi-Fi/Bluetooth operations
still need live validation and the helper/permission update documented above.

Validation: standard release build and 111 unit tests passed. New tests confirm
Android CaptioningManager sees the enabled flag and scale, preset changes preserve
custom colors, stale writes are rejected, and invalid values cannot be written.
Actual playback and remote navigation on GaiaOS remain unverified.

## Settings sheet styling (1.48-gaia.22, uninstalled candidate)

The native settings surface now follows Personalize: full-width bottom sheet,
360 dp tall on the 960×540 canvas, wallpaper glass with 30 dp top corners,
25% backdrop dimming, matching side/top insets and Drawer entrance easing.
Rows use a minimum height of 58 dp with icons and labels on the left, values on
the right, and up to two lines for longer text. Lists retain remote focus handling
and scroll; each settings page starts with its own scroll state. Back keeps the
existing navigation hierarchy; tapping above the sheet has the same action.
No system-setting behavior or helper protocol was changed. Release compilation
is checked locally; visual and physical remote acceptance remain deferred.
The TV was not updated in this iteration.

## Settings tiles and Personalize (1.48-gaia.23, uninstalled candidate)

The settings landing page uses a scrollable three-column grid with icons, section
names and hints. Personalize is its first tile; the separate Control Center entry
is removed for Gaia only. Its existing wallpaper, screensaver, weather, hidden-app,
PIN and phone configuration controls remain intact. Back returns to the settings
grid and requests focus on Personalize. Returning from other top-level sections
likewise restores their tile. Subpages retain their existing parameter rows.
Upstream package navigation is unchanged. Release compilation is checked locally;
remote focus/scroll and appearance still require device acceptance. No TV update,
reboot or system setting changes were performed.

## Upstream updater disabled (1.48-gaia.24, uninstalled candidate)

Gaia no longer initializes cached upstream update offers or performs scheduled,
Control Center or forced version checks. Its old latest/checked_at cache entries
are removed at initialization; unrelated preferences are retained. Installation
and download entry points also reject Gaia, including the diagnostic test-APK
path. Original upstream package builds retain their updater behavior.

Manual update QR links now point to alexkrass76/aerial-gaia releases, and setup
links point to this fork's GaiaOS guide, without TV addresses or version parameters.
The version label preserves the full Gaia suffix. No GitHub release polling,
automatic download or installation has been implemented; updates remain manual.
This change does not imply that a newer public Gaia release exists. Device
installation and acceptance remain deferred.

Validation: release build and 114 unit tests passed, including cached upstream
notification removal, disabled forced checks/installer, full Gaia version and
manual fork links that omit the device address. No TV installation was performed.

## Task switcher transitions (1.48-gaia.25)

The switcher registers opening/closing window transitions: 180 ms alpha and a
1.5% scale change, without capturing another screenshot or blurring video.
Android 14 uses overrideActivityTransition; older versions use the legacy override.
Animation is suppressed when animator or transition scale is zero. Remote input
is not delayed, and the existing tile focus and helper protocol remain unchanged.
Cross-task switches to other applications remain controlled by Android; this does
not promise custom animations for those paths. Physical acceptance is pending.
API behavior: https://developer.android.com/reference/android/app/Activity#overrideActivityTransition(int,int,int)

## Native picture/audio discovery (1.48-gaia.26)

The app binds explicitly to the installed CVTE TV API service and uses the
firmware's read-only interface methods via its on-device system library. No vendor
APK/JAR or decompiled implementation is bundled. Binding from a standalone shell
app_process was rejected by Android because it was not a registered application;
reading from the launcher process succeeded without root or new permissions.
The connection is unbound after each snapshot; binding has a five-second timeout
and all reads run off the UI thread. The individual Binder reads are synchronous;
a hung firmware call is not cancelled by the binding timeout. There is no polling.

The Picture tile and Sound > Audio outputs pages show the current mode, supported
mode lists, picture values and backlight, with per-item availability reported by
CVTE. They are explicitly read-only. The shell-only diagnostic provider also
exposes av_snapshot; no setter or arbitrary reflection method is exposed.

On the tested firmware, discovery returned five picture modes and four audio
outputs; digital audio offered PCM and RAW. In Soft picture mode the five picture
controls were disabled while backlight was enabled. Availability depends on mode
and input; do not turn every declared API into an enabled control. Several vendor
methods are stubs, and generic Android screen brightness is not evidence of actual
panel backlight control. HDMI/CEC, picture mode writes, audio routing writes and
physical picture/audio changes remain untested. No TV picture/audio values were
changed in this discovery round. The original snapshot is stored locally only.

Validation: release build passed; gaia.26 installed over the existing launcher and
speed-compiled. Shell-only av_snapshot successfully read the firmware and the
Picture page rendered the same mode/value data. Injected Down keys scrolled the
read-only rows. Helper remained connected with autostart enabled. Physical remote
acceptance, audio page layout and all write operations remain unverified.
The Control Center still displays generic Android brightness; its value differs
from CVTE backlight, so it must be migrated before claiming actual panel control.


## Native picture/audio controls (1.48-gaia.27)

The Control Center now uses CVTE panel backlight instead of generic Android screen
brightness. Backlight steps are 5 points, limited to 5–100. Picture presets and
PCM/RAW require confirmation; numeric picture controls are editable only in User
mode when the firmware reports them enabled. Fixed API and enum allowlists exclude
panel power, PWM frequency, resets and audio routing. Routing remains read-only
because the vendor implementation also changes CEC and routes sound asynchronously.

Before writes the adapter rereads the source, mode, availability and previous value.
It rejects stale snapshots and verifies the resulting firmware value. These checks
are not an atomic transaction with the vendor service. A synchronous preferences
journal preserves one last change before writing. Manual Undo survives launcher
restart, checks the source and current value, and verifies restoration. It is not
an automatic timed rollback or a complete calibration backup. Later changes replace
the single undo entry. The shell diagnostic provider remains read-only.

Validation: release build and 121 unit tests passed. Installed and speed-compiled
on the test TV. The Control Center showed native backlight 100; an injected Left
key changed it to 95, and the Picture page Undo button restored 100. Read-back
confirmed unchanged Soft picture values, speaker routing and PCM. The helper
remained connected with autostart enabled. Picture mode/numeric writes and PCM/RAW
have not been physically verified; no reboot or audio routing change was performed.
