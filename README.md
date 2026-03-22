# CameraRemote

A WearOS app that lets you remotely control your phone's default camera app from your watch.

## How It Works

Unlike typical camera remote apps, CameraRemote does **not** implement its own camera. Instead, it launches and controls whatever camera app is already installed on your phone using an **AccessibilityService** to find and tap camera UI buttons.

## Features

- **Open Camera** - Launch your default camera app from your watch
- **Take Photo** - Tap the shutter button in any camera app
- **Timer Photo** - 3-second countdown before taking the photo
- **Toggle Flash** - Toggle the flash mode
- **Switch Camera** - Toggle between front and rear cameras
- **Video Mode** - Switch to video camera mode
- **Watch Tile** - Quick controls right from your watch face
- **Haptic Feedback** - Feel each button press on your watch
- **Status Updates** - Watch receives real-time status from the phone

## Architecture

### `mobile/` - Phone Companion App
- Launches the **default camera app** via standard Android intents
- Uses an **AccessibilityService** to find and click camera buttons (shutter, flash, switch, etc.)
- Searches by content description across popular camera apps (Google Camera, Samsung Camera, etc.)
- Falls back to tap gesture at typical shutter button location
- Listens for commands from the watch via the **Wearable Data Layer MessageClient**

### `wear/` - WearOS Watch App
- Material-styled UI with pill-shaped buttons optimized for round watch screens
- Watch face **Tile** with quick camera controls
- Sends commands to the phone via **Wearable Data Layer MessageClient**
- Receives and displays status updates from the phone

## Setup

1. Install both APKs on your paired phone + watch
2. On your phone: Open CameraRemote > tap **"Open Accessibility Settings"**
3. Find **CameraRemote** in the list > **Enable** it
4. On your watch: Open CameraRemote > should show **"Connected"**

## Building

```bash
# Build both modules
./gradlew assembleDebug

# Install on phone
./gradlew :mobile:installDebug

# Install on watch
./gradlew :wear:installDebug
```

## Requirements

- **Phone**: Android 8.0+ (API 26+)
- **Watch**: WearOS 3.0+ (API 30+)
- Both devices must be paired via the Wear OS app

## Permissions

### Phone App
- **AccessibilityService** - Required to control the camera app UI
- No camera, audio, or storage permissions needed (your default camera app handles those)

### Watch App
- No special permissions needed (uses Wearable Data Layer only)
