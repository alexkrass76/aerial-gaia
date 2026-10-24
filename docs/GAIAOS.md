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
