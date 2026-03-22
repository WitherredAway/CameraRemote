package com.cameraremote.mobile

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import com.cameraremote.mobile.databinding.ActivityMainBinding
import com.google.android.gms.wearable.Wearable

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOpenCamera.setOnClickListener {
            openCamera()
        }

        binding.btnEnableService.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateWatchConnection()
    }

    private fun updateServiceStatus() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        val isEnabled = enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }

        if (isEnabled) {
            binding.tvServiceStatus.text = "Accessibility Service: Enabled"
            binding.tvServiceStatus.setTextColor(getColor(R.color.status_enabled))
            binding.cardServiceWarning.visibility = android.view.View.GONE
        } else {
            binding.tvServiceStatus.text = "Accessibility Service: Disabled"
            binding.tvServiceStatus.setTextColor(getColor(R.color.status_disabled))
            binding.cardServiceWarning.visibility = android.view.View.VISIBLE
        }
    }

    private fun updateWatchConnection() {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                val watchName = nodes.first().displayName
                binding.tvWatchStatus.text = "Watch: Connected ($watchName)"
                binding.tvWatchStatus.setTextColor(getColor(R.color.status_enabled))
            } else {
                binding.tvWatchStatus.text = "Watch: Not connected"
                binding.tvWatchStatus.setTextColor(getColor(R.color.status_disabled))
            }
        }.addOnFailureListener {
            binding.tvWatchStatus.text = "Watch: Not available"
            binding.tvWatchStatus.setTextColor(getColor(R.color.status_disabled))
        }
    }

    private fun openCamera() {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivity(fallback)
            } catch (e2: Exception) {
                binding.tvServiceStatus.text = "No camera app found"
            }
        }
    }
}
