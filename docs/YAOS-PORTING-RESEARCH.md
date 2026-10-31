# YaOS: план функционального паритета Aerial UI

Исследование 2026-10-31. Объект: HI/BBK VHIX-65U169MSY, Android 11/API 30,
MediaTek/MStar mt9632, Cultraview. Это план реализации, а не заявление о готовности.
Исходники Aerial изучены вместе с локальными копиями Factory Menu, Yandex Settings,
Yandex SystemUI, TV boot и плеера. На живом ТВ выполнены только чтение свойств,
списков служб/HAL, прав устройств ввода и копирование APK. Настройки не менялись.
Часть методов JADX восстановлена с ошибками: выводы основаны на явно читаемых
участках; полный аудит прошивки, native-кода и всех Binder-проверок не выполнен.

## Подтверждённая платформа и доступ

ADB сейчас работает как UID 2000 shell, не root. SELinux Permissive. Это не
отменяет signature permissions, проверки UID в Binder, Linux DAC или ограничения
загрузчика native-библиотек. Повторять root-сценарии LVG без проверки нельзя.
Shell имеет группу input; /dev/input/event* имеют root:input 0660.
Обнаружены MStar Smart TV IR Receiver, MTK TV KEYPAD и Bluetooth Remote Controller
Consumer Control. Фактический код Home ещё нужно записать при нажатии пульта.

Живой lshal показывает vendor.mediatek.tv.mtktvapi@1.0::IMtkTvApiConfig,
IMtkTvApiCec, IMtkTvApiInputSrc, IMtkTvApiEvent и другие интерфейсы;
отдельно присутствует заводское семейство mtktvfactory (Audio, Display и др.).
Наличие в lshal не доказывает успешный вызов из Aerial.
На диске есть /vendor/lib/libcom_mediatek_twoworlds_tv_jni.so;
Yandex Settings имеет ABI armeabi-v7a и UID 1000. В TVNative найден
System.loadLibrary("com_mediatek_twoworlds_tv_jni").

## Что уже общее и что требует адаптации

| Функция CVTE-сборки | Путь на YaOS | Уровень уверенности / недостающая проверка |
|---|---|---|
| Рабочий стол, док, обои, погода, локализация, заставка, полка, телефонная настройка, обновления | Общий UI и Android API | Общий APK установлен; отдельный полный регресс на YaOS ещё нужен |
| Home и загрузочный экран | DirectBootHomeActivity + обычный HOME | Home подтверждён владельцем; холодная загрузка ещё не проверена |
| HDMI | Существующий MediaTek TV Center + физический passthrough URI | HDMI2/PS5, звук и Home подтверждены; HDMI1/3, CEC и wake отдельно |
| Каталог каналов | YaosChannels + redirect provider + штатный Yandex player | 246 каналов/9 групп, EPG, группы/избранное и воспроизведение проверялись |
| Многозадачность, двойной Home, удаление задачи | Android 11 task bridge + наблюдатель input | Сейчас bootstrap исключает SDK <31; нужны адаптер сигнатур и новые профили пульта |
| Громкость/mute в настройках | AudioManager | Общий механизм; проверить HDMI/ARC, шкалу и mute на пульте |
| Собственный глобальный OSD | Отдельный Android 11 VolumeController backend | В Yandex SystemUI найден setVolumeController(IVolumeController); права Aerial/shell не подтверждены |
| Яркость/контраст/цветность/резкость/профиль | MediaTek MtkTvConfig → JNI → HAL | Обёртки найдены; чтение из нашего процесса и допустимые диапазоны не проверены |
| Подсветка | MediaTek config, отдельная от яркости видеосигнала | Найден fusion backlight key, применимость к панели не подтверждена |
| Баланс, SPDIF, выход звука/профили | MediaTek audio config | Есть ключи/обёртки; значения и влияние на ARC нужно измерять |
| CEC, выключение/пробуждение | MediaTek CEC + Android hdmi_control | HAL и service присутствуют; семантика vendor-переключателей не проверена |
| Wi-Fi/Ethernet/Bluetooth, время, язык, клавиатура, субтитры, хранилище | Общие контроллеры Android + разрешённый privileged bridge | Проверять каждую операцию в app UID, Android 11 имеет собственные ограничения |
| Приложения, разрешения, остановка/удаление | Общий bridge и PackageInstaller | Сначала расширить защиту системных пакетов для YaOS |
| AirPlay и VPN/LAN | Отдельный наш приёмник / WireGuard, независимые от SoC | Возможен перенос; не считается проверенным только по работе LVG |
| USB/LocalSend/APK | Общий SAF/FileProvider/PackageInstaller и файловый менеджер | Проверить выдачу разрешений и установщик именно на Android 11 |
| Таймеры/сон/питание | Android Power/Alarm + vendor power policy | Не подменять политику источников и CEC; нужны проверки standby/wake |

## Изображение и звук: конкретный вход в MediaTek

В Factory Menu и Yandex Settings есть com.mediatek.twoworlds.tv.MtkTvConfig.
Читаемые методы MtkTvConfigBase: getConfigValue(inputGroup, key),
getMinMaxConfigValue(key), getMinValue/getMaxValue, setConfigValue и
setConfigValueForAllElements. Значение inputGroup=-1 применяется по умолчанию.
Нельзя считать его гарантированно глобальным контекстом: параметры могут зависеть
от входа, SDR/HDR, профиля изображения и состояния воспроизведения.

Найденные идентификаторы:

- g_video__picture_mode, g_video__brightness, g_video__contrast;
- g_video__vid_sat, g_video__vid_shp, g_video__vid_hue;
- g_video__vid_game_mode, g_video__vid_hdmi_mode;
- g_fusion_picture__backlight (кандидат, не подтверждён для этой панели);
- g_audio__aud_balance, g_audio__aud_out_port, g_audio__spdif;
- g_cec__cec_func, g_cec__cec_auto_off, g_cec__cec_auto_on,
  g_cec__cec_pass_through, g_cec__cec_sac_func.

Это внутренние vendor-идентификаторы, не Android Settings.Global. Запись таких
строк через settings put не является реализацией управления телевизором.
Нельзя переносить числовые enum/диапазоны CVTE. CFGR_OK=0, отрицательные коды
в обёртке означают ошибки; отрицательное чтение нельзя показывать как значение.
Даже CFGR_OK нужно подтверждать повторным чтением и физической проверкой.

Предпочтительный путь: маленький локальный backend, использующий установленный
на устройстве код/JNI. Сначала выяснить доступность нужных классов и native namespace
из UID Aerial, затем из app_process shell. Для shell учитывать 32-битную библиотеку:
проверить наличие app_process32 и его classpath, а не полагаться на ABI Mac.
Копировать проприетарные классы и .so в наш публичный APK не требуется.
Если загрузка из установленного APK технически невозможна, следующий кандидат —
собственный минимальный клиент документированного по разбору HAL; его доступность
также проверяется отдельно. Root не объявлять обязательным заранее.
Не использовать Factory HAL для обычных пользовательских параметров, если есть
runtime Config API. Не трогать panel type, калибровку, EDID/HDCP/DRM и NVRAM.

## Многозадачность: реальный блокер в нашем коде

TaskBridgeBootstrap.ensure возвращает управление при SDK_INT <31. Это исключает
YaOS/API30 целиком. Его команда запуска также содержит имена пультов LVG.
Исправление: разделить TaskBackendApi30/Api31Plus, проверить getRecentTasks,
возврат к задаче и removeTask по реальным сигнатурам этой прошивки; затем подключить
настраиваемый read-only input observer. Удаление задачи и force-stop — разные
операции и должны остаться разными в UI.
Автозапускать через loopback ADB после unlock, если пользователь включил функцию;
не ждать появления Wi-Fi. Проверить LocalAdbTransport и авторизацию именно здесь.
Если loopback adbd доступен до Wi-Fi, это уберёт сетевую задержку, но пока это гипотеза.
Не захватывать эксклюзивно input device: обычный Home и vendor key interceptor
должны продолжить работать. Код Home определять записью событий, не по имени.

## Громкость: другой путь, чем у CVTE

com.android.systemui здесь установлен как YandexSystemUI.apk. В декомпилированном
b2/b.java AudioManagerApi30 вызывает AudioManager.setVolumeController с
VolumeControllerApi30Wrapper; при снятии вызывает setVolumeController(null).
Есть собственные VolumeDialogImpl и ExternalDeviceVolumeDialog.
Поэтому GaiaVolumeEvents, привязанный к cn.cvte.tv.api.jar/TvApiService, неприменим.

План: сначала read-only AudioManager snapshot/события и различение TV/ARC/внешней
шкалы; далее проверить требуемое STATUS_BAR_SERVICE в AudioService этой прошивки.
Обычный overlay не предотвращает штатный dialog. Успешное чтение input также не
даёт права зарегистрировать IVolumeController. Не отключать весь SystemUI.
Если нужного права нет, показывать наш OSD внутри Aerial, а глобальную замену
оставить недоступной до появления узкого обратимого способа. Если право есть —
lease/watchdog, восстановление штатного контроллера при сбое, без изменения
громкости со стороны самого визуализатора и без polling-мигания на 0%.

## Архитектура и порядок работы

1. Защитить com.mediatek.*, com.ctv.*, необходимые Yandex platform/core/services,
   WebView, клавиатуру, установщик и updater от кнопок остановки/удаления.
   Сейчас AppManagementPolicy содержит CVTE/Droidlogic и не имеет равнозначной
   защиты MediaTek/YaOS. Список зависимостей должен быть по компонентам, а не
   безусловным запретом всех приложений Яндекса (Кинопоиск — пользовательское ПО).
2. CapabilityReport для app UID и shell: Supported/ReadOnly/NeedsBridge/Unsupported/
   TemporarilyUnavailable, причина и версия backend. Выводить только реально
   применимые элементы, ошибки API не маскировать нулём.
3. Вынести общий AvBackend из GaiaAvSettings: snapshot, capabilities, change,
   undo, events. Сохранить CVTE backend без изменения поведения; добавить MediaTek.
4. Реализовать только чтение MediaTek Config: текущий вход, режим, диапазоны,
   звук/CEC. Сравнить со штатными настройками и HDMI/HDR. Это первый прототип.
5. Android 11 task bridge, физический двойной Home, выбор/закрытие задачи,
   автозапуск без Wi-Fi. Проверить отсутствие конфликтов со штатным обработчиком.
6. Обратимые записи по одной: баланс, параметры картинки, backlight/режим,
   SPDIF/CEC — после подтверждения семантики. Журнал before/expected/after,
   optimistic concurrency, немедленный undo и timed revert для потери картинки.
   Не вызывать setConfigValueForAllElements в первом варианте.
7. Глобальная громкость — отдельный эксперимент после выяснения прав контроллера.
8. Проверить AirPlay, USB/LocalSend, Bluetooth, Wi-Fi/VPN и системные настройки;
   завершить standby/cold-boot тестом с Ethernet и отдельным согласием владельца.

Приёмка каждого пункта: реальный результат на ТВ, восстановление исходного значения,
Home/HDMI/каналы и отсутствие лишних foreground-процессов. Полный паритет обещать
до этих проверок нельзя. Менять подпись, applicationId или системную прошивку для
первых этапов не требуется.

## Источники и воспроизводимость

Локальные исходники проекта: system/GaiaAvSettings.kt, GaiaDeviceSettings.kt,
AppManagementPolicy.kt, guard/GaiaVolumeEvents.kt, tasks/TaskBridgeBootstrap.kt,
tools/task-bridge, platform/TvPlatforms.kt и channels/YaosChannels.kt.
Локальные проприетарные APK и дампы не включаются в репозиторий; сохраняются
только выводы и имена интерфейсов. Декомпилированные файлы — средство анализа,
не исходники для включения в продукт.

- https://source.android.com/docs/devices/tv/hdmi-cec — сервис CEC и уровни доступа.
- https://source.android.com/docs/devices/tv — TIF и границы разрешений TV Provider.
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/hdmi/HdmiControlManager.java — API CEC; текущий AOSP служит ориентиром, поведение API30 сверять с прошивкой.
