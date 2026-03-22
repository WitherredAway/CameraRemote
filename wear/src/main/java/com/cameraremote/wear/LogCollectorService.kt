package com.cameraremote.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LogCollectorService : WearableListenerService() {

    companion object {
        private const val TAG = "LogCollectorService"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/request_logcat") {
            Log.d(TAG, "Logcat request received from phone")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "2000"))
                    val logcatOutput = process.inputStream.bufferedReader().readText()
                    val responseBytes = logcatOutput.toByteArray(Charsets.UTF_8)

                    val nodes = Wearable.getNodeClient(this@LogCollectorService).connectedNodes.await()
                    for (node in nodes) {
                        // WearOS MessageClient has ~100KB limit; truncate if needed
                        val payload = if (responseBytes.size > 80_000) {
                            val truncated = logcatOutput.takeLast(78_000)
                            "(Truncated \u2014 showing last 78KB of logcat)\n$truncated".toByteArray(Charsets.UTF_8)
                        } else {
                            responseBytes
                        }
                        Wearable.getMessageClient(this@LogCollectorService)
                            .sendMessage(node.id, "/logcat_response", payload)
                            .await()
                    }
                    Log.d(TAG, "Sent logcat response (${responseBytes.size} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send logcat response", e)
                }
            }
        }
    }
}
