package com.cameraremote.mobile

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Background service that listens for messages from the watch.
 * If the CameraActivity is not running, it launches it first,
 * then forwards the command.
 */
class WearMessageListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearMsgListener"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: path=${messageEvent.path}")

        if (messageEvent.path == CameraActivity.PATH_CAMERA_REMOTE) {
            val command = String(messageEvent.data)
            Log.d(TAG, "Command: $command")

            val activity = CameraActivity.instance
            if (activity != null) {
                activity.runOnUiThread {
                    when (command) {
                        CameraActivity.CMD_TAKE_PHOTO -> activity.takePhoto()
                        CameraActivity.CMD_TAKE_PHOTO_TIMER -> activity.takePhotoWithTimer()
                        CameraActivity.CMD_TOGGLE_FLASH -> activity.toggleFlash()
                        CameraActivity.CMD_SWITCH_CAMERA -> activity.switchCamera()
                        CameraActivity.CMD_START_VIDEO -> activity.startVideoRecording()
                        CameraActivity.CMD_STOP_VIDEO -> activity.stopVideoRecording()
                        CameraActivity.CMD_ZOOM_IN -> activity.zoomIn()
                        CameraActivity.CMD_ZOOM_OUT -> activity.zoomOut()
                    }
                }
            } else {
                val intent = Intent(this, CameraActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("command", command)
                }
                startActivity(intent)
            }
        }
    }
}
