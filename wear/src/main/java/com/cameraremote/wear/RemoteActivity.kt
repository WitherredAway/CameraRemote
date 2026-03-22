package com.cameraremote.wear

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable

class RemoteActivity : FragmentActivity(), MessageClient.OnMessageReceivedListener {

    private lateinit var tvStatus: TextView
    private lateinit var tvConnection: TextView
    private lateinit var vibrator: Vibrator
    private lateinit var messageClient: MessageClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote)

        tvStatus = findViewById(R.id.tvStatus)
        tvConnection = findViewById(R.id.tvConnection)
        vibrator = getSystemService(Vibrator::class.java)
        messageClient = Wearable.getMessageClient(this)

        // Camera controls
        findViewById<Button>(R.id.btnOpenCamera).setOnClickListener {
            sendCommand("open_camera")
        }
        findViewById<Button>(R.id.btnCapture).setOnClickListener {
            sendCommand("take_photo")
        }
        findViewById<Button>(R.id.btnTimer).setOnClickListener {
            sendCommand("take_photo_timer")
        }
        findViewById<Button>(R.id.btnFlash).setOnClickListener {
            sendCommand("toggle_flash")
        }
        findViewById<Button>(R.id.btnSwitch).setOnClickListener {
            sendCommand("switch_camera")
        }
        findViewById<Button>(R.id.btnVideo).setOnClickListener {
            sendCommand("open_video")
        }
    }

    override fun onResume() {
        super.onResume()
        messageClient.addListener(this)
        checkConnection()
    }

    override fun onPause() {
        super.onPause()
        messageClient.removeListener(this)
    }

    private fun checkConnection() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                tvConnection.text = "Connected"
                tvConnection.setTextColor(getColor(android.R.color.holo_green_light))
            } else {
                tvConnection.text = "Not connected"
                tvConnection.setTextColor(getColor(android.R.color.holo_red_light))
            }
        }.addOnFailureListener {
            tvConnection.text = "Error"
            tvConnection.setTextColor(getColor(android.R.color.holo_red_light))
        }
    }

    private fun sendCommand(command: String) {
        vibrate()
        tvStatus.text = "Sending..."

        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                tvStatus.text = "No phone connected"
                return@addOnSuccessListener
            }
            for (node in nodes) {
                messageClient.sendMessage(
                    node.id,
                    "/camera_remote",
                    command.toByteArray()
                ).addOnSuccessListener {
                    tvStatus.text = formatCommand(command)
                }.addOnFailureListener {
                    tvStatus.text = "Send failed"
                }
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
                tvStatus.text = formatStatus(status)
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
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
