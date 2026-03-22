package com.cameraremote.wear

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TileActionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TileActionActivity"
        private const val EXTRA_COMMAND = "command"
        private const val PREFS_NAME = "watch_settings"
        private const val KEY_HAPTIC_DURATION = "haptic_duration_ms"
        private const val PATH_COMMAND = "/camera_remote"
        private const val DEFAULT_HAPTIC_DURATION_MS = 15
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val command = intent.getStringExtra(EXTRA_COMMAND) ?: run {
            Log.w(TAG, "No command in intent")
            finish()
            return
        }
        Log.d(TAG, "Tile action: $command")

        vibrate()
        sendCommand(command)
    }

    private fun sendCommand(command: String) {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@TileActionActivity).connectedNodes.await()
                val messageClient = Wearable.getMessageClient(this@TileActionActivity)
                for (node in nodes) {
                    messageClient.sendMessage(node.id, PATH_COMMAND, command.toByteArray()).await()
                    Log.d(TAG, "Command '$command' sent to ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command", e)
            } finally {
                finish()
            }
        }
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val durationMs = prefs.getInt(KEY_HAPTIC_DURATION, DEFAULT_HAPTIC_DURATION_MS).toLong()
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }
}
