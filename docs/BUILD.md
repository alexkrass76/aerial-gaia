# Сборка Aerial UI

## Сборка

JDK 17, Android SDK 36 и Gradle Wrapper из репозитория:

```sh
./gradlew -Pgaia assembleStandardRelease
```

APK: `app/build/outputs/apk/standard/release/app-standard-release.apk`. Флаг `-Pgaia` обязателен; guard-вариант без HOME здесь не является заменой standard.

Для постоянной подписи задайте `local/signing.properties` и собственный keystore в `local/`:

```properties
storeFile=signing.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Не публикуйте ключи. Без них upstream-конфигурация использует локальный debug key. Обновление поверх установленной Gaia требует **той же подписи**; при несовпадении остановитесь, не удаляйте приложение ради установки.

## Установка и обновления

Следуйте [GaiaOS-инструкции](GAIAOS.md). Upstream `tools/install.sh`, `restore.sh`, сайт относятся к исходному Aerial. Встроенная проверка обновлений upstream отключена. **Не используйте их как установщик/обновлятор Gaia.** Автоматический канал Aerial UI пока не реализован; используйте сборку с сохранёнными патчами и той же подписью.

Обновление не должно заменять проверенный код последней upstream-версией без повторной проверки Direct Boot, HOME, HDMI и AirPlay. Исходные материалы upstream в `docs/`, `site/` и `tools/` сохранены для истории; для этого форка приоритетны `README.md` и `docs/GAIAOS.md`.
