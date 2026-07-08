package com.abhiram.appblur

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Two overlay windows are managed here:
 *  1. A full-screen, non-touchable scrim window that watches ACTION_OUTSIDE touches
 *     (system-wide idle detection with zero accessibility/usage-stats requirement).
 *  2. A small round, touchable, draggable toggle button on top of everything.
 *
 * Per-app exclusion is done via UsageStatsManager polling (needs a one-time
 * "usage access" grant, separate from the overlay permission).
 */
class BlurOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "appblur_channel"
        const val NOTIF_ID = 1
        const val EXTRA_TIMEOUT_MS = "timeout_ms"
        const val ACTION_STOP = "com.abhiram.appblur.STOP"
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var scrimRoot: FrameLayout
    private lateinit var scrimView: View
    private lateinit var buttonView: TextView
    private lateinit var buttonParams: WindowManager.LayoutParams

    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutMs: Long = 8000L
    private var blurred = false
    private var scrimAdded = false
    private var buttonAdded = false
    private var watchingEnabled = true

    private val blurRunnable = Runnable { maybeShowBlur() }
    private val foregroundPollRunnable = object : Runnable {
        override fun run() {
            // Only matters when a blur would otherwise be about to show; cheap enough to poll always.
            mainHandler.postDelayed(this, 2000)
        }
    }

    private var lastForegroundPkg: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        watchingEnabled = Prefs.isWatchingEnabled(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        timeoutMs = intent?.getLongExtra(EXTRA_TIMEOUT_MS, Prefs.getTimeoutSeconds(this) * 1000L)
            ?: (Prefs.getTimeoutSeconds(this) * 1000L)

        startForeground(NOTIF_ID, buildNotification())
        if (!scrimAdded) addScrim()
        if (!buttonAdded) addToggleButton()
        mainHandler.post(foregroundPollRunnable)
        resetTimer()
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        mainHandler.removeCallbacks(blurRunnable)
        mainHandler.removeCallbacks(foregroundPollRunnable)
        if (scrimAdded) {
            windowManager.removeView(scrimRoot)
            scrimAdded = false
        }
        if (buttonAdded) {
            windowManager.removeView(buttonView)
            buttonAdded = false
        }
    }

    // ---------- Scrim (blur) window ----------

    private fun addScrim() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            notTouchableFlags(),
            PixelFormat.TRANSLUCENT
        )

        scrimRoot = FrameLayout(this).apply {
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    resetTimer()
                }
                false
            }
        }

        scrimView = View(this).apply {
            setBackgroundColor(0xCC101014.toInt())
            visibility = View.GONE
            setOnClickListener { hideBlur() }
        }
        scrimRoot.addView(
            scrimView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        windowManager.addView(scrimRoot, params)
        scrimAdded = true
    }

    private fun notTouchableFlags(): Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun touchableScrimFlags(): Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun resetTimer() {
        mainHandler.removeCallbacks(blurRunnable)
        if (blurred) hideBlur()
        if (watchingEnabled) {
            mainHandler.postDelayed(blurRunnable, timeoutMs)
        }
    }

    private fun maybeShowBlur() {
        if (!watchingEnabled) return

        val excluded = Prefs.getExcludedApps(this)
        if (excluded.isNotEmpty()) {
            val fg = ForegroundAppDetector.currentForegroundPackage(this)
            if (fg != null && excluded.contains(fg)) {
                // Excluded app is in front - don't blur, just check again after the same delay.
                mainHandler.postDelayed(blurRunnable, timeoutMs)
                return
            }
        }
        showBlur()
    }

    private fun showBlur() {
        blurred = true
        scrimView.visibility = View.VISIBLE
        scrimView.alpha = 0f
        scrimView.animate().alpha(1f).setDuration(250).start()

        val params = scrimRoot.layoutParams as WindowManager.LayoutParams
        params.flags = touchableScrimFlags()
        windowManager.updateViewLayout(scrimRoot, params)
    }

    private fun hideBlur() {
        blurred = false
        scrimView.animate().alpha(0f).setDuration(150).withEndAction {
            scrimView.visibility = View.GONE
        }.start()

        val params = scrimRoot.layoutParams as WindowManager.LayoutParams
        params.flags = notTouchableFlags()
        windowManager.updateViewLayout(scrimRoot, params)

        mainHandler.removeCallbacks(blurRunnable)
        if (watchingEnabled) {
            mainHandler.postDelayed(blurRunnable, timeoutMs)
        }
    }

    // ---------- Floating toggle button ----------

    private fun addToggleButton() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val sizePx = (56 * resources.displayMetrics.density).toInt()

        buttonParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = Prefs.getButtonX(this@BlurOverlayService)
            y = Prefs.getButtonY(this@BlurOverlayService)
        }

        buttonView = TextView(this).apply {
            text = "\u25C9" // filled circle glyph, doubles as a simple icon
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(if (watchingEnabled) R.drawable.button_on else R.drawable.button_off)
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                toggleWatching()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                cycleTimeout()
            }
        })

        var downRawX = 0f
        var downRawY = 0f
        var downParamX = 0
        var downParamY = 0

        buttonView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downParamX = buttonParams.x
                    downParamY = buttonParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    buttonParams.x = downParamX + dx
                    buttonParams.y = downParamY + dy
                    windowManager.updateViewLayout(buttonView, buttonParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    Prefs.setButtonPos(this, buttonParams.x, buttonParams.y)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(buttonView, buttonParams)
        buttonAdded = true
    }

    private fun toggleWatching() {
        watchingEnabled = !watchingEnabled
        Prefs.setWatchingEnabled(this, watchingEnabled)
        buttonView.setBackgroundResource(if (watchingEnabled) R.drawable.button_on else R.drawable.button_off)
        Toast.makeText(this, if (watchingEnabled) "AppBlur watching" else "AppBlur paused", Toast.LENGTH_SHORT).show()
        if (watchingEnabled) {
            resetTimer()
        } else {
            mainHandler.removeCallbacks(blurRunnable)
            if (blurred) hideBlur()
        }
    }

    private fun cycleTimeout() {
        val next = Prefs.nextTimeout((timeoutMs / 1000L).toInt())
        timeoutMs = next * 1000L
        Prefs.setTimeoutSeconds(this, next)
        Toast.makeText(this, "Blur delay: ${next}s", Toast.LENGTH_SHORT).show()
        resetTimer()
    }

    // ---------- Notification ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "AppBlur privacy watcher", NotificationManager.IMPORTANCE_MIN
            )
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, BlurOverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AppBlur active")
            .setContentText("Watching for idle touch across all apps")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }
}
