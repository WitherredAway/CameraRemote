package com.cameraremote.wear

import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LogCollectorService : WearableListenerService() {

    companion object {
        private const val TAG = "LogCollectorService"
        private const val PATH_LOG_REQUEST = "/camera_remote/log_request"
        private const val PATH_LOG_RESPONSE = "/camera_remote/log_response"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_LOG_REQUEST) {
            Log.d(TAG, "Log collection request received from phone")
            scope.launch {
                val logs = collectWatchLogs()
                sendLogsToPhone(logs, messageEvent.sourceNodeId)
            }
        }
    }

    private fun collectWatchLogs(): String {
        val sb = StringBuilder()
        sb.appendLine("Watch: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Wear OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            sb.appendLine("Watch app version: ${pInfo.versionName}")
        } catch (_: Exception) {}
        sb.appendLine()

        try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "logcat", "-d", "-t", "1000",
                "RemoteActivity:*", "TileActionActivity:*",
                "LogCollectorService:*", "CameraRemoteTile:*", "*:S"
            ))
            val reader = process.inputStream.bufferedReader()
            val lines = reader.readLines()
            reader.close()
            process.waitFor()
            if (lines.isEmpty()) {
                sb.appendLine("(no watch logs found)")
            } else {
                for (line in lines) {
                    sb.appendLine(line)
                }
            }
        } catch (e: Exception) {
            sb.appendLine("Failed to collect watch logs: ${e.message}")
        }

        return sb.toString()
    }

    private suspend fun sendLogsToPhone(logs: String, nodeId: String) {
        try {
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, PATH_LOG_RESPONSE, logs.toByteArray())
                .await()
            Log.d(TAG, "Watch logs sent to phone (${logs.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send watch logs to phone", e)
        }
    }
}
