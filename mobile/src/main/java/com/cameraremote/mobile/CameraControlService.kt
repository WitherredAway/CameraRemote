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
        "shoot", "snap", "photograph", "shutter button",
        "capture button", "camera shutter"
    )
    private val recordDescriptions = listOf(
        "record", "start recording", "record video", "stop recording",
        "stop", "recording", "rec"
    )
    private val switchCameraDescriptions = listOf(
        "switch camera", "flip camera", "toggle camera", "front camera",
        "rear camera", "rotate camera", "switch", "flip", "selfie",
        "change camera", "switch to front", "switch to rear"
    )
    private val flashDescriptions = listOf(
        "flash mode", "toggle flash", "flash button",
        "flash off", "flash on", "flash auto"
    )
    private val videoDescriptions = listOf(
        "video", "record", "video mode", "switch to video",
        "start recording", "record video", "movie", "camcorder"
    )
    private val photoModeDescriptions = listOf(
        "photo", "photo mode", "switch to photo", "camera mode"
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

        // Parse zoom commands with steps: "zoom_in:3" or "zoom_out:2"
        if (command.startsWith("zoom_in:") || command.startsWith("zoom_out:")) {
            val parts = command.split(":")
            val zoomIn = command.startsWith("zoom_in")
            val steps = parts.getOrNull(1)?.toIntOrNull() ?: 1
            val root = rootInActiveWindow
            if (root != null && isCameraAppInForeground(root)) {
                root.recycle()
                zoom(zoomIn = zoomIn, steps = steps)
            } else {
                root?.recycle()
                sendStatusToWatch("camera_not_open")
            }
            return
        }

        when (command) {
            "open_camera" -> openCamera()
            "capture" -> capture()
            "open_video" -> openVideoCamera()
            "switch_camera" -> {
                val root = rootInActiveWindow
                if (root != null && isCameraAppInForeground(root)) {
                    root.recycle()
                    switchCamera()
                } else {
                    root?.recycle()
                    sendStatusToWatch("camera_not_open")
                }
            }
            "toggle_flash" -> {
                val root = rootInActiveWindow
                if (root != null && isCameraAppInForeground(root)) {
                    root.recycle()
                    toggleFlash()
                } else {
                    root?.recycle()
                    sendStatusToWatch("camera_not_open")
                }
            }
            "zoom_in" -> {
                val root = rootInActiveWindow
                if (root != null && isCameraAppInForeground(root)) {
                    root.recycle()
                    zoom(zoomIn = true, steps = 1)
                } else {
                    root?.recycle()
                    sendStatusToWatch("camera_not_open")
                }
            }
            "zoom_out" -> {
                val root = rootInActiveWindow
                if (root != null && isCameraAppInForeground(root)) {
                    root.recycle()
                    zoom(zoomIn = false, steps = 1)
                } else {
                    root?.recycle()
                    sendStatusToWatch("camera_not_open")
                }
            }
            else -> {
                Log.w(TAG, "Unknown command: $command")
                sendStatusToWatch("unknown_command")
            }
        }
    }

    private fun openCamera() {
        try {
            // CLEAR_TASK forces the camera app to restart fresh instead of
            // resuming in its last mode (which might be video)
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            Log.d(TAG, "openCamera: launching STILL_IMAGE_CAMERA with CLEAR_TASK")
            startActivity(intent)
            sendStatusToWatch("camera_opened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera with STILL_IMAGE intent", e)
            try {
                val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                Log.d(TAG, "openCamera: trying IMAGE_CAPTURE fallback with CLEAR_TASK")
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
     * Capture: tap the shutter/record button in the current camera app.
     * If no camera app is detected, open camera in PHOTO mode first then capture.
     * Does NOT re-launch camera if already in a camera app (preserves current mode).
     */
    private fun capture() {
        Log.d(TAG, "capture: attempting to find and click shutter/record")
        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "capture: no active window, opening camera in photo mode")
            if (settings.isAutoOpenCameraEnabled()) {
                openCamera() // Uses STILL_IMAGE_CAMERA intent = photo mode
                handler.postDelayed({ captureAfterOpen() }, settings.getCameraLaunchDelayMs().toLong())
            } else {
                sendStatusToWatch("no_camera_app")
            }
            return
        }

        // Check if we're in a camera app by looking for any camera-like buttons
        val isCameraApp = isCameraAppInForeground(root)
        root.recycle()

        if (!isCameraApp) {
            Log.d(TAG, "capture: not in camera app, opening camera in photo mode")
            if (settings.isAutoOpenCameraEnabled()) {
                openCamera() // Uses STILL_IMAGE_CAMERA intent = photo mode
                handler.postDelayed({ captureAfterOpen() }, settings.getCameraLaunchDelayMs().toLong())
            } else {
                sendStatusToWatch("no_camera_app")
            }
            return
        }

        // Already in a camera app — just tap the shutter/record, don't re-launch
        doCapture()
    }

    private fun captureAfterOpen() {
        Log.d(TAG, "captureAfterOpen: retrying after camera open (photo priority)")

        // Dump ALL nodes so we can see what the camera UI looks like
        dumpAllNodes("captureAfterOpen")

        // First try photo shutter buttons
        if (findAndClickButton(shutterDescriptions)) {
            sendStatusToWatch("captured")
            return
        }

        // Camera might have opened in video mode despite STILL_IMAGE intent.
        // Check if we see record buttons but no shutter — means we're in video mode.
        // Try to switch to photo mode by clicking the photo mode tab.
        Log.d(TAG, "captureAfterOpen: no shutter found, checking if in video mode")
        if (isInVideoMode()) {
            Log.d(TAG, "captureAfterOpen: detected video mode, switching to photo")
            if (switchToPhotoMode()) {
                // Wait for mode switch, then try capture again
                handler.postDelayed({
                    Log.d(TAG, "captureAfterOpen: retrying capture after mode switch")
                    if (findAndClickButton(shutterDescriptions)) {
                        sendStatusToWatch("captured")
                    } else if (settings.isShutterFallbackEnabled()) {
                        tapShutterFallback()
                    } else {
                        sendStatusToWatch("shutter_not_found")
                    }
                }, 800L)
                return
            }
        }

        // Fallback
        if (settings.isShutterFallbackEnabled()) {
            Log.d(TAG, "captureAfterOpen: trying fallback tap")
            tapShutterFallback()
        } else {
            sendStatusToWatch("shutter_not_found")
        }
    }

    /**
     * Check if the camera is currently in video mode by looking for record buttons
     * but no photo shutter buttons.
     */
    private fun isInVideoMode(): Boolean {
        val root = rootInActiveWindow ?: return false
        var hasRecord = false
        var hasShutter = false

        for (desc in recordDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes.isNotEmpty()) {
                hasRecord = true
                for (node in nodes) node.recycle()
                break
            }
        }
        for (desc in shutterDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes.isNotEmpty()) {
                hasShutter = true
                for (node in nodes) node.recycle()
                break
            }
        }
        root.recycle()
        Log.d(TAG, "isInVideoMode: hasRecord=$hasRecord, hasShutter=$hasShutter")
        return hasRecord && !hasShutter
    }

    /**
     * Try to switch from video mode to photo mode by clicking a "Photo" mode tab/button.
     */
    private fun switchToPhotoMode(): Boolean {
        Log.d(TAG, "switchToPhotoMode: looking for photo mode button")
        if (findAndClickButton(photoModeDescriptions)) {
            Log.d(TAG, "switchToPhotoMode: clicked photo mode button")
            sendStatusToWatch("photo_mode")
            return true
        }
        Log.d(TAG, "switchToPhotoMode: no photo mode button found")
        return false
    }

    private fun doCapture() {
        // Try video record/stop button first — this ensures that pressing
        // shutter while recording stops the video instead of taking a photo
        if (findAndClickButton(recordDescriptions)) {
            sendStatusToWatch("captured")
            return
        }
        // Then try photo shutter descriptions
        if (findAndClickButton(shutterDescriptions)) {
            sendStatusToWatch("captured")
            return
        }
        // Fallback: tap the shutter/record button position on screen
        if (settings.isShutterFallbackEnabled()) {
            Log.d(TAG, "doCapture: shutter/record not found by description, trying fallback tap")
            tapShutterFallback()
        } else {
            Log.d(TAG, "doCapture: shutter/record not found and fallback disabled")
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
        for (desc in recordDescriptions + videoDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes.isNotEmpty()) {
                for (node in nodes) node.recycle()
                return true
            }
        }
        // Check for flash button (indicates camera app)
        for (desc in flashDescriptions) {
            val nodes = root.findAccessibilityNodeInfosByText(desc)
            if (nodes.isNotEmpty()) {
                for (node in nodes) node.recycle()
                return true
            }
        }
        // Check for switch camera button
        for (desc in switchCameraDescriptions) {
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

    /**
     * Zoom in or out by performing a pinch gesture on screen.
     * Uses two-finger spread (zoom in) or pinch (zoom out).
     */
    private fun zoom(zoomIn: Boolean, steps: Int = 1) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)

        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f
        // Scale gesture distance by steps — more rotation = bigger zoom
        val baseOffset = metrics.widthPixels / 10f
        val offset = baseOffset * steps.coerceIn(1, 5)

        val duration = 300L
        Log.d(TAG, "zoom: zoomIn=$zoomIn steps=$steps offset=$offset")
        if (zoomIn) {
            // Spread: fingers move outward from center
            val path1 = Path().apply {
                moveTo(centerX - offset / 2, centerY)
                lineTo(centerX - offset, centerY)
            }
            val path2 = Path().apply {
                moveTo(centerX + offset / 2, centerY)
                lineTo(centerX + offset, centerY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
                .addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
                .build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Zoom in gesture completed")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Zoom in gesture cancelled")
                }
            }, null)
        } else {
            // Pinch: fingers move inward toward center
            val path1 = Path().apply {
                moveTo(centerX - offset, centerY)
                lineTo(centerX - offset / 2, centerY)
            }
            val path2 = Path().apply {
                moveTo(centerX + offset, centerY)
                lineTo(centerX + offset / 2, centerY)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
                .addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
                .build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Zoom out gesture completed")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Zoom out gesture cancelled")
                }
            }, null)
        }
    }

    private fun toggleFlash() {
        // Samsung camera (and many others) use icon-only flash buttons that don't
        // expose useful text to accessibility. Strategy:
        // 1. Try accessibility click on the main flash button to open submenu
        // 2. After submenu opens, find submenu options by their node bounds
        //    (leftmost = off, rightmost = on) — no hardcoded coords
        Log.d(TAG, "toggleFlash: flashOn=$flashOn")

        // First dump all visible nodes for debugging
        dumpVisibleNodes()

        // Try accessibility-based approach
        if (tryAccessibilityFlashToggle()) {
            return
        }

            // If accessibility matching totally failed, try findAndClickButton with flash descriptions
            Log.d(TAG, "toggleFlash: specific flash matching failed, trying broad search")
            if (findAndClickButton(flashDescriptions)) {
                flashSubmenuRetries = 0
                handler.postDelayed({ selectFlashSubmenuOption() }, settings.getFlashSubmenuDelayMs().toLong())
            return
        }

        Log.d(TAG, "toggleFlash: no flash button found at all")
        sendStatusToWatch("flash_not_found")
    }

    /**
     * Try to toggle flash using accessibility node matching.
     * Returns true if we found and clicked something.
     */
    private fun tryAccessibilityFlashToggle(): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        // Search all clickable nodes for flash-related content descriptions
        val clickable = findClickableNodes(rootNode)
        for (node in clickable) {
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

            // Match nodes whose contentDescription is specifically about flash
            // (e.g. "Flash", "Flash off", "Flash on", "Flash auto")
            // but NOT "Motion photo" or similar
            if (contentDesc.contains("flash") && !contentDesc.contains("motion")) {
                if (node.isVisibleToUser) {
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (result) {
                        Log.d(TAG, "Accessibility: clicked flash node: '$contentDesc'")
                        recycleNodes(clickable)
                        rootNode.recycle()

                        // After clicking, wait for submenu and try to select on/off
                        flashSubmenuRetries = 0
                        handler.postDelayed({
                            selectFlashSubmenuOption()
                        }, settings.getFlashSubmenuDelayMs().toLong())
                        return true
                    }
                }
            }
        }

        recycleNodes(clickable)
        rootNode.recycle()
        return false
    }

    /**
     * After the flash submenu opens, find and click the on or off option.
     * Samsung camera uses text field like BACK_FLASH_OFF, BACK_FLASH_ON,
     * FRONT_FLASH_OFF, FRONT_FLASH_ON to identify flash submenu items.
     * All items share desc='Flash', so we must check the text field.
     */
    private var flashSubmenuRetries = 0

    private fun selectFlashSubmenuOption() {
        // Dump nodes for debugging
        dumpVisibleNodes()

        val rootNode = rootInActiveWindow ?: run {
            flashOn = !flashOn
            sendStatusToWatch(if (flashOn) "flash_on" else "flash_off")
            return
        }

        val allNodes = findAllNodes(rootNode)
        val wantOn = !flashOn  // If flash is currently off, we want to turn it on
        val screenHeight = resources.displayMetrics.heightPixels

        data class FlashNode(val node: AccessibilityNodeInfo, val desc: String, val text: String, val bounds: android.graphics.Rect)

        // Strategy 1 (Samsung-specific): Match on text field containing FLASH_OFF / FLASH_ON
        // Samsung camera sets text like "BACK_FLASH_OFF", "BACK_FLASH_ON", "FRONT_FLASH_OFF", etc.
        val targetText = if (wantOn) "FLASH_ON" else "FLASH_OFF"
        for (node in allNodes) {
            val text = node.text?.toString() ?: ""
            if (text.contains(targetText)) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                Log.d(TAG, "Flash submenu (text match): found '$text' at $bounds, trying click/tap")
                if (tryClickOrTap(node, bounds)) {
                    flashOn = wantOn
                    sendStatusToWatch(if (flashOn) "flash_on" else "flash_off")
                    recycleNodes(allNodes)
                    rootNode.recycle()
                    return
                }
            }
        }

        // Strategy 2: Exact match on contentDescription containing "flash on"/"flash off"
        val targetPhrase = if (wantOn) "flash on" else "flash off"
        for (node in allNodes) {
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (contentDesc.contains(targetPhrase) && !contentDesc.contains("motion") && node.isVisibleToUser) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                Log.d(TAG, "Flash submenu (desc match): found '$contentDesc' at $bounds")
                if (tryClickOrTap(node, bounds)) {
                    flashOn = wantOn
                    sendStatusToWatch(if (flashOn) "flash_on" else "flash_off")
                    recycleNodes(allNodes)
                    rootNode.recycle()
                    return
                }
            }
        }

        // Strategy 3: Position-based — collect flash submenu nodes in top area, sort by X
        val flashNodes = mutableListOf<FlashNode>()
        for (node in allNodes) {
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            if (contentDesc.contains("motion") || text.contains("motion")) continue
            if (!node.isVisibleToUser) continue

            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            // Submenu appears below the main toolbar — look for nodes in top 30%
            if (bounds.top >= screenHeight * 3 / 10) continue
            if (bounds.width() < 10 || bounds.height() < 10) continue

            // Must contain "flash" in desc or text, or be a standalone on/off/auto
            val isFlashOption = contentDesc.contains("flash") || text.contains("flash")
                    || contentDesc == "off" || contentDesc == "on" || contentDesc == "auto"
            if (isFlashOption) {
                flashNodes.add(FlashNode(node, contentDesc, text, bounds))
                Log.d(TAG, "Flash candidate: desc='$contentDesc' text='$text' bounds=$bounds")
            }
        }

        // Filter out the main flash button (it's in the top toolbar row, y < 200 typically)
        // Submenu items appear below the main toolbar
        val submenuNodes = flashNodes.filter { it.bounds.top > 150 }
        val candidates = if (submenuNodes.size >= 2) submenuNodes else flashNodes

        if (candidates.size >= 2) {
            val sorted = candidates.sortedBy { it.bounds.left }
            val target = if (wantOn) sorted.last() else sorted.first()
            Log.d(TAG, "Flash submenu (position): picking '${target.desc}' text='${target.text}' (want ${if (wantOn) "on" else "off"})")
            if (tryClickOrTap(target.node, target.bounds)) {
                flashOn = wantOn
                sendStatusToWatch(if (flashOn) "flash_on" else "flash_off")
                recycleNodes(allNodes)
                rootNode.recycle()
                return
            }
        }

        // No submenu found — retry once with longer delay (submenu may not have rendered yet)
        recycleNodes(allNodes)
        rootNode.recycle()
        if (flashSubmenuRetries < 1) {
            flashSubmenuRetries++
            Log.d(TAG, "Flash submenu not found, retrying (attempt $flashSubmenuRetries)")
            handler.postDelayed({ selectFlashSubmenuOption() }, 500L)
            return
        }

        // After retry, assume the first click toggled it
        flashSubmenuRetries = 0
        flashOn = !flashOn
        Log.d(TAG, "No flash submenu options found after retry, assuming toggle -> flash ${if (flashOn) "on" else "off"}")
        sendStatusToWatch(if (flashOn) "flash_on" else "flash_off")
    }

    /**
     * Try to activate a node: first by accessibility click, then parent click, then gesture tap.
     */
    private fun tryClickOrTap(node: AccessibilityNodeInfo, bounds: android.graphics.Rect): Boolean {
        // Try direct click
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.d(TAG, "tryClickOrTap: direct click succeeded")
            return true
        }
        // Try parent click
        val parent = node.parent
        if (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(TAG, "tryClickOrTap: parent click succeeded")
                parent.recycle()
                return true
            }
            parent.recycle()
        }
        // Gesture tap at node center
        Log.d(TAG, "tryClickOrTap: gesture tap at (${bounds.centerX()}, ${bounds.centerY()})")
        tapAtPosition(bounds.centerX().toFloat(), bounds.centerY().toFloat()) {}
        return true  // Assume gesture will work
    }

    /**
     * Tap a specific screen position using a gesture.
     * Uses the node's own reported bounds — no hardcoded coordinates.
     */
    private fun tapAtPosition(x: Float, y: Float, onComplete: () -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, settings.getGestureTapDurationMs().toLong()))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap gesture completed at ($x, $y)")
                onComplete()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap gesture cancelled at ($x, $y)")
            }
        }, null)
    }

    /**
     * Dump all visible clickable nodes to logcat for debugging.
     */
    private fun dumpVisibleNodes() {
        val rootNode = rootInActiveWindow ?: run {
            Log.d(TAG, "dumpVisibleNodes: no active window")
            return
        }
        val clickable = findClickableNodes(rootNode)
        Log.d(TAG, "=== VISIBLE CLICKABLE NODES (${clickable.size}) ===")
        for ((i, node) in clickable.withIndex()) {
            val contentDesc = node.contentDescription?.toString() ?: "(none)"
            val text = node.text?.toString() ?: "(none)"
            val className = node.className?.toString() ?: "(none)"
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            Log.d(TAG, "  [$i] class=$className desc='$contentDesc' text='$text' bounds=$bounds visible=${node.isVisibleToUser}")
        }
        Log.d(TAG, "=== END NODES ===")
        recycleNodes(clickable)
        rootNode.recycle()
    }

    /**
     * Dump ALL nodes (clickable or not) to logcat for debugging flash submenu.
     */
    private fun dumpAllNodes(label: String = "dumpAllNodes") {
        val rootNode = rootInActiveWindow ?: run {
            Log.d(TAG, "$label: no active window")
            return
        }
        val all = findAllNodes(rootNode)
        Log.d(TAG, "=== $label: ALL NODES (${all.size}) ===")
        for ((i, node) in all.withIndex()) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val contentDesc = node.contentDescription?.toString() ?: "(none)"
            val text = node.text?.toString() ?: "(none)"
            val className = node.className?.toString() ?: "(none)"
            Log.d(TAG, "  [$i] class=$className desc='$contentDesc' text='$text' bounds=$bounds clickable=${node.isClickable} visible=${node.isVisibleToUser}")
        }
        Log.d(TAG, "=== END $label ===")
        recycleNodes(all)
        rootNode.recycle()
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

    /**
     * Find ALL nodes in the accessibility tree (clickable or not).
     * Samsung camera submenu items may not report as clickable.
     */
    private fun findAllNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        result.add(AccessibilityNodeInfo.obtain(node))
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            result.addAll(findAllNodes(child))
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
