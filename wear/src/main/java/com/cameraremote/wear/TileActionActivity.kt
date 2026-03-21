package com.cameraremote.wear

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.google.android.gms.wearable.Wearable

/**
 * Transparent activity launched by tile button clicks.
 * Sends the command to the phone and finishes immediately.
 */
class TileActionActivity : Activity() {

    companion object {
        private const val TAG = "TileAction"
        private const val PATH_CAMERA_REMOTE = "/camera_remote"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val command = intent.getStringExtra("command")
        if (command != null) {
            Log.d(TAG, "Tile action: $command")
            sendCommand(command)
        }

        finish()
    }

    private fun sendCommand(command: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                val node = nodes.first()
                Wearable.getMessageClient(this)
                    .sendMessage(node.id, PATH_CAMERA_REMOTE, command.toByteArray())
                    .addOnSuccessListener {
                        Log.d(TAG, "Sent command: $command")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send: $command", e)
                    }
            } else {
                Log.w(TAG, "No connected phone found")
            }
        }
    }
}
