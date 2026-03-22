package com.cameraremote.wear

import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cameraremote.wear.databinding.ActivityRemoteBinding
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RemoteActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "RemoteActivity"
    }

    private lateinit var binding: ActivityRemoteBinding
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vibrator: Vibrator? = null
    private var messageClient: MessageClient? = null
    private var timerSeconds = 3
    private var countdownTimer: CountDownTimer? = null
    private var hapticDurationMs = 30L
    private var vibrateOnCountdown = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Don't apply DynamicColors — it overrides our dark theme with the device's
        // Material You color (which may be green/teal instead of our intended dark bg)
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
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
        Log.d(TAG, "MessageClient initialized: ${messageClient != null}")

        loadSyncedSettings()
        setupButtons()
        Log.d(TAG, "onCreate completed")
    }

    private fun loadSyncedSettings() {
        try {
            Wearable.getDataClient(this).getDataItems()
                .addOnSuccessListener { dataItems ->
                    for (item in dataItems) {
                        if (item.uri.path == "/camera_remote/settings") {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            hapticDurationMs = dataMap.getInt("haptic_duration_ms", 30).toLong()
                            timerSeconds = dataMap.getInt("default_timer_seconds", 3)
                            vibrateOnCountdown = dataMap.getBoolean("vibrate_on_countdown", true)
                            Log.d(TAG, "Loaded settings: haptic=${hapticDurationMs}ms, timer=${timerSeconds}s, vibrateCountdown=$vibrateOnCountdown")
                        }
                    }
                    dataItems.release()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load synced settings", e)
        }
    }

    private fun setupButtons() {
        fun bindButton(button: ImageButton, command: String, label: String) {
            button.setOnClickListener {
                Log.d(TAG, "Button clicked: $label ($command)")
                vibrate()
                sendCommand(command)
            }
            Log.d(TAG, "Button bound: $label -> ${button.id}")
        }

        // Shutter: just captures in whatever mode camera is in
        bindButton(binding.btnCapture, "capture", "Capture")

        // Open camera / photo mode
        bindButton(binding.btnOpenCamera, "open_camera", "Camera")

        // Flash toggle on/off
        bindButton(binding.btnFlash, "toggle_flash", "Flash")

        // Flip / switch camera
        bindButton(binding.btnSwitch, "switch_camera", "Switch")

        // Video mode
        bindButton(binding.btnVideo, "open_video", "Video")

        // Timer: tap to start countdown, long-press to change duration
        binding.btnTimer.setOnClickListener {
            Log.d(TAG, "Timer clicked: ${timerSeconds}s")
            vibrate()
            startCountdown()
        }
        binding.btnTimer.setOnLongClickListener {
            timerSeconds = when (timerSeconds) {
                3 -> 5
                5 -> 10
                else -> 3
            }
            vibrate()
            binding.tvStatus.text = "Timer: ${timerSeconds}s"
            Toast.makeText(this, "Timer: ${timerSeconds}s", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun startCountdown() {
        countdownTimer?.cancel()
        binding.tvStatus.text = "$timerSeconds"
        countdownTimer = object : CountDownTimer(timerSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                runOnUiThread {
                    binding.tvStatus.text = "$secondsLeft"
                }
                if (vibrateOnCountdown) vibrate()
            }
            override fun onFinish() {
                runOnUiThread {
                    binding.tvStatus.text = "Capturing..."
                }
                vibrate()
                sendCommand("capture")
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        try {
            messageClient?.addListener(this)
            Wearable.getDataClient(this).addListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add listeners", e)
        }
        checkConnection()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        try {
            messageClient?.removeListener(this)
            Wearable.getDataClient(this).removeListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove listeners", e)
        }
    }

    private fun checkConnection() {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@RemoteActivity).connectedNodes.await()
                Log.d(TAG, "Connected nodes: ${nodes.size}")
                runOnUiThread {
                    if (nodes.isNotEmpty()) {
                        binding.tvConnection.setBackgroundResource(R.drawable.bg_status_active)
                        binding.tvConnection.text = "Connected to ${nodes.first().displayName}"
                    } else {
                        binding.tvConnection.setBackgroundResource(R.drawable.bg_status_inactive)
                        binding.tvConnection.text = "No phone connected"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check connection", e)
                runOnUiThread {
                    binding.tvConnection.setBackgroundResource(R.drawable.bg_status_inactive)
                    binding.tvConnection.text = "Connection error"
                }
            }
        }
    }

    private fun sendCommand(command: String) {
        Log.d(TAG, "sendCommand: $command")
        binding.tvStatus.text = formatCommand(command)

        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@RemoteActivity).connectedNodes.await()
                Log.d(TAG, "Sending '$command' to ${nodes.size} node(s)")
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected nodes for command: $command")
                    runOnUiThread { binding.tvStatus.text = "No phone connected" }
                    return@launch
                }
                val mc = messageClient ?: run {
                    Log.e(TAG, "MessageClient is null")
                    runOnUiThread { binding.tvStatus.text = "API not available" }
                    return@launch
                }
                for (node in nodes) {
                    mc.sendMessage(node.id, "/camera_remote", command.toByteArray()).await()
                    Log.d(TAG, "Command '$command' sent to ${node.displayName}")
                    runOnUiThread {
                        binding.tvStatus.text = "Sent: ${formatCommand(command)}"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command: $command", e)
                runOnUiThread { binding.tvStatus.text = "Send failed" }
            }
        }
    }

    private fun formatCommand(command: String): String {
        return when (command) {
            "open_camera" -> "Opening camera..."
            "capture" -> "Capturing..."
            "toggle_flash" -> "Flash..."
            "switch_camera" -> "Switching..."
            "open_video" -> "Video mode..."
            else -> command
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/camera_remote/status") {
            val status = String(messageEvent.data)
            Log.d(TAG, "Status received from phone: $status")
            runOnUiThread {
                binding.tvStatus.text = formatStatus(status)
                vibrate()
            }
        }
    }

    private fun formatStatus(status: String): String {
        return when (status) {
            "camera_opened" -> "Camera opened"
            "captured" -> "Captured!"
            "capture_failed" -> "Capture failed"
            "camera_switched" -> "Switched"
            "switch_not_found" -> "Switch N/A"
            "flash_on" -> "Flash ON"
            "flash_off" -> "Flash OFF"
            "flash_not_found" -> "Flash N/A"
            "video_camera_opened" -> "Video mode"
            "video_open_failed" -> "Video failed"
            "camera_open_failed" -> "Can't open camera"
            "service_not_enabled" -> "Enable service!"
            else -> status
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/camera_remote/settings") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                hapticDurationMs = dataMap.getInt("haptic_duration_ms", 30).toLong()
                timerSeconds = dataMap.getInt("default_timer_seconds", 3)
                vibrateOnCountdown = dataMap.getBoolean("vibrate_on_countdown", true)
                Log.d(TAG, "Settings updated: haptic=${hapticDurationMs}ms, timer=${timerSeconds}s, vibrateCountdown=$vibrateOnCountdown")
            }
        }
    }

    private fun vibrate() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(hapticDurationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }
}
