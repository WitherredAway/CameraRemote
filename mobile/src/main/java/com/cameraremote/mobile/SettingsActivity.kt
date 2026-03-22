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
            if (launchDelay == null || launchDelay < 100 || launchDelay > 10000) {
                Toast.makeText(this, "Camera launch delay must be between 100 and 10000 ms", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Burst Count
            val burstCount = burstCountInput.text.toString().trim().toIntOrNull()
            if (burstCount == null || burstCount < 1 || burstCount > 50) {
                Toast.makeText(this, "Burst photo count must be between 1 and 50", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Fallback Position
            val fallbackPos = fallbackPositionInput.text.toString().trim().toIntOrNull()
            if (fallbackPos == null || fallbackPos !in 10..99) {
                Toast.makeText(this, "Fallback position must be between 10 and 99%", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Gesture Tap Duration
            val gestureDur = gestureTapDurationInput.text.toString().trim().toIntOrNull()
            if (gestureDur == null || gestureDur < 10 || gestureDur > 500) {
                Toast.makeText(this, "Gesture tap duration must be between 10 and 500 ms", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Flash Submenu Delay
            val flashDelay = flashSubmenuDelayInput.text.toString().trim().toIntOrNull()
            if (flashDelay == null || flashDelay < 50 || flashDelay > 2000) {
                Toast.makeText(this, "Flash submenu delay must be between 50 and 2000 ms", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Haptic Duration
            val hapticDur = hapticDurationInput.text.toString().trim().toIntOrNull()
            if (hapticDur == null || hapticDur < 5 || hapticDur > 500) {
                Toast.makeText(this, "Haptic duration must be between 5 and 500 ms", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate Default Timer
            val timerSec = defaultTimerInput.text.toString().trim().toIntOrNull()
            if (timerSec == null || timerSec < 1 || timerSec > 60) {
                Toast.makeText(this, "Timer duration must be between 1 and 60 seconds", Toast.LENGTH_SHORT).show()
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
            val putReq = com.google.android.gms.wearable.PutDataMapRequest.create("/camera_remote/settings").apply {
                dataMap.putInt("haptic_duration_ms", settings.getHapticDurationMs())
                dataMap.putInt("default_timer_seconds", settings.getDefaultTimerSeconds())
                dataMap.putBoolean("vibrate_on_countdown", settings.isVibrateOnCountdownEnabled())
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            com.google.android.gms.wearable.Wearable.getDataClient(this)
                .putDataItem(putReq.asPutDataRequest().setUrgent())
                .addOnSuccessListener {
                    android.util.Log.d("SettingsActivity", "Watch settings synced")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("SettingsActivity", "Failed to sync watch settings", e)
                }
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Failed to sync watch settings", e)
        }
    }
}
