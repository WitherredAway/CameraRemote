package com.cameraremote.wear

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.MotionEvent
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.cameraremote.wear.databinding.ActivityRemoteBinding
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RemoteActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "RemoteActivity"
    }

    private lateinit var binding: ActivityRemoteBinding
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vibrator: Vibrator? = null
    private var messageClient: MessageClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vibrator = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrator not available", e)
            null
        }

        messageClient = try {
            Wearable.getMessageClient(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MessageClient", e)
            null
        }

        // Rotary/bezel scrolling support
        binding.scrollView.requestFocus()
        binding.scrollView.setOnGenericMotionListener { _, event ->
            if (event.action == MotionEvent.ACTION_SCROLL) {
                val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * 50
                binding.scrollView.smoothScrollBy(0, delta.toInt())
                true
            } else false
        }

        binding.btnOpenCamera.setOnClickListener { sendCommand("open_camera") }
        binding.btnCapture.setOnClickListener { sendCommand("take_photo") }
        binding.btnTimer.setOnClickListener { sendCommand("take_photo_timer") }
        binding.btnFlash.setOnClickListener { sendCommand("toggle_flash") }
        binding.btnSwitch.setOnClickListener { sendCommand("switch_camera") }
        binding.btnVideo.setOnClickListener { sendCommand("open_video") }
    }

    override fun onResume() {
        super.onResume()
        try {
            messageClient?.addListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add message listener", e)
        }
        checkConnection()
    }

    override fun onPause() {
        super.onPause()
        try {
            messageClient?.removeListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove message listener", e)
        }
    }

    private fun checkConnection() {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@RemoteActivity).connectedNodes.await()
                runOnUiThread {
                    if (nodes.isNotEmpty()) {
                        binding.statusCard.setBackgroundResource(R.drawable.bg_status_active)
                        binding.statusIcon.setImageResource(R.drawable.ic_check_circle)
                        binding.tvConnection.text = "Connected to ${nodes.first().displayName}"
                    } else {
                        binding.statusCard.setBackgroundResource(R.drawable.bg_status_inactive)
                        binding.statusIcon.setImageResource(R.drawable.ic_error_circle)
                        binding.tvConnection.text = "No phone connected"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check connection", e)
                runOnUiThread {
                    binding.statusCard.setBackgroundResource(R.drawable.bg_status_inactive)
                    binding.statusIcon.setImageResource(R.drawable.ic_error_circle)
                    binding.tvConnection.text = "Connection error"
                }
            }
        }
    }

    private fun sendCommand(command: String) {
        vibrate()
        binding.tvStatus.text = formatCommand(command)

        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@RemoteActivity).connectedNodes.await()
                if (nodes.isEmpty()) {
                    runOnUiThread { binding.tvStatus.text = "No phone connected" }
                    return@launch
                }
                val mc = messageClient ?: run {
                    runOnUiThread { binding.tvStatus.text = "API not available" }
                    return@launch
                }
                for (node in nodes) {
                    mc.sendMessage(node.id, "/camera_remote", command.toByteArray()).await()
                    Log.d(TAG, "Command '$command' sent to ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command", e)
                runOnUiThread { binding.tvStatus.text = "Send failed" }
            }
        }
    }

    private fun formatCommand(command: String): String {
        return when (command) {
            "open_camera" -> "Opening camera..."
            "take_photo" -> "Taking photo..."
            "take_photo_timer" -> "3s timer..."
            "toggle_flash" -> "Toggling flash..."
            "switch_camera" -> "Switching camera..."
            "open_video" -> "Opening video..."
            else -> command
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/camera_remote/status") {
            val status = String(messageEvent.data)
            runOnUiThread {
                binding.tvStatus.text = formatStatus(status)
                vibrate()
            }
        }
    }

    private fun formatStatus(status: String): String {
        return when (status) {
            "camera_opened" -> "Camera opened"
            "photo_taken" -> "Photo taken!"
            "photo_failed" -> "Photo failed"
            "timer_started" -> "Timer: 3..."
            "camera_switched" -> "Camera switched"
            "switch_not_found" -> "Switch not found"
            "flash_toggled" -> "Flash toggled"
            "flash_not_found" -> "Flash not found"
            "video_camera_opened" -> "Video mode"
            "video_open_failed" -> "Video failed"
            "camera_open_failed" -> "Can't open camera"
            "service_not_enabled" -> "Enable service on phone!"
            else -> status
        }
    }

    private fun vibrate() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }
}
