package com.abhiram.appblur

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

object ForegroundAppDetector {

    fun hasUsageAccess(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Returns the package name currently in the foreground, scanning usage events
     * from the last few seconds. Returns null if it can't be determined.
     */
    fun currentForegroundPackage(ctx: Context): String? {
        if (!hasUsageAccess(ctx)) return null

        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 10_000 // look back 10s, cheap enough to poll every couple seconds

        val events = usm.queryEvents(start, end)
        var lastPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }
}
