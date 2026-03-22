# CameraRemote - Project Knowledge

## Overview
WearOS Camera Remote app. Two modules: a phone companion (CameraX camera) and a WearOS watch remote control. Communication via Wearable Data Layer MessageClient.

## Project Structure
```
CameraRemote/
├── mobile/          # Phone companion app (CameraX + message listener)
│   └── src/main/java/com/cameraremote/mobile/
│       ├── CameraActivity.kt         # Main camera UI, handles all camera ops
│       └── WearMessageListenerService.kt  # Background service for watch messages
├── wear/            # WearOS watch app (remote control UI + Tile)
│   └── src/main/java/com/cameraremote/wear/
│       ├── RemoteActivity.kt          # Main watch remote UI
│       ├── CameraRemoteTileService.kt # Watch face tile with camera controls
│       └── TileActionActivity.kt      # Transparent activity for tile button actions
├── build.gradle.kts       # Root build file (AGP 8.2.0, Kotlin 1.9.22)
├── settings.gradle.kts    # Multi-module settings
├── gradle.properties      # JVM args, AndroidX flags
└── SKILL.md               # This file
```

## Build Commands
```bash
# Set environment
export ANDROID_HOME=/home/ubuntu/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Build both modules (debug)
./gradlew assembleDebug

# Build individual modules
./gradlew :mobile:assembleDebug
./gradlew :wear:assembleDebug

# Clean build
./gradlew clean assembleDebug
```

## APK Output Locations
- Phone: `mobile/build/outputs/apk/debug/CameraRemote-Phone-v{version}-debug.apk`
- Watch: `wear/build/outputs/apk/debug/CameraRemote-Watch-v{version}-debug.apk`
- Naming convention: `CameraRemote-{Phone|Watch}-v{version}.apk` (strip `-debug` suffix when delivering)

## Versioning
- Current version: **1.0.0** (versionCode: 1)
- Version is set in both `mobile/build.gradle.kts` and `wear/build.gradle.kts`
- APK filenames include version via `archivesBaseName` property
- **Rule**: Only bump version once per release cycle. Don't bump more than once before a release.

## Architecture

### Communication Protocol
- **Path**: `/camera_remote` — watch sends commands to phone
- **Status Path**: `/camera_remote/status` — phone sends status back to watch
- **Transport**: Google Wearable MessageClient API
- **Commands**: `take_photo`, `take_photo_timer`, `toggle_flash`, `switch_camera`, `start_video`, `stop_video`, `zoom_in`, `zoom_out`
- **Status messages**: `photo_taken`, `photo_failed`, `recording_started`, `recording_stopped`, `recording_failed`, `flash_ON`, `flash_OFF`, `camera_switched_Rear`, `camera_switched_Front`, `zoom_{ratio}`

### Mobile Module
- Uses CameraX for camera operations (preview, image capture, video capture)
- Material Design 3 (Material You) themed UI with dark camera theme
- MaterialCardView-based control bar with FAB capture button and icon buttons
- `WearMessageListenerService` runs in background to receive watch commands even when app is closed
- Photos saved to `Pictures/CameraRemote/`, videos to `Movies/CameraRemote/`

### Wear Module
- Material-styled round UI with `BoxInsetLayout` for round watch support
- Outlined buttons with pill shapes for clean WearOS look
- Haptic feedback on all button presses
- **Tile support**: `CameraRemoteTileService` provides quick camera controls from watch face
- `TileActionActivity` is a transparent activity that handles tile button clicks and sends commands

## Permissions
### Mobile (phone)
- `android.permission.CAMERA` — required
- `android.permission.RECORD_AUDIO` — for video recording
- `android.permission.WRITE_EXTERNAL_STORAGE` — only for SDK ≤ 28

### Wear (watch)
- `android.hardware.type.watch` — required feature
- `com.google.android.wearable.permission.BIND_TILE_PROVIDER` — for tile service

## Dependencies
### Mobile
- CameraX 1.3.1 (core, camera2, lifecycle, video, view)
- Material Design 1.11.0
- Play Services Wearable 18.1.0
- ConstraintLayout 2.1.4
- AndroidX AppCompat 1.6.1

### Wear
- Wear 1.3.0
- Wear Tiles 1.2.0 + Tiles Material 1.2.0
- Play Services Wearable 18.1.0
- Guava 31.1-android (for Tiles ListenableFuture)
- Wearable Support 2.9.0

## SDK Configuration
- Compile SDK: 34
- Target SDK: 34
- Min SDK (mobile): 26
- Min SDK (wear): 30
- Java: 17
- Kotlin: 1.9.22
- AGP: 8.2.0
- Gradle: 8.2

## Features
- Take photo (instant + 3-second timer)
- Toggle flash on/off (with torch for viewfinder)
- Switch front/rear camera
- Start/stop video recording with audio
- Zoom in/out (0.5x steps)
- Capture flash animation on photo taken
- Real-time status updates (phone → watch)
- Background message listener (opens camera from watch even when phone app is closed)
- Watch face Tile with quick controls (capture, flash, flip, record, zoom)
- Haptic feedback on all watch buttons

## Testing
1. Install both APKs on paired phone + watch
2. Open CameraRemote on watch — should show "Connected" if phone is reachable
3. Tap CAPTURE — phone camera opens and takes photo
4. Test each button: timer, flash, flip, record, zoom
5. Verify status messages appear on watch
6. Add Camera Controls tile to watch face, test tile buttons

## Known Issues / Notes
- CameraX `setSurfaceProvider()` must be called with method syntax, not property syntax
- Tile buttons launch `TileActionActivity` which sends the command and finishes immediately
- Flash toggle applies to both ImageCapture flash mode and Camera torch
