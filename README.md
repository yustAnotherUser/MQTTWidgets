# MQTT Widgets

Turn your home screen into a live dashboard for your MQTT devices.

MQTT Widgets is an Android app that subscribes to your MQTT broker and renders incoming values directly on home-screen widgets — with **1x1**, **2x1** and **3x1** layouts — while keeping a full in-app dashboard for management, live preview and configuration.

![GitHub Release](https://img.shields.io/badge/version-2.9.3-blue)
![Android](https://img.shields.io/badge/Android-8.0%2B-green)
![License](https://img.shields.io/badge/license-MIT-yellow)

## Features

- **Home-screen widgets** in three sizes: 1x1 value-only, 2x1 with label, 3x1 with label + last-update time and status dot.
- **Live updates** — widgets are pushed new values the moment a message arrives, no polling.
- **JSON value picker** — subscribe to a topic, press *Test*, and select the exact JSON field (e.g. `t_c`) to display from the live payload.
- **Smart formatting** — raw values, numbers or JSON fields, with configurable decimals and unit suffix (e.g. `32.8 °C`).
- **Threshold colors** — background changes automatically (e.g. red above 30 °C, blue below 5 °C) based on high/low limits you set per widget.
- **Resilient connection** — foreground service with automatic reconnection (exponential backoff), topic ref-counting, and auto-start after device reboot.
- **Slash-insensitive topics** — subscribes to both `sensors/...` and `/sensors/...` variants so device/broker differences never cause missed messages.
- **Secure credentials** — broker password stored with Android Keystore-backed encryption.

## Getting started

### Prerequisites

- Android 8.0 (API 26) or newer
- An MQTT broker (TCP — e.g. Mosquitto), reachable from your device
- Android Studio (for building from source)

### Install and configure

1. Build the APK (see [Development](#development)) or install a provided release:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
2. Launch **MQTT Widgets** — the first-run wizard asks for your broker host, port, and optional credentials. Press **Test** to verify connectivity, then save.
3. Tap **+** to create a widget:
   - Give it a label and pick a size (1x1 / 2x1 / 3x1)
   - Enter the topic your device publishes to, e.g. `sensors/environmental/aht20`
   - Tap **Test** to connect, wait for a message, and pick the JSON value you want to display
   - If you picked a numeric value you can set decimal places, a unit and threshold colors
4. Back on the home screen, tap **📌** on your widget card to pin it to the home screen and select the card in the configuration dialog.
5. Done — the widget updates live as messages arrive.

> [!TIP]
> Use the **Test** button in both the create and edit dialogs to verify payload and value selection *before* committing — the raw payload is shown live, so you can confirm your topic and JSON path are correct.

## Usage

### Managing widgets

The app home screen lists all configured widgets with a live preview. From each card you can:

| Action | Description |
| --- | --- |
| Tap the card | Edit label, topic, size, format, thresholds and colors |
| 📌 | Pin/update the widget on your home screen |
| 🗑️ | Delete the widget (and its subscription) |

### Broker settings

Open the navigation drawer → **Broker Settings** to change host, port or credentials at any time. The connection is re-established automatically after saving.

### Status feedback

A persistent notification shows the service state — connection status, matching card count per message, plus a `DB WIDGETS` counter confirming widget updates (`updated=1` means the value was pushed).

## Architecture

MQTT Widgets is a single-module Android app written in Kotlin with Jetpack Compose (Material 3).

```text
app/src/main/java/com/mqttwidgets/app/
├── MainActivity.kt          # Navigation shell (first-run wizard + drawer)
├── ConfigActivity.kt        # Widget configuration flow when pinning
├── service/
│   ├── MqttService.kt       # Foreground MQTT service: connect, subscribe, update widgets
│   └── TopicManager.kt      # Ref-counted topic diffing (subscribe/unsubscribe changes)
├── data/
│   ├── Card.kt              # Widget model + size/format enums (Room entity)
│   ├── CardDao.kt           # Room DAO (flow + sync queries)
│   ├── AppDatabase.kt       # Room database
│   └── PrefsManager.kt      # Broker settings (encrypted password)
├── widget/
│   └── WidgetProvider.kt    # Small/Compact/Wide AppWidgetProviders + renderer
├── util/
│   ├── PayloadParser.kt     # RAW/NUMBER/JSON parsing, JSON key extraction, thresholds
│   └── WidgetRenderer.kt    # Background/status color + display text resolution
├── receiver/
│   ├── BootReceiver.kt      # Restart service after reboot
│   └── TopicChangedReceiver.kt # Resubscribe when topics change
└── ui/
    ├── screens/             # Home, FirstRun, BrokerSettings, About
    └── components/          # Create/Edit dialogs, TestButton, WidgetCard
```

### How it works

1. `MqttService` (foreground service) connects to the broker and subscribes to the union of all configured topics (both slash variants).
2. On every incoming message, the payload is parsed per card (raw / number / JSON-path) and stored in Room.
3. `WidgetRenderer` computes the display text, unit suffix, background color (thresholds) and status.
4. The matching `AppWidgetProvider` views are updated immediately via `AppWidgetManager.updateAppWidget`.

The widget configuration is persisted in `widget_prefs` clamped to each widget ID, so widgets survive service restarts and app updates.

## Development

```bash
# Build a debug APK
./gradlew assembleDebug

# Run the unit tests
./gradlew test
```

### Releasing

The full release pipeline (bump version, clean build, deploy to the local download server, commit/push, GitHub release) is automated in `release.ps1`:

```powershell
# auto-increment the patch version (e.g. 2.9.2 -> 2.9.3)
.\release.ps1 -CommitMessage "Release v2.9.3" -Notes "Debug build v2.9.3"

# or specify the version explicitly
.\release.ps1 -Version 2.9.3 -CommitMessage "Release v2.9.3" -Notes "Debug build v2.9.3"
```

The APK is published to the GitHub **Releases** page (tag `v<version>`) and served at the local download server (`http://192.168.178.39:8888/`); it is never committed to the repository.

> [!NOTE]
> Use JDK 17. The project targets `compileSdk 34` / `minSdk 26` and uses Kotlin 2.0 with the Compose compiler plugin.

Key libraries: [Eclipse Paho MQTT client](https://www.eclipse.org/paho/) 1.2.5, Jetpack Compose (BOM 2024.08.00), Room 2.6.1, EncryptedSharedPreferences, Kotlin Coroutines.

## Project structure

```text
.
├── app/
│   ├── build.gradle.kts          # App module: deps, SDK levels, version
│   └── src/main/
│       ├── AndroidManifest.xml   # Providers, service, receivers, permissions
│       ├── java/com/mqttwidgets/app/   # Source (see Architecture)
│       └── res/                  # Layouts, widget info XMLs, icons, strings
├── build.gradle.kts              # Root build script
├── gradle/                       # Gradle wrapper
└── settings.gradle.kts           # Module + repository config
```

## Troubleshooting

| Symptom | Likely cause / fix |
| --- | --- |
| Widget shows `--` | Widget was pinned before the card existed or no message arrived yet. Re-pin via **📌** and verify the notification shows `DB WIDGETS: updated=1`. |
| No messages received | Check the notification for the connection state; verify host/port and that the topic exists on the broker (use the **Test** button). |
| Background color never changes | The value must be numeric and within the thresholds; thresholds are per-card, editable in the edit dialog. |
| Colors lost after update | Some launchers cache widget views — remove and re-pin the widget once. |

## Roadmap

Ideas for future iterations:

- Wildcard (`+` / `#`) topic support with manual refresh
- Multiple values per widget
- TLS (`ssl://`) broker support
- App widget click actions (e.g. publish a toggle command)