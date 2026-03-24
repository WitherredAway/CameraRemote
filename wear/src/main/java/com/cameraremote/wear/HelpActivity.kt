package com.cameraremote.wear

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        // Show build timestamp
        findViewById<TextView>(R.id.lastUpdatedText).text = "Last updated: ${BuildConfig.BUILD_TIMESTAMP}"

        // Request focus for bezel/rotary scrolling
        findViewById<ScrollView>(R.id.helpScrollView).requestFocus()
    }
}
