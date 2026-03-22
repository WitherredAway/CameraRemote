package com.cameraremote.mobile

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"

        // Wearable Data Layer
        private const val WEARABLE_SETTINGS_PATH = "/camera_remote/settings"
        private const val KEY_HAPTIC_DURATION_MS = "haptic_duration_ms"
        private const val KEY_DEFAULT_TIMER_SECONDS = "default_timer_seconds"
        private const val KEY_VIBRATE_ON_COUNTDOWN = "vibrate_on_countdown"
        private const val KEY_TIMESTAMP = "timestamp"

        // Validation ranges — Camera Control
        private const val CAMERA_LAUNCH_DELAY_MIN_MS = 100
        private const val CAMERA_LAUNCH_DELAY_MAX_MS = 10000
        private const val BURST_COUNT_MIN = 1
        private const val BURST_COUNT_MAX = 50
        private const val FALLBACK_POSITION_MIN = 10
        private const val FALLBACK_POSITION_MAX = 99
        private const val GESTURE_TAP_DURATION_MIN_MS = 10
        private const val GESTURE_TAP_DURATION_MAX_MS = 500
        private const val FLASH_SUBMENU_DELAY_MIN_MS = 50
        private const val FLASH_SUBMENU_DELAY_MAX_MS = 2000

        // Validation ranges — Watch
        private const val HAPTIC_DURATION_MIN_MS = 5
        private const val HAPTIC_DURATION_MAX_MS = 500
        private const val TIMER_DURATION_MIN_SEC = 1
        private const val TIMER_DURATION_MAX_SEC = 60
    }

    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_settings)

        settings = SettingsManager(this)

        // Camera Control section
        val autoOpenCameraSwitch = findViewById<SwitchMaterial>(R.id.autoOpenCameraSwitch)
        val cameraLaunchDelayInput = findViewById<EditText>(R.id.cameraLaunchDelayInput)
        val shutterFallbackSwitch = findViewById<SwitchMaterial>(R.id.shutterFallbackSwitch)
        val fallbackPositionInput = findViewById<EditText>(R.id.fallbackPositionInput)
        val gestureTapDurationInput = findViewById<EditText>(R.id.gestureTapDurationInput)
        val flashSubmenuDelayInput = findViewById<EditText>(R.id.flashSubmenuDelayInput)
        val burstCountInput = findViewById<EditText>(R.id.burstCountInput)

        // Watch section
        val hapticDurationInput = findViewById<EditText>(R.id.hapticDurationInput)
        val defaultTimerInput = findViewById<EditText>(R.id.defaultTimerInput)
        val vibrateCountdownSwitch = findViewById<SwitchMaterial>(R.id.vibrateCountdownSwitch)

        val saveButton = findViewById<MaterialButton>(R.id.saveSettingsButton)

        // Load current values - Camera Control
        autoOpenCameraSwitch.isChecked = settings.isAutoOpenCameraEnabled()
        cameraLaunchDelayInput.setText(settings.getCameraLaunchDelayMs().toString())
        burstCountInput.setText(settings.getBurstCount().toString())
        shutterFallbackSwitch.isChecked = settings.isShutterFallbackEnabled()
        fallbackPositionInput.setText(settings.getShutterFallbackPosition().toString())
        gestureTapDurationInput.setText(settings.getGestureTapDurationMs().toString())
        flashSubmenuDelayInput.setText(settings.getFlashSubmenuDelayMs().toString())

        // Load current values - Watch
        hapticDurationInput.setText(settings.getHapticDurationMs().toString())
        defaultTimerInput.setText(settings.getDefaultTimerSeconds().toString())
        vibrateCountdownSwitch.isChecked = settings.isVibrateOnCountdownEnabled()

        // Show save button when any setting changes
        val showSave = { saveButton.visibility = android.view.View.VISIBLE }

        val switches = listOf(autoOpenCameraSwitch, shutterFallbackSwitch, vibrateCountdownSwitch)
        for (sw in switches) {
            sw.setOnCheckedChangeListener { _, _ -> showSave() }
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { showSave() }
        }
        val inputs = listOf(
            cameraLaunchDelayInput, burstCountInput, fallbackPositionInput, gestureTapDurationInput,
            flashSubmenuDelayInput, hapticDurationInput, defaultTimerInput
        )
        for (input in inputs) {
            input.addTextChangedListener(textWatcher)
        }

        saveButton.setOnClickListener {
            // Validate Camera Launch Delay
            val launchDelay = cameraLaunchDelayInput.text.toString().trim().toIntOrNull()
            if (launchDelay == null || launchDelay < CAMERA_LAUNCH_DELAY_MIN_MS || launchDelay > CAMERA_LAUNCH_DELAY_MAX_MS) {
                Toast.makeText(this, "Camera launch delay must be between $CAMERA_LAUNCH_DELAY_MIN_MS and $CAMERA_LAUNCH_DELAY_MAX_MS ms", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Burst Count
            val burstCount = burstCountInput.text.toString().trim().toIntOrNull()
            if (burstCount == null || burstCount < BURST_COUNT_MIN || burstCount > BURST_COUNT_MAX) {
                Toast.makeText(this, "Burst photo count must be between $BURST_COUNT_MIN and $BURST_COUNT_MAX", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Fallback Position
            val fallbackPos = fallbackPositionInput.text.toString().trim().toIntOrNull()
            if (fallbackPos == null || fallbackPos !in FALLBACK_POSITION_MIN..FALLBACK_POSITION_MAX) {
                Toast.makeText(this, "Fallback position must be between $FALLBACK_POSITION_MIN and $FALLBACK_POSITION_MAX%", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Gesture Tap Duration
            val gestureDur = gestureTapDurationInput.text.toString().trim().toIntOrNull()
            if (gestureDur == null || gestureDur < GESTURE_TAP_DURATION_MIN_MS || gestureDur > GESTURE_TAP_DURATION_MAX_MS) {
                Toast.makeText(this, "Gesture tap duration must be between $GESTURE_TAP_DURATION_MIN_MS and $GESTURE_TAP_DURATION_MAX_MS ms", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Flash Submenu Delay
            val flashDelay = flashSubmenuDelayInput.text.toString().trim().toIntOrNull()
            if (flashDelay == null || flashDelay < FLASH_SUBMENU_DELAY_MIN_MS || flashDelay > FLASH_SUBMENU_DELAY_MAX_MS) {
                Toast.makeText(this, "Flash submenu delay must be between $FLASH_SUBMENU_DELAY_MIN_MS and $FLASH_SUBMENU_DELAY_MAX_MS ms", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Haptic Duration
            val hapticDur = hapticDurationInput.text.toString().trim().toIntOrNull()
            if (hapticDur == null || hapticDur < HAPTIC_DURATION_MIN_MS || hapticDur > HAPTIC_DURATION_MAX_MS) {
                Toast.makeText(this, "Haptic duration must be between $HAPTIC_DURATION_MIN_MS and $HAPTIC_DURATION_MAX_MS ms", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Default Timer
            val timerSec = defaultTimerInput.text.toString().trim().toIntOrNull()
            if (timerSec == null || timerSec < TIMER_DURATION_MIN_SEC || timerSec > TIMER_DURATION_MAX_SEC) {
                Toast.makeText(this, "Timer duration must be between $TIMER_DURATION_MIN_SEC and $TIMER_DURATION_MAX_SEC seconds", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // All validation passed — save all settings
            settings.setAutoOpenCameraEnabled(autoOpenCameraSwitch.isChecked)
            settings.setCameraLaunchDelayMs(launchDelay)
            settings.setBurstCount(burstCount)
            settings.setShutterFallbackEnabled(shutterFallbackSwitch.isChecked)
            settings.setShutterFallbackPosition(fallbackPos)
            settings.setGestureTapDurationMs(gestureDur)
            settings.setFlashSubmenuDelayMs(flashDelay)
            settings.setHapticDurationMs(hapticDur)
            settings.setDefaultTimerSeconds(timerSec)
            settings.setVibrateOnCountdownEnabled(vibrateCountdownSwitch.isChecked)

            // Sync watch settings via Wearable Data Layer
            syncWatchSettings()

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun syncWatchSettings() {
        try {
            val putReq = com.google.android.gms.wearable.PutDataMapRequest.create(WEARABLE_SETTINGS_PATH).apply {
                dataMap.putInt(KEY_HAPTIC_DURATION_MS, settings.getHapticDurationMs())
                dataMap.putInt(KEY_DEFAULT_TIMER_SECONDS, settings.getDefaultTimerSeconds())
                dataMap.putBoolean(KEY_VIBRATE_ON_COUNTDOWN, settings.isVibrateOnCountdownEnabled())
                dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            }
            com.google.android.gms.wearable.Wearable.getDataClient(this)
                .putDataItem(putReq.asPutDataRequest().setUrgent())
                .addOnSuccessListener {
                    android.util.Log.d(TAG, "Watch settings synced")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e(TAG, "Failed to sync watch settings", e)
                }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to sync watch settings", e)
        }
    }
}
