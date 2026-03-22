package com.cameraremote.wear

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import com.cameraremote.wear.databinding.ActivityRemoteBinding
import com.google.android.gms.wearable.DataClient
import com.google.android.material.color.DynamicColors
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

const val DEFAULT_HAPTIC_DURATION_MS = 15
const val DEFAULT_TIMER_SECONDS = 3

class RemoteActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "RemoteActivity"
    }

    private lateinit var binding: ActivityRemoteBinding
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vibrator: Vibrator? = null
    private var messageClient: MessageClient? = null
    private var timerSeconds = DEFAULT_TIMER_SECONDS
    private var countdownTimer: CountDownTimer? = null
    private var isCountdownActive = false
    private var hapticDurationMs = DEFAULT_HAPTIC_DURATION_MS.toLong()
    private var vibrateOnCountdown = true
    private var bezelRotationAccumulator = 0f
    private var lastBezelZoomTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        binding = ActivityRemoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply Material You dynamic color to background only
        applyDynamicBackground()

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
                            hapticDurationMs = dataMap.getInt("haptic_duration_ms", DEFAULT_HAPTIC_DURATION_MS).toLong()
                            timerSeconds = dataMap.getInt("default_timer_seconds", DEFAULT_TIMER_SECONDS)
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

        // Timer: tap to start/cancel countdown, long-press to change duration
        binding.btnTimer.setOnClickListener {
            vibrate()
            if (isCountdownActive) {
                Log.d(TAG, "Timer clicked: cancelling countdown")
                cancelCountdown()
            } else {
                Log.d(TAG, "Timer clicked: starting ${timerSeconds}s countdown")
                startCountdown()
            }
        }
        binding.btnTimer.setOnLongClickListener {
            timerSeconds = when (timerSeconds) {
                3 -> 5
                5 -> 10
                else -> 3
            }
            vibrate()
            binding.tvStatus.text = "Timer: ${timerSeconds}s"
            true
        }
    }

    private fun startCountdown() {
        countdownTimer?.cancel()
        isCountdownActive = true
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
                isCountdownActive = false
                runOnUiThread {
                    binding.tvStatus.text = "Capturing..."
                }
                vibrate()
                sendCommand("capture")
            }
        }.start()
    }

    private fun cancelCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
        isCountdownActive = false
        binding.tvStatus.text = "Cancelled"
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
            "open_camera" -> "Opening..."
            "capture" -> "Capture"
            "toggle_flash" -> "Flash..."
            "switch_camera" -> "Switching..."
            "open_video" -> "Video..."
            "zoom_in" -> "Zoom +"
            "zoom_out" -> "Zoom \u2212"
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
            "camera_opened" -> "Camera ready"
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
                hapticDurationMs = dataMap.getInt("haptic_duration_ms", DEFAULT_HAPTIC_DURATION_MS).toLong()
                timerSeconds = dataMap.getInt("default_timer_seconds", DEFAULT_TIMER_SECONDS)
                vibrateOnCountdown = dataMap.getBoolean("vibrate_on_countdown", true)
                Log.d(TAG, "Settings updated: haptic=${hapticDurationMs}ms, timer=${timerSeconds}s, vibrateCountdown=$vibrateOnCountdown")
            }
        }
    }

    private fun applyDynamicBackground() {
        try {
            val typedArray = obtainStyledAttributes(intArrayOf(
                com.google.android.material.R.attr.colorSurface
            ))
            val surfaceColor = typedArray.getColor(0, Color.BLACK)
            typedArray.recycle()
            binding.root.setBackgroundColor(surfaceColor)
            Log.d(TAG, "Applied Material You dynamic background")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply dynamic background, using black", e)
            binding.root.setBackgroundColor(Color.BLACK)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL)
            bezelRotationAccumulator += delta

            val now = System.currentTimeMillis()
            val threshold = 1.0f
            val debounceMs = 150L

            if (Math.abs(bezelRotationAccumulator) >= threshold && (now - lastBezelZoomTime) >= debounceMs) {
                if (bezelRotationAccumulator > 0) {
                    Log.d(TAG, "Bezel: zoom in (accumulated=${bezelRotationAccumulator})")
                    sendCommand("zoom_in")
                } else {
                    Log.d(TAG, "Bezel: zoom out (accumulated=${bezelRotationAccumulator})")
                    sendCommand("zoom_out")
                }
                bezelRotationAccumulator = 0f
                lastBezelZoomTime = now
                vibrate()
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun vibrate() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(hapticDurationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }
}
