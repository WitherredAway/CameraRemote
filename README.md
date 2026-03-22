# Camera Remote

**Control your phone's camera from your WearOS watch** — open camera, snap photos, record video, toggle flash, switch lens, zoom with bezel, burst capture, photo preview, and more. Works with any camera app.

Made with help from [Devin AI](https://devin.ai).

---

<p align="center">
  <a href="https://ko-fi.com/wthrr"><img src="https://img.shields.io/badge/Support%20Me-Ko--fi-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Ko-fi" /></a>
  <a href="https://discord.gg/gK6wQywwzb"><img src="https://img.shields.io/badge/Join-Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" /></a>
</p>

---

## Why?

Most camera remote apps implement their own camera — meaning you lose all the features, processing, and quality of your phone's default camera app. CameraRemote takes a completely different approach: it controls **your existing camera app** via AccessibilityService, so you get the full native camera experience controlled from your wrist.

Perfect for group photos, tripod shots, vlogging, or any time your phone is out of reach.

---

## Features

### Camera Controls
- **Open Camera** — launch your default camera app from the watch
- **Take Photo** — tap the shutter button in any camera app
- **Timer Photo** — configurable countdown before capture (default 3s)
- **Video Mode** — switch to video and start/stop recording with recording timer display
- **Toggle Flash** — cycle through flash modes with smart submenu detection
- **Switch Camera** — toggle between front and rear cameras
- **Bezel Zoom** — rotate the watch bezel to zoom in/out with speed-proportional acceleration
- **Burst Capture** — long-press shutter for rapid photos with click-confirmed sequencing (each shot waits for shutter confirmation before the next)
- **Burst Timer** — countdown timer followed by burst capture
- **Photo Preview** — view captured photos on the watch with save/delete options
- **Gallery Shortcut** — long-press the camera button to open your gallery

### Watch App
- **Circular button layout** — aesthetic pastel-colored circle buttons arranged around a central shutter, optimized for round watch screens
- **Material You** — dynamic colors from your device theme applied to the background
- **Wear Tile** — quick-access tile with 6 camera controls (snap, camera, video, flash, flip, timer) right from your watch face
- **Haptic feedback** — configurable vibration on every button press
- **Real-time status** — live feedback from the phone (e.g. "Photo saved", "● REC 01:23", "Flash: Auto")
- **Recording timer** — live elapsed time display while recording video
- **Photo count** — tracks how many photos you've taken in the current session
- **Bezel scrolling** — native rotary input support

### Phone App
- **Configurable settings** — adjust timer duration, haptic strength, and more from the phone
- **Settings sync** — settings automatically sync to the watch via Wearable Data Layer
- **Auto-update** — check for and install updates from GitHub releases
- **Accessibility service monitor** — notification when the service stops, with quick re-enable link
- **Heartbeat connection check** — 30-second interval connection monitoring with auto-reconnect
- **Camera detection** — automatically notifies watch when camera app opens or closes
- **Discord link** — quick link to the [Discord server](https://discord.gg/gK6wQywwzb)
- **Ko-fi link** — support the developer on [Ko-fi](https://ko-fi.com/wthrr)
- **GitHub link** — quick link to the project repo with version info

### Smart Camera Control
- **Works with any camera app** — not hardcoded to any specific camera (Samsung, Google, Pixel, etc.)
- **Accessibility-based detection** — finds camera buttons by content description, not screen coordinates
- **Shutter priority** — tries photo shutter first in photo mode, but stops recording first if video is active
- **Flash submenu handling** — automatically detects and navigates flash option submenus with retry logic
- **Camera-not-open gating** — commands are only sent when the camera app is actually in the foreground
- **Photo mode enforcement** — shutter button opens camera in photo mode, not video

---

## Setup

### Download
1. Download the phone and watch APKs from the [releases page](https://github.com/WitherredAway/CameraRemote/releases) (or build the APKs yourself, scroll down for that)

### Install
You'll have to sideload the watch APK using one of these tools:
- [Wear Installer 2](https://play.google.com/store/apps/details?id=org.freepoc.wearinstaller2) ([Website](https://freepoc.org/wear-installer-2-help-page/)) — simple ADB-based installer over Wi-Fi
- [WearLoad](https://play.google.com/store/apps/details?id=com.camope.wearload) ([Website](https://wearload.github.io/index_en.html) · [XDA](https://xdaforums.com/t/app-wear-os-wearload-install-apk-apks-zip-without-debug-mode-and-adb.4766128/)) — install APKs without debug mode or ADB
- [GeminiMan WearOS Manager](https://play.google.com/store/apps/details?id=com.geminiman.wearosmanager) — full-featured watch manager with app installer
- Or use `adb` directly from a computer

If Google Play prevents you from installing the phone APK, please use [Install With Options](https://github.com/zacharee/InstallWithOptions) to install it.

1. Install the `mobile` APK on your Android phone
2. Install the `wear` APK on your Wear OS watch
3. Open the phone app → tap **"Open Accessibility Settings"** → find **CameraRemote** → **Enable** it
4. On your watch: Open CameraRemote → should show **"Connected"**

### Usage
1. Once the accessibility service is enabled, the phone app runs in the background
2. Tap **Open Camera** on the watch to launch your phone's camera
3. Use the watch buttons to control the camera — snap photos, record video, toggle flash, etc.
4. Rotate the bezel to zoom in/out
5. After taking a photo, tap the preview button to view it on the watch

---

## How It Works

1. **Watch sends command** — user taps a button, watch sends command via `MessageClient` (Wearable Data Layer API)
2. **Phone receives command** — `WearMessageListenerService` routes the command to `CameraControlService`
3. **Camera app launched** — if not already open, the camera app is launched via standard Android intents
4. **Accessibility controls camera** — `CameraControlService` (an `AccessibilityService`) finds UI elements by content description and performs click actions
5. **Status sent back** — phone sends real-time status updates back to the watch
6. **Preview captured** — after a photo, the phone reads the latest image from MediaStore, resizes it, and sends it to the watch via DataClient

---

## Notes

- Both devices must be connected via Bluetooth for real-time control
- The phone and watch apps share the same application ID (`com.cameraremote.mobile`) for Wearable API pairing
- The AccessibilityService does **not** read or store any personal data — it only interacts with camera app buttons
- No camera, audio, or storage permissions are needed by the phone app itself (your camera app handles those)
- Zoom uses a queue-based approach with 200ms spacing between commands for smooth operation
- Settings are cached on the watch for offline access and synced when connected

---

## Architecture

This is a multi-module Android project:

### `mobile/` — Phone Companion App
- **`CameraControlService`** — AccessibilityService that finds and clicks camera UI elements (shutter, flash, switch, record) by content description
- **`WearMessageListenerService`** — receives commands from the watch via Wearable Data Layer and routes them to the accessibility service
- **`MainActivity`** — dashboard showing connection status, accessibility service state, settings, and links
- **`SettingsActivity`** — configurable settings (timer duration, haptic strength) synced to watch via DataClient
- **`SettingsManager`** — manages settings with SharedPreferences and Wearable DataClient sync
- **`DeletePhotoActivity`** — handles photo deletion with Android 11+ `MediaStore.createDeleteRequest()` confirmation

### `wear/` — WearOS Watch App
- **`RemoteActivity`** — main UI with circular button layout, bezel zoom, status display, and preview overlay
- **`CameraRemoteTileService`** — Wear Tile with 6 quick-access camera controls
- **`TileActionActivity`** — transparent activity that receives tile button clicks and sends commands to phone

---

## Building

### Prerequisites
- Android Studio (Arctic Fox or later)
- Android SDK 34
- Wear OS emulator or physical watch

### Build
1. Open the project in Android Studio
2. Sync Gradle
3. Build both `mobile` and `wear` modules

```bash
# Build both modules
./gradlew assembleDebug

# Install on phone
./gradlew :mobile:installDebug

# Install on watch
./gradlew :wear:installDebug
```

---

## Requirements

- **Phone**: Android 8.0+ (API 26+)
- **Watch**: WearOS 3.0+ (API 30+)
- Both devices must be paired via the Wear OS app

## Permissions

### Phone App
- **AccessibilityService** — required to control the camera app UI (find and click buttons)
- **POST_NOTIFICATIONS** — for service status notifications
- **READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES** — for photo preview feature
- No camera permissions needed — your default camera app handles those

### Watch App
- **VIBRATE** — for haptic feedback
- No other special permissions (uses Wearable Data Layer only)
