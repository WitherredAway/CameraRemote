package com.cameraremote.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream

class CameraControlService : AccessibilityService() {

    companion object {
        const val TAG = "CameraControlService"
        private const val NOTIFICATION_CHANNEL_ID = "camera_remote_service"
        private const val NOTIFICATION_ID = 1001
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
        "recording", "record button"
    )
    private val switchCameraDescriptions = listOf(
        "switch camera", "flip camera", "toggle camera", "front camera",
        "rear camera", "rotate camera", "selfie",
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
        createNotificationChannel()
        cancelServiceNotification()
        Log.d(TAG, "CameraControlService connected and ready")
    }

    private var lastCameraDetected = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Detect when camera app opens/closes and notify watch
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val root = rootInActiveWindow ?: return
            val isCameraApp = isCameraAppInForeground(root)
            root.recycle()
            if (isCameraApp && !lastCameraDetected) {
                lastCameraDetected = true
                sendStatusToWatch("camera_detected")
            } else if (!isCameraApp && lastCameraDetected) {
                lastCameraDetected = false
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "CameraControlService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        showServiceStoppedNotification()
        Log.d(TAG, "CameraControlService destroyed")
    }

    // Track flash state for direct toggling
    private var flashOn = false

    private fun requireCameraOpen(action: () -> Unit) {
        val root = rootInActiveWindow
        if (root != null && isCameraAppInForeground(root)) {
            root.recycle()
            action()
        } else {
            root?.recycle()
            sendStatusToWatch("camera_not_open")
        }
    }

    fun handleCommand(command: String) {
        Log.d(TAG, "handleCommand: $command")

        // Parse zoom commands with steps: "zoom_in:3" or "zoom_out:2"
        // Handle delete_preview with URI parameter
        if (command.startsWith("delete_preview:")) {
            val uriStr = command.removePrefix("delete_preview:")
            deletePreview(uriStr)
            return
        }

        if (command.startsWith("zoom_in:") || command.startsWith("zoom_out:")) {
            val parts = command.split(":")
            val zoomIn = command.startsWith("zoom_in")
            val steps = parts.getOrNull(1)?.toIntOrNull() ?: 1
            requireCameraOpen { zoom(zoomIn = zoomIn, steps = steps) }
            return
        }

        when (command) {
            "open_camera" -> openCamera()
            "capture" -> capture()
            "open_video" -> openVideoCamera()
            "switch_camera" -> requireCameraOpen { switchCamera() }
            "toggle_flash" -> requireCameraOpen { toggleFlash() }
            "zoom_in" -> requireCameraOpen { zoom(zoomIn = true, steps = 1) }
            "zoom_out" -> requireCameraOpen { zoom(zoomIn = false, steps = 1) }
            "open_gallery" -> openGallery()
            "burst_capture" -> requireCameraOpen { burstCapture() }
            "capture_timer" -> captureWithTimer()
            "preview_capture" -> requireCameraOpen { previewCapture() }
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
            Log.d(TAG, "openCamera: launching STILL_IMAGE_CAMERA intent")
            startActivity(intent)
            sendStatusToWatch("camera_opened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera with STILL_IMAGE intent", e)
            try {
                val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                Log.d(TAG, "openCamera: trying IMAGE_CAPTURE fallback")
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
    private fun isInVideoMode(existingRoot: AccessibilityNodeInfo? = null): Boolean {
        val root = existingRoot ?: rootInActiveWindow ?: return false
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
        if (existingRoot == null) root.recycle()
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
        // Try photo shutter buttons FIRST — camera apps may have "record"
        // nodes even in photo mode, so we must prioritize shutter
        if (findAndClickButton(shutterDescriptions)) {
            sendStatusToWatch("captured")
            return
        }
        // Then try record/stop button (for video mode or stopping recording)
        if (findAndClickButton(recordDescriptions)) {
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
    private fun getScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun zoom(zoomIn: Boolean, steps: Int = 1) {
        val (screenWidth, screenHeight) = getScreenSize()
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val baseOffset = screenWidth / 10f
        val offset = baseOffset * steps.coerceIn(1, 5)
        val duration = 150L

        // Zoom in = spread outward, zoom out = pinch inward
        val startOffset = if (zoomIn) offset / 2 else offset
        val endOffset = if (zoomIn) offset else offset / 2

        val path1 = Path().apply {
            moveTo(centerX - startOffset, centerY)
            lineTo(centerX - endOffset, centerY)
        }
        val path2 = Path().apply {
            moveTo(centerX + startOffset, centerY)
            lineTo(centerX + endOffset, centerY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path1, 0, duration))
            .addStroke(GestureDescription.StrokeDescription(path2, 0, duration))
            .build()
        val label = if (zoomIn) "in" else "out"
        Log.d(TAG, "zoom: $label steps=$steps offset=$offset")
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Zoom $label gesture completed")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Zoom $label gesture cancelled")
            }
        }, null)
    }

    private fun toggleFlash() {
        // Samsung camera (and many others) use icon-only flash buttons that don't
        // expose useful text to accessibility. Strategy:
        // 1. Try accessibility click on the main flash button to open submenu
        // 2. After submenu opens, find submenu options by their node bounds
        //    (leftmost = off, rightmost = on) — no hardcoded coords
        Log.d(TAG, "toggleFlash: flashOn=$flashOn")

        // Dump nodes only in debug builds
        if (BuildConfig.DEBUG) dumpVisibleNodes()

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
        if (BuildConfig.DEBUG) dumpVisibleNodes()

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
        val (screenWidth, screenHeight) = getScreenSize()
        val x = screenWidth / 2f
        val fallbackPercent = settings.getShutterFallbackPosition() / 100f
        val y = screenHeight * fallbackPercent

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

    private fun previewCapture() {
        doCapture()
        sendStatusToWatch("preview_capturing")
        // Wait for photo to save, then read and send preview
        handler.postDelayed({ sendPreviewToWatch() }, 2500L)
    }

    private fun sendPreviewToWatch() {
        try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            Log.d(TAG, "sendPreviewToWatch: querying MediaStore for latest image")
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
            )
            if (cursor == null) {
                Log.e(TAG, "sendPreviewToWatch: cursor is null - permission denied?")
                sendStatusToWatch("preview_failed")
                return
            }
            cursor.use {
                Log.d(TAG, "sendPreviewToWatch: cursor count=${it.count}")
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    Log.d(TAG, "sendPreviewToWatch: latest image uri=$uri")

                    val inputStream = contentResolver.openInputStream(uri)
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream?.close()

                    if (bitmap != null) {
                        val maxDim = maxOf(bitmap.width, bitmap.height)
                        val scale = 300f / maxDim
                        val resized = Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * scale).toInt(),
                            (bitmap.height * scale).toInt(),
                            true
                        )

                        val baos = ByteArrayOutputStream()
                        resized.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        val imageBytes = baos.toByteArray()

                        val request = PutDataMapRequest.create("/camera_remote/preview").apply {
                            dataMap.putByteArray("image", imageBytes)
                            dataMap.putString("uri", uri.toString())
                            dataMap.putLong("timestamp", System.currentTimeMillis())
                        }.asPutDataRequest().setUrgent()
                        Wearable.getDataClient(this).putDataItem(request)

                        sendStatusToWatch("preview_ready")
                        bitmap.recycle()
                        resized.recycle()
                        Log.d(TAG, "Preview sent to watch: ${imageBytes.size} bytes")
                    } else {
                        sendStatusToWatch("preview_failed")
                    }
                } else {
                    sendStatusToWatch("preview_failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send preview", e)
            sendStatusToWatch("preview_failed")
        }
    }

    private fun deletePreview(uriStr: String) {
        try {
            val intent = Intent(this, DeletePhotoActivity::class.java).apply {
                putExtra(DeletePhotoActivity.EXTRA_URI, uriStr)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch delete activity", e)
            sendStatusToWatch("preview_delete_failed")
        }
    }

    private fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            sendStatusToWatch("gallery_opened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open gallery", e)
            sendStatusToWatch("gallery_failed")
        }
    }

    private fun burstCapture() {
        val burstCount = settings.getBurstCount()
        sendStatusToWatch("burst_$burstCount")
        for (i in 0 until burstCount) {
            handler.postDelayed({ doCapture() }, i * 500L)
        }
    }

    private fun captureWithTimer() {
        val timerSec = settings.getDefaultTimerSeconds()
        sendStatusToWatch("timer_${timerSec}s")
        handler.postDelayed({ capture() }, timerSec * 1000L)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Service Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about the Camera Remote accessibility service"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showServiceStoppedNotification() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Camera Remote")
                .setContentText("Accessibility service stopped. Tap to re-enable.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show service stopped notification", e)
        }
    }

    private fun cancelServiceNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel notification", e)
        }
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
