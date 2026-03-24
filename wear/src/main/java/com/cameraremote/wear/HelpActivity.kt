package com.cameraremote.wear

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        // Show build timestamp as relative time
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm z", java.util.Locale.US)
            val buildDate = sdf.parse(BuildConfig.BUILD_TIMESTAMP)
            if (buildDate != null) {
                val relative = android.text.format.DateUtils.getRelativeTimeSpanString(
                    buildDate.time, System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                )
                findViewById<TextView>(R.id.lastUpdatedText).text = "Last updated: $relative"
            } else {
                findViewById<TextView>(R.id.lastUpdatedText).text = "Last updated: ${BuildConfig.BUILD_TIMESTAMP}"
            }
        } catch (_: Exception) {
            findViewById<TextView>(R.id.lastUpdatedText).text = "Last updated: ${BuildConfig.BUILD_TIMESTAMP}"
        }

        // Request focus for bezel/rotary scrolling
        findViewById<ScrollView>(R.id.helpScrollView).requestFocus()
    }
}
