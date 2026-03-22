package com.cameraremote.mobile

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cameraremote.mobile.databinding.ActivityMainBinding
import com.google.android.gms.wearable.Wearable
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val GITHUB_URL = "https://github.com/WitherredAway/CameraRemote"
        private const val DISCORD_URL = "https://discord.gg/gK6wQywwzb"
        private const val KOFI_URL = "https://ko-fi.com/wthrr"
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var binding: ActivityMainBinding
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + scopeJob)

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.lastUpdatedText.text = "Last updated: ${BuildConfig.BUILD_TIMESTAMP}"

        // Show current version immediately
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.updateTitle.text = "v${pInfo.versionName} \u2014 Checking for updates..."
        } catch (_: Exception) {
            binding.updateTitle.text = "Checking for updates..."
        }

        binding.enableServiceButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open accessibility settings", e)
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
            }
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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

        binding.discordButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DISCORD_URL)))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Discord", e)
            }
        }

        binding.kofiButton.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KOFI_URL)))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Ko-fi", e)
            }
        }

        checkForUpdates()
        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateWatchConnection()
        checkForUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        scopeJob.cancel()
    }

    private fun checkForUpdates() {
        val checker = UpdateChecker(this)
        checker.checkForUpdate { info ->
            runOnUiThread {
                if (info != null && info.isUpdateAvailable) {
                    binding.updateTitle.text = "Update available"
                    binding.updateTitle.setTextColor(getColor(com.google.android.material.R.color.design_default_color_primary))
                    binding.updateSubtitle.visibility = View.VISIBLE
                    binding.updateSubtitle.text = "v${info.currentVersion} \u2192 v${info.latestVersion}"
                    binding.updateButtonsRow.visibility = View.VISIBLE

                    binding.updateButton.setOnClickListener {
                        if (info.downloadUrl.isNotEmpty()) {
                            checker.downloadAndInstall(info.downloadUrl)
                            binding.updateButton.isEnabled = false
                            binding.updateButton.text = "Downloading..."
                        }
                    }

                    if (info.watchDownloadUrl.isNotEmpty()) {
                        binding.updateWatchButton.visibility = View.VISIBLE
                        binding.updateWatchButton.setOnClickListener {
                            binding.updateWatchButton.isEnabled = false
                            binding.updateWatchButton.text = "Downloading..."
                            checker.downloadWatchApk(info.watchDownloadUrl) { file ->
                                runOnUiThread {
                                    if (file != null) {
                                        binding.updateWatchButton.text = "Downloaded"
                                        Toast.makeText(
                                            this,
                                            "Watch APK saved to ${file.absolutePath}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        binding.updateWatchButton.isEnabled = true
                                        binding.updateWatchButton.text = "Watch APK"
                                        Toast.makeText(
                                            this,
                                            "Failed to download watch APK",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    } else {
                        binding.updateWatchButton.visibility = View.GONE
                    }
                } else if (info != null) {
                    val versionName = info.currentVersion.ifEmpty {
                        try {
                            packageManager.getPackageInfo(packageName, 0).versionName
                        } catch (_: Exception) { "?" }
                    }
                    binding.updateTitle.text = "Up to date \u2014 v$versionName"
                    val tv = TypedValue()
                    theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true)
                    binding.updateTitle.setTextColor(tv.data)
                    binding.updateSubtitle.visibility = View.GONE
                    binding.updateButtonsRow.visibility = View.GONE
                } else {
                    val versionName = try {
                        packageManager.getPackageInfo(packageName, 0).versionName
                    } catch (_: Exception) { "?" }
                    binding.updateTitle.text = "v$versionName \u2014 could not check for updates"
                    val tv = TypedValue()
                    theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, tv, true)
                    binding.updateTitle.setTextColor(tv.data)
                    binding.updateSubtitle.visibility = View.GONE
                    binding.updateButtonsRow.visibility = View.GONE
                }
            }
        }
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
