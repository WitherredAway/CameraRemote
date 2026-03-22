package com.cameraremote.mobile

import android.app.Activity
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import com.google.android.gms.wearable.Wearable

class DeletePhotoActivity : Activity() {

    companion object {
        const val EXTRA_URI = "photo_uri"
        private const val TAG = "DeletePhotoActivity"
        private const val DELETE_REQUEST_CODE = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriStr = intent.getStringExtra(EXTRA_URI)
        if (uriStr == null) {
            Log.e(TAG, "No URI provided")
            sendStatus("preview_delete_failed")
            finish()
            return
        }

        val uri = Uri.parse(uriStr)
        Log.d(TAG, "Attempting to delete: $uri")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: use createDeleteRequest for user confirmation
            try {
                val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                startIntentSenderForResult(
                    deleteRequest.intentSender,
                    DELETE_REQUEST_CODE,
                    null, 0, 0, 0
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create delete request", e)
                sendStatus("preview_delete_failed")
                finish()
            }
        } else {
            // Android 10 and below: direct delete
            try {
                val deleted = contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    sendStatus("preview_deleted")
                    Log.d(TAG, "Preview deleted: $uriStr")
                } else {
                    sendStatus("preview_delete_failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete", e)
                sendStatus("preview_delete_failed")
            }
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DELETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                sendStatus("preview_deleted")
                Log.d(TAG, "User approved deletion")
            } else {
                sendStatus("preview_delete_cancelled")
                Log.d(TAG, "User denied deletion")
            }
            finish()
        }
    }

    private fun sendStatus(status: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(
                    node.id,
                    "/camera_remote/status",
                    status.toByteArray()
                )
            }
        }
    }
}
