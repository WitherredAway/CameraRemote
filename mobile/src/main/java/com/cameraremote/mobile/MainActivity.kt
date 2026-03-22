package com.cameraremote.mobile

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cameraremote.mobile.databinding.ActivityMainBinding
import com.google.android.gms.wearable.Wearable
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val GITHUB_URL = "https://github.com/WitherredAway/CameraRemote"
    }

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionText.text = "v${BuildConfig.VERSION_NAME}"
        binding.lastUpdatedText.text = "Built ${BuildConfig.BUILD_TIMESTAMP}"

        binding.enableServiceButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open accessibility settings", e)
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
            }
        }

        binding.openCameraButton.setOnClickListener {
            openCamera()
        }

        binding.githubButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open GitHub", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateWatchConnection()
    }

    private fun updateServiceStatus() {
        try {
            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            )
            val isEnabled = enabledServices.any {
                it.resolveInfo.serviceInfo.packageName == packageName
            }

            if (isEnabled) {
                binding.statusCard.setBackgroundResource(R.drawable.bg_status_active)
                binding.statusIcon.setImageResource(R.drawable.ic_check_circle)
                binding.statusTitle.text = "Service Active"
                binding.statusSubtitle.text = "Accessibility service enabled"
            } else {
                binding.statusCard.setBackgroundResource(R.drawable.bg_status_inactive)
                binding.statusIcon.setImageResource(R.drawable.ic_error_circle)
                binding.statusTitle.text = "Service Inactive"
                binding.statusSubtitle.text = "Enable accessibility service to use remote"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check service status", e)
        }
    }

    private fun updateWatchConnection() {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@MainActivity).connectedNodes.await()
                runOnUiThread {
                    if (nodes.isNotEmpty()) {
                        val watchName = nodes.first().displayName
                        binding.watchStatusIcon.setImageResource(R.drawable.ic_check_circle)
                        binding.watchStatusText.text = "Watch connected ($watchName)"
                    } else {
                        binding.watchStatusIcon.setImageResource(R.drawable.ic_error_circle)
                        binding.watchStatusText.text = "No watch connected"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check watch connection", e)
                runOnUiThread {
                    binding.watchStatusIcon.setImageResource(R.drawable.ic_error_circle)
                    binding.watchStatusText.text = "Watch not available"
                }
            }
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            try {
                val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivity(fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open camera (fallback)", e2)
                Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
