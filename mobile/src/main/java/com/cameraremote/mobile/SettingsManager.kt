package com.cameraremote.mobile

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "camera_remote_prefs"

        // Camera Control
        private const val KEY_AUTO_OPEN_CAMERA = "auto_open_camera"
        private const val KEY_CAMERA_LAUNCH_DELAY = "camera_launch_delay_ms"
        private const val KEY_SHUTTER_FALLBACK_ENABLED = "shutter_fallback_enabled"
        private const val KEY_SHUTTER_FALLBACK_POSITION = "shutter_fallback_position"
        private const val KEY_GESTURE_TAP_DURATION = "gesture_tap_duration_ms"
        private const val KEY_FLASH_SUBMENU_DELAY = "flash_submenu_delay_ms"

        // Watch
        private const val KEY_HAPTIC_DURATION = "haptic_duration_ms"
        private const val KEY_DEFAULT_TIMER_SECONDS = "default_timer_seconds"
        private const val KEY_VIBRATE_ON_COUNTDOWN = "vibrate_on_countdown"
    }

    internal val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Auto-Open Camera ---

    fun isAutoOpenCameraEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_OPEN_CAMERA, true)

    fun setAutoOpenCameraEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_OPEN_CAMERA, enabled).apply()
    }

    // --- Camera Launch Delay ---

    fun getCameraLaunchDelayMs(): Int = prefs.getInt(KEY_CAMERA_LAUNCH_DELAY, 1500)

    fun setCameraLaunchDelayMs(delayMs: Int) {
        prefs.edit().putInt(KEY_CAMERA_LAUNCH_DELAY, delayMs).apply()
    }

    // --- Shutter Fallback ---

    fun isShutterFallbackEnabled(): Boolean = prefs.getBoolean(KEY_SHUTTER_FALLBACK_ENABLED, true)

    fun setShutterFallbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUTTER_FALLBACK_ENABLED, enabled).apply()
    }

    // --- Shutter Fallback Position (percentage from top) ---

    fun getShutterFallbackPosition(): Int = prefs.getInt(KEY_SHUTTER_FALLBACK_POSITION, 85)

    fun setShutterFallbackPosition(percent: Int) {
        prefs.edit().putInt(KEY_SHUTTER_FALLBACK_POSITION, percent).apply()
    }

    // --- Gesture Tap Duration ---

    fun getGestureTapDurationMs(): Int = prefs.getInt(KEY_GESTURE_TAP_DURATION, 50)

    fun setGestureTapDurationMs(durationMs: Int) {
        prefs.edit().putInt(KEY_GESTURE_TAP_DURATION, durationMs).apply()
    }

    // --- Flash Submenu Delay ---

    fun getFlashSubmenuDelayMs(): Int = prefs.getInt(KEY_FLASH_SUBMENU_DELAY, 300)

    fun setFlashSubmenuDelayMs(delayMs: Int) {
        prefs.edit().putInt(KEY_FLASH_SUBMENU_DELAY, delayMs).apply()
    }

    // --- Haptic Feedback Duration ---

    fun getHapticDurationMs(): Int = prefs.getInt(KEY_HAPTIC_DURATION, 30)

    fun setHapticDurationMs(durationMs: Int) {
        prefs.edit().putInt(KEY_HAPTIC_DURATION, durationMs).apply()
    }

    // --- Default Timer ---

    fun getDefaultTimerSeconds(): Int = prefs.getInt(KEY_DEFAULT_TIMER_SECONDS, 3)

    fun setDefaultTimerSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_DEFAULT_TIMER_SECONDS, seconds).apply()
    }

    // --- Vibrate on Countdown ---

    fun isVibrateOnCountdownEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATE_ON_COUNTDOWN, true)

    fun setVibrateOnCountdownEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATE_ON_COUNTDOWN, enabled).apply()
    }
}
