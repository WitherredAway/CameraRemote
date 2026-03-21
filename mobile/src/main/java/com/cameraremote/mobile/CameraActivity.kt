package com.cameraremote.mobile

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.cameraremote.mobile.databinding.ActivityCameraBinding
import java.text.SimpleDateFormat
import java.util.Locale

class CameraActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener {

    private lateinit var binding: ActivityCameraBinding

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashEnabled = false
    private var isRecording = false
    private var countdownTimer: CountDownTimer? = null

    companion object {
        private const val TAG = "CameraRemote"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        const val PATH_CAMERA_REMOTE = "/camera_remote"
        const val CMD_TAKE_PHOTO = "take_photo"
        const val CMD_TAKE_PHOTO_TIMER = "take_photo_timer"
        const val CMD_TOGGLE_FLASH = "toggle_flash"
        const val CMD_SWITCH_CAMERA = "switch_camera"
        const val CMD_START_VIDEO = "start_video"
        const val CMD_STOP_VIDEO = "stop_video"
        const val CMD_ZOOM_IN = "zoom_in"
        const val CMD_ZOOM_OUT = "zoom_out"

        @Volatile
        var instance: CameraActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        instance = this

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        setupLocalButtons()
        updateFlashIcon()
        updateRecordButton()
        updateStatusText("Waiting for watch connection...")
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        countdownTimer?.cancel()
    }

    private fun setupLocalButtons() {
        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnSwitchCamera.setOnClickListener { switchCamera() }
        binding.btnFlash.setOnClickListener { toggleFlash() }
        binding.btnRecord.setOnClickListener {
            if (isRecording) stopVideoRecording() else startVideoRecording()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permissions are required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                )
            )
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )
            applyFlash()
            updateStatusText("Camera ready")
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    fun takePhoto() {
        val imageCapture = imageCapture ?: return
        updateStatusText("Taking photo...")

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraRemote")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    updateStatusText("Photo capture failed!")
                    sendStatusToWatch("photo_failed")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Photo saved")
                    updateStatusText("Photo saved!")
                    sendStatusToWatch("photo_taken")
                    showCaptureFlash()
                }
            }
        )
    }

    fun takePhotoWithTimer() {
        updateStatusText("Timer: 3...")
        binding.tvCountdown.visibility = View.VISIBLE

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                binding.tvCountdown.text = secondsLeft.toString()
                updateStatusText("Timer: $secondsLeft...")
            }

            override fun onFinish() {
                binding.tvCountdown.visibility = View.GONE
                takePhoto()
            }
        }.start()
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCameraUseCases()
        val label = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Rear" else "Front"
        updateStatusText("Switched to $label camera")
        sendStatusToWatch("camera_switched_$label")
    }

    fun toggleFlash() {
        flashEnabled = !flashEnabled
        applyFlash()
        updateFlashIcon()
        val state = if (flashEnabled) "ON" else "OFF"
        updateStatusText("Flash $state")
        sendStatusToWatch("flash_$state")
    }

    private fun applyFlash() {
        imageCapture?.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        camera?.cameraControl?.enableTorch(flashEnabled)
    }

    private fun updateFlashIcon() {
        runOnUiThread {
            binding.btnFlash.setIconResource(
                if (flashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
        }
    }

    private fun updateRecordButton() {
        runOnUiThread {
            if (isRecording) {
                binding.btnRecord.setIconResource(R.drawable.ic_stop)
                binding.btnRecord.setIconTintResource(com.google.android.material.R.color.design_default_color_error)
            } else {
                binding.btnRecord.setIconResource(R.drawable.ic_videocam)
                binding.btnRecord.setIconTintResource(com.google.android.material.R.color.design_default_color_on_primary)
            }
        }
    }

    fun startVideoRecording() {
        val videoCapture = this.videoCapture ?: return

        if (isRecording) return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraRemote")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (ContextCompat.checkSelfPermission(
                        this@CameraActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        updateStatusText("Recording...")
                        updateRecordButton()
                        sendStatusToWatch("recording_started")
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        updateRecordButton()
                        if (!recordEvent.hasError()) {
                            updateStatusText("Video saved!")
                            sendStatusToWatch("recording_stopped")
                        } else {
                            Log.e(TAG, "Video recording error: ${recordEvent.error}")
                            updateStatusText("Video recording failed!")
                            sendStatusToWatch("recording_failed")
                        }
                    }
                }
            }
    }

    fun stopVideoRecording() {
        recording?.stop()
        recording = null
    }

    fun zoomIn() {
        val cam = camera ?: return
        val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
        val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
        val newZoom = (currentZoom + 0.5f).coerceAtMost(maxZoom)
        cam.cameraControl.setZoomRatio(newZoom)
        updateStatusText("Zoom: %.1fx".format(newZoom))
        sendStatusToWatch("zoom_${newZoom}")
    }

    fun zoomOut() {
        val cam = camera ?: return
        val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
        val minZoom = cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
        val newZoom = (currentZoom - 0.5f).coerceAtLeast(minZoom)
        cam.cameraControl.setZoomRatio(newZoom)
        updateStatusText("Zoom: %.1fx".format(newZoom))
        sendStatusToWatch("zoom_${newZoom}")
    }

    private fun updateStatusText(text: String) {
        runOnUiThread {
            binding.tvStatus.text = text
        }
    }

    private fun showCaptureFlash() {
        runOnUiThread {
            binding.flashOverlay.visibility = View.VISIBLE
            binding.flashOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    binding.flashOverlay.visibility = View.GONE
                    binding.flashOverlay.alpha = 1f
                }
                .start()
        }
    }

    private fun sendStatusToWatch(status: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(this)
                    .sendMessage(node.id, "/camera_remote/status", status.toByteArray())
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PATH_CAMERA_REMOTE) {
            val command = String(messageEvent.data)
            Log.d(TAG, "Received command from watch: $command")

            runOnUiThread {
                when (command) {
                    CMD_TAKE_PHOTO -> takePhoto()
                    CMD_TAKE_PHOTO_TIMER -> takePhotoWithTimer()
                    CMD_TOGGLE_FLASH -> toggleFlash()
                    CMD_SWITCH_CAMERA -> switchCamera()
                    CMD_START_VIDEO -> startVideoRecording()
                    CMD_STOP_VIDEO -> stopVideoRecording()
                    CMD_ZOOM_IN -> zoomIn()
                    CMD_ZOOM_OUT -> zoomOut()
                    else -> Log.w(TAG, "Unknown command: $command")
                }
            }
        }
    }
}
