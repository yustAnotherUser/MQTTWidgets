# AGENTS.md

Guidance for agent sessions working on this repo.

## Project

MQTT Widgets — Android app (Kotlin + Jetpack Compose, Material 3) that renders MQTT values on home-screen widgets (1x1, 2x1, 3x1). Foreground MQTT service, Room storage, three `AppWidgetProvider`s. `compileSdk 34` / `minSdk 26`.

## Building — JDK 17 is required

- Gradle 8.11.1 / Kotlin 2.0.21 **cannot run on the machine's default JDK 26** (fails with `IllegalArgumentException: 26.0.1`).
- Use the installed JDK 17 explicitly — never rely on `JAVA_HOME`/`java` on PATH:
  ```powershell
  $env:JAVA_HOME = "C:\Users\micro\jdk-17\jdk-17.0.12"
  .\gradlew.bat clean :app:assembleDebug --console=plain
  ```
- The APK lands at `app\build\outputs\apk\debug\app-debug.apk`.
- Build output goes to a log and poll for completion instead of blocking on the command: the wrapper can get killed by the tool but Gradle keeps running. Use `release.ps1` (below) which does this correctly.

## Releasing (do NOT reinvent the pipeline)

Everything is automated in **`release.ps1`** in the repo root: version bump, clean build (JDK 17), deploy to the local download server, git commit + push, and a GitHub Release with the APK attached.

- Run from the repo root, e.g.:
  ```powershell
  .\release.ps1 -Version 2.9.3 -CommitMessage "Release v2.9.3" -Notes "Debug build v2.9.3"
  ```
  Without `-Version` it auto-increments the patch (2.9.2 -> 2.9.3); `versionCode` increments by 1 and the README badge is updated.
- Detached/agent usage — start it, then poll the stable log `C:\Users\micro\AppData\Local\Temp\opencode\mqttwidgets-release.log` for the `*_OK` markers (`BUILD_OK`, `WEB_OK`, `PUSH_OK`, `RELEASE_OK`) or errors:
  ```powershell
  Start-Process powershell -Args '-ExecutionPolicy','Bypass','-File','release.ps1','-Version','2.9.3','-CommitMessage','Release v2.9.3','-Notes','Debug build v2.9.3' -WorkingDirectory "C:\Users\micro\Desktop\android-app-mqttclient-widgets" -PassThru
  ```
- Switches to run only parts: `-SkipBuild`, `-SkipWeb`, `-SkipGit`, `-SkipRelease`.

### Facts the script encodes (know them, don't "fix" them away)

- Local download server: `python -m http.server 8888 --directory C:\Users\micro\mqtt-widgets-download` (bound 0.0.0.0). Serves `index.html` + versioned APKs at `http://192.168.178.39:8888/`. 192.168.178.39 is this Windows machine, NOT the Pi.
- GitHub: remote `origin` = `https://github.com/yustAnotherUser/MQTTWidgets.git`, branch `main`. `gh` CLI is authenticated as `yustAnotherUser`.
- Releases: tag `v<version>`, title `MQTT Widgets v<version>`, APK `MQTTWidgets-v<version>-debug.apk` attached. **The APK is never committed to the repo** — only to GitHub Releases.
- Version scheme: bump patch for routine changes (`versionName` in `app\build.gradle.kts`); About screen reads the version at runtime, nothing else to update except the README badge (the script handles it).
- Old versioned APKs stay on the webserver as history; `index.html` always points at the latest.

## Notes

- The `deploy-*.sh` scripts seen in `C:\Users\micro\AppData\Local\Temp\opencode` belong to a **different project** (freellmapi on the Pi). Do not use or "reuse" them for this app.
- Pre-existing deprecation warnings in `PrefsManager.kt` (EncryptedSharedPreferences `MasterKeys`) are expected; don't chase them.
