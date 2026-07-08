package com.abhiram.appblur

import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeoutLabel: TextView
    private var timeoutSeconds = 8

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        timeoutSeconds = Prefs.getTimeoutSeconds(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "AppBlur"
            textSize = 26f
        }
        root.addView(title)

        statusText = TextView(this).apply {
            text = "Status: stopped"
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        root.addView(statusText)

        timeoutLabel = TextView(this).apply {
            text = "Blur after: ${timeoutSeconds}s of no touch"
        }
        root.addView(timeoutLabel)

        seekBar = SeekBar(this).apply {
            max = 25
            progress = timeoutSeconds - 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    timeoutSeconds = 5 + progress
                    timeoutLabel.text = "Blur after: ${timeoutSeconds}s of no touch"
                    Prefs.setTimeoutSeconds(this@MainActivity, timeoutSeconds)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(seekBar)

        val opacityLabel = TextView(this).apply {
            text = "Blur opacity: ${Prefs.getOpacityPercent(this@MainActivity)}%"
        }
        root.addView(opacityLabel)

        val opacitySeekBar = SeekBar(this).apply {
            max = 100
            progress = Prefs.getOpacityPercent(this@MainActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    opacityLabel.text = "Blur opacity: ${progress}%"
                    Prefs.setOpacityPercent(this@MainActivity, progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(opacitySeekBar)

        val sectionPerms = TextView(this).apply {
            text = "\nPermissions (one-time each)"
            textSize = 18f
        }
        root.addView(sectionPerms)

        val grantOverlayBtn = Button(this).apply {
            text = "1. Grant overlay permission"
            setOnClickListener { requestOverlayPermission() }
        }
        root.addView(grantOverlayBtn)

        val grantUsageBtn = Button(this).apply {
            text = "2. Grant usage access (for per-app exclusions)"
            setOnClickListener { requestUsageAccess() }
        }
        root.addView(grantUsageBtn)

        val excludeBtn = Button(this).apply {
            text = "Choose apps to never blur"
            setOnClickListener { startActivity(Intent(this@MainActivity, AppListActivity::class.java)) }
        }
        root.addView(excludeBtn)

        val sectionService = TextView(this).apply {
            text = "\nService"
            textSize = 18f
        }
        root.addView(sectionService)

        val startBtn = Button(this).apply {
            text = "3. Start AppBlur"
            setOnClickListener { startBlurService() }
        }
        root.addView(startBtn)

        val stopBtn = Button(this).apply {
            text = "Stop AppBlur"
            setOnClickListener { stopBlurService() }
        }
        root.addView(stopBtn)

        val note = TextView(this).apply {
            text = "\nOnce running, a small round button floats on screen. " +
                    "Tap it to pause/resume watching, drag to move it, long-press to cycle the delay."
            textSize = 13f
        }
        root.addView(note)

        setContentView(root)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasUsage = ForegroundAppDetector.hasUsageAccess(this)
        statusText.text = buildString {
            append(if (hasOverlay) "Overlay: granted\n" else "Overlay: NOT granted\n")
            append(if (hasUsage) "Usage access: granted\n" else "Usage access: not granted (exclusions off)\n")
            append(if (BlurOverlayService.isRunning) "Service: running" else "Service: stopped")
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Enable 'Allow display over other apps', then come back", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestUsageAccess() {
        if (!ForegroundAppDetector.hasUsageAccess(this)) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Toast.makeText(this, "Find AppBlur in the list and enable usage access", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBlurService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant overlay permission first (step 1)", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf("android.permission.POST_NOTIFICATIONS"), 100)
        }
        val intent = Intent(this, BlurOverlayService::class.java)
        intent.putExtra(BlurOverlayService.EXTRA_TIMEOUT_MS, timeoutSeconds * 1000L)
        ContextCompat.startForegroundService(this, intent)
        refreshStatus()
    }

    private fun stopBlurService() {
        val intent = Intent(this, BlurOverlayService::class.java).apply { action = BlurOverlayService.ACTION_STOP }
        startService(intent)
        refreshStatus()
    }
}                        
