package com.cameraremote.mobile

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

class WearMessageListenerService : WearableListenerService() {

    companion object {
        const val TAG = "WearMessageListener"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WearMessageListenerService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WearMessageListenerService destroyed")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived: path=${messageEvent.path}, dataSize=${messageEvent.data.size}")
        if (messageEvent.path == "/camera_remote") {
            val command = String(messageEvent.data)
            Log.d(TAG, "Received command from watch: $command")

            val service = CameraControlService.instance
            if (service != null) {
                Log.d(TAG, "Forwarding command to CameraControlService: $command")
                // Run on main thread since AccessibilityService needs it
                Handler(Looper.getMainLooper()).post {
                    service.handleCommand(command)
                }
            } else {
                Log.w(TAG, "AccessibilityService not running. Command ignored: $command")
                // If open_camera or open_video was requested, we can still launch without service
                if (command == "open_camera" || command == "open_video") {
                    val action = if (command == "open_video")
                        android.provider.MediaStore.INTENT_ACTION_VIDEO_CAMERA
                    else
                        android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
                    try {
                        startActivity(Intent(action).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        sendStatusToWatch(if (command == "open_video") "video_camera_opened" else "camera_opened")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch camera", e)
                        sendStatusToWatch("camera_open_failed")
                    }
                } else {
                    sendStatusToWatch("service_not_enabled")
                }
            }
        }
    }

    private fun sendStatusToWatch(status: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                for (node in nodes) {
                    Wearable.getMessageClient(this)
                        .sendMessage(
                            node.id,
                            "/camera_remote/status",
                            status.toByteArray()
                        )
                }
            }
    }
}
