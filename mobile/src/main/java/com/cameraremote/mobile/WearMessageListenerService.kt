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
        private const val TAG = "WearMessageListener"
        private const val PATH_CAMERA_REMOTE = "/camera_remote"
        private const val PATH_CAMERA_REMOTE_STATUS = "/camera_remote/status"
        private const val COMMAND_OPEN_CAMERA = "open_camera"
        private const val COMMAND_OPEN_VIDEO = "open_video"
        private const val STATUS_VIDEO_CAMERA_OPENED = "video_camera_opened"
        private const val STATUS_CAMERA_OPENED = "camera_opened"
        private const val STATUS_CAMERA_OPEN_FAILED = "camera_open_failed"
        private const val STATUS_SERVICE_NOT_ENABLED = "service_not_enabled"
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
        if (messageEvent.path == PATH_CAMERA_REMOTE) {
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
                if (command == COMMAND_OPEN_CAMERA || command == COMMAND_OPEN_VIDEO) {
                    val action = if (command == COMMAND_OPEN_VIDEO)
                        android.provider.MediaStore.INTENT_ACTION_VIDEO_CAMERA
                    else
                        android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
                    try {
                        startActivity(Intent(action).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        sendStatusToWatch(if (command == COMMAND_OPEN_VIDEO) STATUS_VIDEO_CAMERA_OPENED else STATUS_CAMERA_OPENED)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch camera", e)
                        sendStatusToWatch(STATUS_CAMERA_OPEN_FAILED)
                    }
                } else {
                    sendStatusToWatch(STATUS_SERVICE_NOT_ENABLED)
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
                            PATH_CAMERA_REMOTE_STATUS,
                            status.toByteArray()
                        )
                }
            }
    }
}
