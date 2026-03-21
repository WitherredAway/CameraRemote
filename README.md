# WearOS Camera Remote

A simple WearOS app that lets you remotely control your phone's camera from your watch.

## Features

- **Take Photo** - Snap a photo on your phone with a single tap on the watch
- **Timer Photo** - 3-second countdown before taking the photo (great for group shots)
- **Toggle Flash** - Turn the camera flash on/off remotely
- **Switch Camera** - Toggle between front and rear cameras
- **Video Recording** - Start/stop video recording from your watch
- **Zoom Controls** - Zoom in and out remotely
- **Haptic Feedback** - Feel each button press on your watch
- **Status Updates** - Watch receives real-time status from the phone (photo saved, recording started, etc.)

## Architecture

The project consists of two modules:

### `mobile/` - Phone Companion App
- Uses **CameraX** for camera operations (photo capture, video recording, zoom, flash)
- Listens for commands from the watch via the **Wearable Data Layer MessageClient**
- Sends status updates back to the watch
- Includes a `WearableListenerService` that can launch the camera even when the app is closed

### `wear/` - WearOS Watch App
- Scrollable button-based UI optimized for round watch screens
- Sends commands to the phone via the **Wearable Data Layer MessageClient**
- Receives and displays status updates from the phone
- Haptic feedback on all button presses

## Communication Protocol

Commands sent from watch to phone via path `/camera_remote`:
| Command | Description |
|---------|-------------|
| `take_photo` | Capture a photo immediately |
| `take_photo_timer` | Capture after 3-second countdown |
| `toggle_flash` | Toggle flash on/off |
| `switch_camera` | Switch front/rear camera |
| `start_video` | Begin video recording |
| `stop_video` | Stop video recording |
| `zoom_in` | Increase zoom by 0.5x |
| `zoom_out` | Decrease zoom by 0.5x |

Status responses sent from phone to watch via path `/camera_remote/status`.

## Requirements

- **Phone**: Android 8.0+ (API 26+)
- **Watch**: WearOS 3.0+ (API 30+)
- Both devices must be paired via the Wear OS app

## Building

1. Open the project in Android Studio
2. Connect your phone and watch (or use emulators)
3. Build and install the `mobile` module on your phone
4. Build and install the `wear` module on your watch

```bash
# Build both modules
./gradlew assembleDebug

# Install on phone
./gradlew :mobile:installDebug

# Install on watch
./gradlew :wear:installDebug
```

## Permissions

### Phone App
- `CAMERA` - Required for camera operations
- `RECORD_AUDIO` - Required for video recording with audio
- `WRITE_EXTERNAL_STORAGE` (API <= 28) - For saving media files

### Watch App
- No special permissions needed (uses Wearable Data Layer only)

## How to Use

1. Install both apps on their respective devices
2. Make sure the watch is paired with the phone
3. Open **Camera Remote** on your watch
4. Wait for "Connected to phone" status
5. Use the buttons to control the camera!
   - The phone's camera app will open automatically
   - Photos are saved to `Pictures/CameraRemote/`
   - Videos are saved to `Movies/CameraRemote/`
