# CameraRemote - Project Knowledge

## Overview
WearOS Camera Remote app. Two modules: a phone companion (AccessibilityService to control the default camera app) and a WearOS watch remote control. Communication via Wearable Data Layer MessageClient.

**Key difference from typical camera apps**: This app does NOT implement its own camera. It launches and controls the phone's default camera app using an AccessibilityService to find and tap camera UI buttons.

**Code style**: Matches [NotificationMirror](https://github.com/WitherredAway/NotificationMirror) conventions — AppCompatActivity, ViewBinding, DynamicColors, CoroutineScope with SupervisorJob, coroutines with `await()`, proper logging/error handling, card-based Material3 dark UI.

## Project Structure
```
CameraRemote/
├── mobile/          # Phone companion app (AccessibilityService + message listener)
│   └── src/main/java/com/cameraremote/mobile/
│       ├── MainActivity.kt              # Status UI, instructions, open camera button
│       ├── CameraControlService.kt      # AccessibilityService — finds & clicks camera buttons
│       └── WearMessageListenerService.kt # Background service for watch messages
├── wear/            # WearOS watch app (remote control UI + Tile)
│   └── src/main/java/com/cameraremote/wear/
│       ├── RemoteActivity.kt            # Main watch remote UI
│       ├── CameraRemoteTileService.kt   # Watch face tile with camera controls
│       └── TileActionActivity.kt        # Transparent activity for tile button actions
├── build.gradle           # Root build file (AGP 8.2.2, Kotlin 1.9.22, Groovy)
├── settings.gradle        # Multi-module settings (Groovy)
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
- Version is set in both `mobile/build.gradle` and `wear/build.gradle`
- APK filenames include version via `archivesBaseName` property
- **Rule**: Only bump version once per release cycle. Don't bump more than once before a release.

## Architecture

### How Camera Control Works
1. Watch sends command via Wearable MessageClient → phone's `WearMessageListenerService`
2. `WearMessageListenerService` forwards command via broadcast to `CameraControlService` (AccessibilityService)
3. `CameraControlService` either:
   - Launches default camera app via Intent (for `open_camera`, `open_video`)
   - Finds and clicks camera UI buttons via AccessibilityService node tree (for `take_photo`, `switch_camera`, `toggle_flash`)
   - Falls back to `dispatchGesture()` tap at typical shutter button location if node not found
4. Status is sent back to watch via MessageClient

### Communication Protocol
- **Path**: `/camera_remote` — watch sends commands to phone
- **Status Path**: `/camera_remote/status` — phone sends status back to watch
- **Transport**: Google Wearable MessageClient API
- **Commands**: `open_camera`, `take_photo`, `take_photo_timer`, `toggle_flash`, `switch_camera`, `open_video`
- **Status messages**: `camera_opened`, `photo_taken`, `photo_failed`, `timer_started`, `camera_switched`, `switch_not_found`, `flash_toggled`, `flash_not_found`, `video_camera_opened`, `video_open_failed`, `camera_open_failed`, `service_not_enabled`

### AccessibilityService Button Detection
The service searches for camera buttons using common content descriptions:
- **Shutter**: "shutter", "take photo", "capture", "camera button", "take picture", "shoot", etc.
- **Switch camera**: "switch camera", "flip camera", "toggle camera", "front camera", "selfie", etc.
- **Flash**: "flash", "flash mode", "toggle flash", "flash off", "flash on", etc.
- **Video**: "video", "record", "video mode", "start recording", etc.

Strategy: First searches by text, then by content description fuzzy match, then tries clicking parent of matching nodes. Falls back to screen tap at 50% width, 85% height for shutter.

### Mobile Module
- `MainActivity`: AppCompatActivity with DynamicColors, ViewBinding, CoroutineScope — shows service status, watch connection, settings, and "Open Camera" button
- `CameraControlService`: AccessibilityService that controls any camera app
- `WearMessageListenerService`: Background listener forwarding watch commands
- Card-based Material3 dark UI with section headers, icon circles, bg_card drawables

### Code Conventions (matching NotificationMirror)
- Groovy `.gradle` build files (not `.gradle.kts`)
- `AppCompatActivity` base class with `DynamicColors.applyToActivityIfAvailable(this)` in `onCreate`
- `ViewBinding` for type-safe view access
- `CoroutineScope(Dispatchers.IO + SupervisorJob())` for async operations
- `kotlinx.coroutines.tasks.await()` for Wearable API calls
- `companion object { private const val TAG = "..." }` for logging
- `Log.d/e/w(TAG, ...)` for all log output
- Try-catch around all system service calls and Wearable API calls
- `Toast.makeText` for user feedback
- `runOnUiThread { }` for UI updates from coroutines
- `buildConfigField` for BUILD_TIMESTAMP
- `Theme.Material3.Dark.NoActionBar` with transparent status/navigation bars
- Card-based layout: `bg_card.xml`, `bg_icon_circle.xml`, `bg_status_active.xml`, `bg_status_inactive.xml`
- Section headers: uppercase, 12sp bold (9sp on wear), `?colorPrimary`, letterSpacing 0.1
- List items: icon circle + text columns + chevron_right

### Wear Module
- Card-based Material3 dark UI with ScrollView and rotary/bezel scrolling
- List-item style controls with icon circles, matching NotificationMirror design
- Haptic feedback on all button presses
- **Tile support**: `CameraRemoteTileService` provides quick camera controls from watch face
- `TileActionActivity` is a transparent activity that handles tile button clicks

## Setup Required (User)
1. Install both APKs on paired phone + watch
2. On phone: Open CameraRemote → tap "Open Accessibility Settings"
3. Find "CameraRemote" in the accessibility service list → Enable it
4. On watch: Open CameraRemote → should show "Connected"

## Permissions
### Mobile (phone)
- `android.permission.BIND_ACCESSIBILITY_SERVICE` — required for controlling camera app
- No camera, audio, or storage permissions needed (default camera app handles those)

### Wear (watch)
- `android.hardware.type.watch` — required feature
- `com.google.android.wearable.permission.BIND_TILE_PROVIDER` — for tile service

## Dependencies
### Mobile
- Material Design 1.11.0
- Play Services Wearable 18.1.0
- ConstraintLayout 2.1.4
- AndroidX AppCompat 1.6.1
- AndroidX Activity KTX 1.8.2
- Kotlinx Coroutines Android 1.7.3
- Kotlinx Coroutines Play Services 1.7.3

### Wear
- Wear 1.3.0
- Wear Tiles 1.2.0
- Play Services Wearable 18.1.0
- Material Design 1.11.0
- Guava 31.1-android (for Tiles ListenableFuture)
- Wearable Support 2.9.0
- Kotlinx Coroutines Android 1.7.3
- Kotlinx Coroutines Play Services 1.7.3

## SDK Configuration
- Compile SDK: 34
- Target SDK: 34
- Min SDK (mobile): 26
- Min SDK (wear): 30
- Java: 17
- Kotlin: 1.9.22
- AGP: 8.2.2
- Gradle: 8.2

## Features
- Open default camera app from watch
- Take photo (tap shutter in any camera app)
- 3-second timer photo
- Toggle flash
- Switch front/rear camera
- Open video mode
- Watch face Tile with quick controls (open, snap, timer, flash, flip, video)
- Real-time status updates (phone → watch)
- Background message listener (receives watch commands even when phone app is closed)
- Haptic feedback on all watch buttons
- Fallback: opens camera via intent even if accessibility service is disabled

## Testing
1. Install both APKs on paired phone + watch
2. Enable CameraRemote accessibility service on phone
3. Open CameraRemote on watch — should show "Connected"
4. Tap OPEN CAMERA — phone's default camera app launches
5. Tap CAPTURE — phone takes photo
6. Test each button: timer, flash, flip, video
7. Verify status messages appear on watch
8. Add Camera Controls tile to watch face, test tile buttons

## Known Issues / Notes
- AccessibilityService button detection depends on the camera app's content descriptions
- Different camera apps (Google Camera, Samsung Camera, etc.) use different labels
- Fallback tap gesture targets center-bottom of screen (85% down) where shutter typically is
- Tile buttons launch `TileActionActivity` which sends the command and finishes immediately
- `open_camera` command works even without accessibility service enabled
