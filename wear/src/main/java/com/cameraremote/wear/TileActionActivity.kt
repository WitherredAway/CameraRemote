package com.cameraremote.wear

import android.app.Activity
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.google.android.gms.wearable.Wearable

class TileActionActivity : Activity() {

    companion object {
        const val TAG = "TileActionActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val command = intent.getStringExtra("command")
        if (command != null) {
            Log.d(TAG, "Tile action: $command")
            vibrate()
            sendCommand(command)
        }

        finish()
    }

    private fun sendCommand(command: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(this).sendMessage(
                    node.id,
                    "/camera_remote",
                    command.toByteArray()
                ).addOnSuccessListener {
                    Log.d(TAG, "Command '$command' sent to ${node.displayName}")
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send command", e)
                }
            }
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
