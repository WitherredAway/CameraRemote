package com.cameraremote.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable

class CameraControlService : AccessibilityService() {

    companion object {
        const val TAG = "CameraControlService"
        var instance: CameraControlService? = null
            private set
        val isRunning: Boolean get() = instance != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var messageClient: MessageClient
    private lateinit var settings: SettingsManager

    // Common content descriptions for camera controls across popular camera apps
    private val shutterDescriptions = listOf(
        "shutter", "take photo", "capture", "camera button", "take picture",
        "shoot", "snap", "photo", "photograph", "shutter button",
        "capture button", "camera shutter"
    )
    private val switchCameraDescriptions = listOf(
        "switch camera", "flip camera", "toggle camera", "front camera",
        "rear camera", "rotate camera", "switch", "flip", "selfie",
        "change camera", "switch to front", "switch to rear"
    )
    private val flashDescriptions = listOf(
        "flash", "flash mode", "toggle flash", "flash button",
        "flash off", "flash on", "flash auto", "flashlight"
    )
    private val videoDescriptions = listOf(
        "video", "record", "video mode", "switch to video",
        "start recording", "record video", "movie", "camcorder"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        messageClient = Wearable.getMessageClient(this)
        settings = SettingsManager(this)
        Log.d(TAG, "CameraControlService connected and ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to actively process events; we react to commands
    }

    override fun onInterrupt() {
        Log.d(TAG, "CameraControlService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "CameraControlService destroyed")
    }

    // Track flash state for direct toggling
    private var flashOn = false

    fun handleCommand(command: String) {
        Log.d(TAG, "handleCommand: $command")
        when (command) {
            "open_camera" -> openCamera()
            "capture" -> capture()
            "switch_camera" -> switchCamera()
            "toggle_flash" -> toggleFlash()
            "open_video" -> openVideoCamera()
            else -> {
                Log.w(TAG, "Unknown command: $command")
                sendStatusToWatch("unknown_command")
            }
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            sendStatusToWatch("camera_opened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            // Fallback: try generic camera intent
            try {
                val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(fallback)
                sendStatusToWatch("camera_opened")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open camera (fallback)", e2)
                sendStatusToWatch("camera_open_failed")
            }
        }
    }

    private fun openVideoCamera() {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            sendStatusToWatch("video_camera_opened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open video camera", e)
            sendStatusToWatch("video_open_failed")
        }
    }

    /**
     * Capture: if camera app is in foreground, tap the shutter/record button.
     * If not in a camera app, open camera first then capture.
     */
    private fun capture() {
        Log.d(TAG, "capture: attempting to find and click shutter")
        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "capture: no active window, opening camera first")
            if (settings.isAutoOpenCameraEnabled()) {
                openCamera()
                handler.postDelayed({ captureAfterOpen() }, settings.getCameraLaunchDelayMs().toLong())
            } else {
                sendStatusToWatch("no_camera_app")
            }
            return
        }

        // Check if we're in a camera app by looking for shutter-like buttons
        val isCameraApp = isCameraAppInForeground(root)
        root.recycle()

        if (!isCameraApp) {
            Log.d(TAG, "capture: not in camera app, opening camera first")
            if (settings.isAutoOpenCameraEnabled()) {
                openCamera()
                handler.postDelayed({ captureAfterOpen() }, settings.getCameraLaunchDelayMs().toLong())
            } else {
                sendStatusToWatch("no_camera_app")
            }
            return
        }

        doCapture()
    }

    private fun captureAfterOpen() {
        Log.d(TAG, "captureAfterOpen: retrying after camera open")
        doCapture()
    }

    private fun doCapture() {
        if (findAndClickButton(shutterDescriptions)) {
            sendStatusToWatch("captured")
            return
        }
        if (settings.isShutterFallbackEnabled()) {
            Log.d(TAG, "doCapture: shutter not found by description, trying fallback tap")
            tapShutterFallback()
        } else {
            Log.d(TAG, "doCapture: shutter not found and fallback disabled")
            sendStatusToWatch("shutter_not_found")
        }
    }

    private fun isCameraAppInForeground(root: AccessibilityNodeInfo): Boolean {
        // Check if any shutter/capture-like button exists in the current window
        for (desc in shutterDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes.isNotEmpty()) {
                for (node in nodes) node.recycle()
                return true
            }
        }
        // Also check for video record buttons
        for (desc in videoDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes.isNotEmpty()) {
                for (node in nodes) node.recycle()
                return true
            }
        }
        return false
    }

    private fun switchCamera() {
        if (findAndClickButton(switchCameraDescriptions)) {
            sendStatusToWatch("camera_switched")
        } else {
            sendStatusToWatch("switch_not_found")
        }
    }

    private fun toggleFlash() {
        // Try to find flash button and click it.
        // Many camera apps cycle: off -> auto -> on. We want direct on/off.
        // Strategy: click flash button, then if a menu appears, click "on" or "off"
        val flashOnDescriptions = listOf("flash on", "on", "flash_on")
        val flashOffDescriptions = listOf("flash off", "off", "flash_off")

        if (flashOn) {
            // Try to turn flash off
            if (findAndClickButton(flashOffDescriptions)) {
                flashOn = false
                sendStatusToWatch("flash_off")
                return
            }
        } else {
            // Try to turn flash on
            if (findAndClickButton(flashOnDescriptions)) {
                flashOn = true
                sendStatusToWatch("flash_on")
                return
            }
        }

        // Fallback: just click the flash button (cycles through modes)
        if (findAndClickButton(flashDescriptions)) {
            flashOn = !flashOn
            sendStatusToWatch(if (flashOn) "flash_on" else "flash_off")

            // After clicking flash, a submenu might appear — try to click on/off in it
            handler.postDelayed({
                if (flashOn) {
                    findAndClickButton(flashOnDescriptions)
                } else {
                    findAndClickButton(flashOffDescriptions)
                }
            }, settings.getFlashSubmenuDelayMs().toLong())
        } else {
            sendStatusToWatch("flash_not_found")
        }
    }

    private fun findAndClickButton(descriptions: List<String>): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        // Search by content description
        for (desc in descriptions) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(desc)
            for (node in nodes) {
                if (node.isClickable && node.isVisibleToUser) {
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (result) {
                        Log.d(TAG, "Clicked button with text: $desc")
                        node.recycle()
                        rootNode.recycle()
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Search clickable nodes and check content description with fuzzy matching
        val clickable = findClickableNodes(rootNode)
        for (node in clickable) {
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            for (desc in descriptions) {
                if (contentDesc.contains(desc) || text.contains(desc)) {
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (result) {
                        Log.d(TAG, "Clicked node with contentDescription containing: $desc")
                        recycleNodes(clickable)
                        rootNode.recycle()
                        return true
                    }
                }
            }
        }

        // Also try clicking parent of matching non-clickable nodes
        for (desc in descriptions) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(desc)
            for (node in nodes) {
                val parent = node.parent
                if (parent != null && parent.isClickable) {
                    val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (result) {
                        Log.d(TAG, "Clicked parent of node with text: $desc")
                        parent.recycle()
                        node.recycle()
                        rootNode.recycle()
                        return true
                    }
                    parent.recycle()
                }
                node.recycle()
            }
        }

        recycleNodes(clickable)
        rootNode.recycle()
        return false
    }

    private fun findClickableNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (node.isClickable) {
            result.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            result.addAll(findClickableNodes(child))
            child.recycle()
        }
        return result
    }

    private fun recycleNodes(nodes: List<AccessibilityNodeInfo>) {
        for (node in nodes) {
            try {
                node.recycle()
            } catch (e: Exception) {
                // Already recycled
            }
        }
    }

    private fun tapShutterFallback() {
        // Tap the center-bottom area of the screen where the shutter button typically is
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)

        val x = metrics.widthPixels / 2f
        val fallbackPercent = settings.getShutterFallbackPosition() / 100f
        val y = metrics.heightPixels * fallbackPercent

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, settings.getGestureTapDurationMs().toLong()))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap gesture completed at ($x, $y)")
                sendStatusToWatch("captured")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap gesture cancelled")
                sendStatusToWatch("capture_failed")
            }
        }, null)
    }

    private fun sendStatusToWatch(status: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                messageClient.sendMessage(
                    node.id,
                    "/camera_remote/status",
                    status.toByteArray()
                ).addOnSuccessListener {
                    Log.d(TAG, "Status '$status' sent to watch ${node.displayName}")
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send status to watch", e)
                }
            }
        }
    }
}
