package com.cameraremote.mobile

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearMessageListenerService : WearableListenerService() {

    companion object {
        const val TAG = "WearMessageListener"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/camera_remote") {
            val command = String(messageEvent.data)
            Log.d(TAG, "Received command from watch: $command")

            if (CameraControlService.isRunning) {
                // Send command to the AccessibilityService via broadcast
                val intent = Intent(CameraControlService.ACTION_CAMERA_COMMAND).apply {
                    setPackage(packageName)
                    putExtra(CameraControlService.EXTRA_COMMAND, command)
                }
                sendBroadcast(intent)
            } else {
                Log.w(TAG, "AccessibilityService not running. Command ignored: $command")
                // If open_camera was requested, we can still do that without accessibility service
                if (command == "open_camera") {
                    val launchIntent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch camera", e)
                    }
                }
                // Send status that service isn't enabled
                sendServiceNotRunningStatus(messageEvent)
            }
        }
    }

    private fun sendServiceNotRunningStatus(messageEvent: MessageEvent) {
        val command = String(messageEvent.data)
        if (command != "open_camera") {
            com.google.android.gms.wearable.Wearable.getNodeClient(this).connectedNodes
                .addOnSuccessListener { nodes ->
                    for (node in nodes) {
                        com.google.android.gms.wearable.Wearable.getMessageClient(this)
                            .sendMessage(
                                node.id,
                                "/camera_remote/status",
                                "service_not_enabled".toByteArray()
                            )
                    }
                }
        }
    }
}
