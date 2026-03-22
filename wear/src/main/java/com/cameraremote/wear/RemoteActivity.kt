package com.cameraremote.wear

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RemoteActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "RemoteActivity"

        // Defaults
        private const val DEFAULT_HAPTIC_DURATION_MS = 15
        private const val DEFAULT_TIMER_SECONDS = 3

        // Timing
        private const val RECORDING_TIMER_INTERVAL_MS = 500L
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val COUNTDOWN_TICK_MS = 1000L
        private const val MS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60

        // Timer duration cycle (long-press to cycle)
        private val TIMER_DURATION_OPTIONS = intArrayOf(3, 5, 10)

        // SharedPreferences
        private const val PREFS_NAME = "watch_settings"
        private const val KEY_HAPTIC_DURATION = "haptic_duration_ms"
        private const val KEY_TIMER_SECONDS = "default_timer_seconds"
        private const val KEY_VIBRATE_COUNTDOWN = "vibrate_on_countdown"

        // Message/Data paths
        private const val PATH_COMMAND = "/camera_remote"
        private const val PATH_STATUS = "/camera_remote/status"
        private const val PATH_SETTINGS = "/camera_remote/settings"
        private const val PATH_PREVIEW = "/camera_remote/preview"

        // Zoom
        private const val ZOOM_INTERVAL_MS = 200L
    }

    private lateinit var binding: ActivityRemoteBinding
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + scopeJob)
    private var vibrator: Vibrator? = null
    private var messageClient: MessageClient? = null
    private var timerSeconds = DEFAULT_TIMER_SECONDS
    private var countdownTimer: CountDownTimer? = null
    private var isCountdownActive = false
    private var hapticDurationMs = DEFAULT_HAPTIC_DURATION_MS.toLong()
    private var vibrateOnCountdown = true
    private var captureCount = 0
    private var lastPreviewUri: String? = null
    private var isRecording = false
    private var recordingStartTime = 0L
    private val recordingTimerHandler = Handler(Looper.getMainLooper())
    private val recordingTimerRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                val elapsed = (System.currentTimeMillis() - recordingStartTime) / MS_PER_SECOND
                val mins = elapsed / SECONDS_PER_MINUTE
                val secs = elapsed % SECONDS_PER_MINUTE
                binding.tvStatus.text = "\u25CF REC ${String.format("%02d:%02d", mins, secs)}"
                recordingTimerHandler.postDelayed(this, RECORDING_TIMER_INTERVAL_MS)
            }
        }
    }
    private val zoomQueue = mutableListOf<String>()
    private var isProcessingZoom = false
    private var lastZoomSentTime = 0L
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            checkConnection()
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

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
        // Load from local cache first
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        hapticDurationMs = prefs.getInt(KEY_HAPTIC_DURATION, DEFAULT_HAPTIC_DURATION_MS).toLong()
        timerSeconds = prefs.getInt(KEY_TIMER_SECONDS, DEFAULT_TIMER_SECONDS)
        vibrateOnCountdown = prefs.getBoolean(KEY_VIBRATE_COUNTDOWN, true)

        // Then try to load latest from DataClient
        try {
            Wearable.getDataClient(this).getDataItems()
                .addOnSuccessListener { dataItems ->
                    for (item in dataItems) {
                        if (item.uri.path == PATH_SETTINGS) {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            hapticDurationMs = dataMap.getInt(KEY_HAPTIC_DURATION, DEFAULT_HAPTIC_DURATION_MS).toLong()
                            timerSeconds = dataMap.getInt(KEY_TIMER_SECONDS, DEFAULT_TIMER_SECONDS)
                            vibrateOnCountdown = dataMap.getBoolean(KEY_VIBRATE_COUNTDOWN, true)
                            saveSettingsLocally()
                            Log.d(TAG, "Loaded settings: haptic=${hapticDurationMs}ms, timer=${timerSeconds}s, vibrateCountdown=$vibrateOnCountdown")
                        }
                    }
                    dataItems.release()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load synced settings", e)
        }
    }

    private fun saveSettingsLocally() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putInt(KEY_HAPTIC_DURATION, hapticDurationMs.toInt())
            .putInt(KEY_TIMER_SECONDS, timerSeconds)
            .putBoolean(KEY_VIBRATE_COUNTDOWN, vibrateOnCountdown)
            .apply()
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

        // Preview button
        binding.btnPreview.setOnClickListener {
            vibrate()
            sendCommand("preview_capture")
        }

        // Burst Timer button: countdown then burst capture
        binding.btnBurstTimer.setOnClickListener {
            vibrate()
            if (isCountdownActive) {
                // Cancel active countdown
                countdownTimer?.cancel()
                isCountdownActive = false
                binding.tvStatus.text = "Cancelled"
                return@setOnClickListener
            }
            isCountdownActive = true
            val totalMs = timerSeconds * COUNTDOWN_TICK_MS
            countdownTimer = object : CountDownTimer(totalMs, COUNTDOWN_TICK_MS) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = (millisUntilFinished / MS_PER_SECOND) + 1
                    runOnUiThread { binding.tvStatus.text = "Burst $secondsLeft\u2026" }
                    if (vibrateOnCountdown) vibrate()
                }
                override fun onFinish() {
                    isCountdownActive = false
                    sendCommand("burst_capture")
                    runOnUiThread { binding.tvStatus.text = "Burst!" }
                    vibrate()
                }
            }.start()
        }

        // Preview overlay buttons
        binding.btnPreviewSave.setOnClickListener {
            vibrate()
            binding.previewOverlay.visibility = View.GONE
            binding.tvStatus.text = "Saved"
        }
        binding.btnPreviewDelete.setOnClickListener {
            vibrate()
            lastPreviewUri?.let { uri ->
                sendCommand("delete_preview:$uri")
            }
            binding.previewOverlay.visibility = View.GONE
            binding.tvStatus.text = "Deleted"
        }

        // Shutter: tap to capture, long-press for burst mode
        bindButton(binding.btnCapture, "capture", "Capture")
        binding.btnCapture.setOnLongClickListener {
            vibrate()
            sendCommand("burst_capture")
            true
        }

        // Open camera / photo mode, long-press for gallery
        bindButton(binding.btnOpenCamera, "open_camera", "Camera")
        binding.btnOpenCamera.setOnLongClickListener {
            vibrate()
            sendCommand("open_gallery")
            true
        }

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
        countdownTimer = object : CountDownTimer(timerSeconds * COUNTDOWN_TICK_MS, COUNTDOWN_TICK_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / MS_PER_SECOND).toInt() + 1
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
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        try {
            messageClient?.removeListener(this)
            Wearable.getDataClient(this).removeListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove listeners", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
        countdownTimer?.cancel()
        scopeJob.cancel()
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
        return when {
            command == "open_camera" -> "Opening..."
            command == "capture" -> "Capture"
            command == "toggle_flash" -> "Flash..."
            command == "switch_camera" -> "Switching..."
            command == "open_video" -> "Video..."
            command.startsWith("zoom_in") -> "Zoom +"
            command.startsWith("zoom_out") -> "Zoom \u2212"
            else -> command
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_STATUS) {
            val status = String(messageEvent.data)
            Log.d(TAG, "Status received from phone: $status")
            if (status == "captured") captureCount++

            // Handle recording state
            if (status == "recording_started") {
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                runOnUiThread {
                    binding.tvStatus.text = "\u25CF REC 00:00"
                    recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
                    recordingTimerHandler.postDelayed(recordingTimerRunnable, RECORDING_TIMER_INTERVAL_MS)
                    vibrate()
                }
                return
            }
            if (status == "recording_stopped") {
                isRecording = false
                recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
                runOnUiThread {
                    binding.tvStatus.text = "Recording saved"
                    vibrate()
                }
                return
            }

            // Stop recording timer if any other status comes in
            if (isRecording && status != "recording_started") {
                isRecording = false
                recordingTimerHandler.removeCallbacks(recordingTimerRunnable)
            }

            runOnUiThread {
                val display = if (status == "captured" && captureCount > 1) {
                    "${formatStatus(status)} ($captureCount)"
                } else {
                    formatStatus(status)
                }
                binding.tvStatus.text = display
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
            "recording_started" -> "\u25CF REC 00:00"
            "recording_stopped" -> "Recording saved"
            "video_camera_opened" -> "Video mode"
            "video_open_failed" -> "Video failed"
            "camera_open_failed" -> "Can't open camera"
            "camera_not_open" -> "Open camera first"
            "no_camera_app" -> "No camera app"
            "photo_mode" -> "Switching to photo..."
            "shutter_not_found" -> "Shutter N/A"
            "unknown_command" -> "Unknown command"
            "service_not_enabled" -> "Enable service!"
            "gallery_opened" -> "Gallery"
            "gallery_failed" -> "Gallery N/A"
            "camera_detected" -> "Camera ready"
            "preview_capturing" -> "Capturing\u2026"
            "preview_ready" -> "Preview"
            "preview_failed" -> "Preview N/A"
            "preview_deleted" -> "Deleted"
            "preview_delete_failed" -> "Delete failed"
            "preview_delete_denied" -> "Delete denied"
            "preview_delete_cancelled" -> "Delete cancelled"
            else -> {
                // Handle dynamic statuses like "burst_5", "timer_3s"
                when {
                    status.startsWith("burst_") && status.contains("_of_") -> {
                        // burst_2_of_5 -> "Burst 2/5"
                        val parts = status.removePrefix("burst_").split("_of_")
                        "Burst ${parts[0]}/${parts[1]}"
                    }
                    status.startsWith("burst_") -> {
                        val count = status.removePrefix("burst_").toIntOrNull() ?: 0
                        "Burst $count\u00D7"
                    }
                    status.startsWith("timer_") -> {
                        val sec = status.removePrefix("timer_").removeSuffix("s")
                        "Timer $sec\u2026"
                    }
                    else -> status.replace("_", " ").replaceFirstChar { it.uppercase() }
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                when (event.dataItem.uri.path) {
                    PATH_SETTINGS -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        hapticDurationMs = dataMap.getInt(KEY_HAPTIC_DURATION, DEFAULT_HAPTIC_DURATION_MS).toLong()
                        timerSeconds = dataMap.getInt(KEY_TIMER_SECONDS, DEFAULT_TIMER_SECONDS)
                        vibrateOnCountdown = dataMap.getBoolean(KEY_VIBRATE_COUNTDOWN, true)
                        saveSettingsLocally()
                        Log.d(TAG, "Settings updated: haptic=${hapticDurationMs}ms, timer=${timerSeconds}s, vibrateCountdown=$vibrateOnCountdown")
                    }
                    PATH_PREVIEW -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val imageBytes = dataMap.getByteArray("image")
                        val uri = dataMap.getString("uri")
                        if (imageBytes != null && imageBytes.isNotEmpty()) {
                            lastPreviewUri = uri
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            runOnUiThread {
                                binding.previewImage.setImageBitmap(bitmap)
                                binding.previewOverlay.visibility = View.VISIBLE
                                binding.tvStatus.text = "Preview"
                            }
                            Log.d(TAG, "Preview received: ${imageBytes.size} bytes, uri=$uri")
                        }
                    }
                }
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

    private val processZoomRunnable = object : Runnable {
        override fun run() {
            if (zoomQueue.isNotEmpty()) {
                // Batch all consecutive same-direction ticks into one command
                val direction = zoomQueue.removeAt(0)
                var steps = 1
                while (zoomQueue.isNotEmpty() && zoomQueue[0] == direction) {
                    zoomQueue.removeAt(0)
                    steps++
                }
                // Scale steps with power curve: slow=precise (1→1), fast=accelerated (5→11, 10→32)
                val scaledSteps = Math.ceil(Math.pow(steps.toDouble(), 1.5)).toInt()
                val dir = if (direction.startsWith("zoom_in")) "zoom_in" else "zoom_out"
                sendCommand("$dir:$scaledSteps")
                lastZoomSentTime = System.currentTimeMillis()
                // Always schedule next check — more ticks may arrive during the wait
                heartbeatHandler.postDelayed(this, ZOOM_INTERVAL_MS)
            } else {
                isProcessingZoom = false
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL)

            // Queue rotation ticks — they arrive ~10ms apart from bezel
            if (delta > 0) {
                zoomQueue.add("zoom_in:1")
            } else if (delta < 0) {
                zoomQueue.add("zoom_out:1")
            }

            if (!isProcessingZoom) {
                isProcessingZoom = true
                // Ensure minimum interval since last command sent
                val elapsed = System.currentTimeMillis() - lastZoomSentTime
                val delay = maxOf(0L, ZOOM_INTERVAL_MS - elapsed)
                heartbeatHandler.postDelayed(processZoomRunnable, delay)
            }
            vibrate()
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
