package com.abhiram.appblur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, BlurOverlayService::class.java)
                serviceIntent.putExtra(BlurOverlayService.EXTRA_TIMEOUT_MS, Prefs.getTimeoutSeconds(context) * 1000L)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
