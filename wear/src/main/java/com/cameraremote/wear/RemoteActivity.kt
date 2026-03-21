package com.cameraremote.wear

import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.cameraremote.wear.databinding.ActivityRemoteBinding

class RemoteActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener {

    private lateinit var binding: ActivityRemoteBinding
    private var connectedNode: Node? = null
    private var isRecording = false
    private var flashOn = false

    companion object {
        private const val TAG = "WearRemote"
        const val PATH_CAMERA_REMOTE = "/camera_remote"
        const val PATH_STATUS = "/camera_remote/status"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        findConnectedPhone()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
        findConnectedPhone()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    private fun setupButtons() {
        binding.btnTakePhoto.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            sendCommand("take_photo")
            updateStatus("Taking photo...")
        }

        binding.btnTimer.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            sendCommand("take_photo_timer")
            updateStatus("Timer: 3s...")
        }

        binding.btnFlash.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            flashOn = !flashOn
            sendCommand("toggle_flash")
            binding.btnFlash.text = if (flashOn) "FLASH ON" else "FLASH"
            updateStatus("Flash ${if (flashOn) "ON" else "OFF"}")
        }

        binding.btnSwitchCamera.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            sendCommand("switch_camera")
            updateStatus("Switching camera...")
        }

        binding.btnRecord.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            if (isRecording) {
                sendCommand("stop_video")
                isRecording = false
                binding.btnRecord.text = "REC"
                updateStatus("Stopping recording...")
            } else {
                sendCommand("start_video")
                isRecording = true
                binding.btnRecord.text = "STOP"
                updateStatus("Recording...")
            }
        }

        binding.btnZoomIn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            sendCommand("zoom_in")
            updateStatus("Zoom +")
        }

        binding.btnZoomOut.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            sendCommand("zoom_out")
            updateStatus("Zoom -")
        }
    }

    private fun findConnectedPhone() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                connectedNode = nodes.first()
                updateStatus("Connected")
                Log.d(TAG, "Connected to: ${connectedNode?.displayName}")
            } else {
                connectedNode = null
                updateStatus("No phone connected")
                Log.w(TAG, "No connected nodes found")
            }
        }.addOnFailureListener {
            updateStatus("Connection failed")
            Log.e(TAG, "Failed to get nodes", it)
        }
    }

    fun sendCommand(command: String) {
        val node = connectedNode
        if (node == null) {
            updateStatus("No phone found!")
            Toast.makeText(this, "Phone not connected", Toast.LENGTH_SHORT).show()
            findConnectedPhone()
            return
        }

        Wearable.getMessageClient(this)
            .sendMessage(node.id, PATH_CAMERA_REMOTE, command.toByteArray())
            .addOnSuccessListener {
                Log.d(TAG, "Sent command: $command")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send command: $command", e)
                updateStatus("Send failed!")
                Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateStatus(text: String) {
        runOnUiThread {
            binding.tvStatus.text = text
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_STATUS) {
            val status = String(messageEvent.data)
            Log.d(TAG, "Status from phone: $status")
            runOnUiThread {
                when {
                    status == "photo_taken" -> updateStatus("Photo saved!")
                    status == "photo_failed" -> updateStatus("Photo failed!")
                    status == "recording_started" -> {
                        isRecording = true
                        binding.btnRecord.text = "STOP"
                        updateStatus("Recording...")
                    }
                    status == "recording_stopped" -> {
                        isRecording = false
                        binding.btnRecord.text = "REC"
                        updateStatus("Video saved!")
                    }
                    status.startsWith("flash_") -> {
                        flashOn = status == "flash_ON"
                        binding.btnFlash.text = if (flashOn) "FLASH ON" else "FLASH"
                        updateStatus("Flash ${if (flashOn) "ON" else "OFF"}")
                    }
                    status.startsWith("camera_switched_") -> {
                        val cam = status.removePrefix("camera_switched_")
                        updateStatus("$cam camera")
                    }
                    status.startsWith("zoom_") -> {
                        val zoom = status.removePrefix("zoom_")
                        updateStatus("Zoom: ${zoom}x")
                    }
                }
            }
        }
    }
}
