package com.abhiram.appblur

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class AppListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(launcherIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val excluded = Prefs.getExcludedApps(this)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Never blur these apps"
            textSize = 22f
            setPadding(0, 0, 0, 24)
        }
        list.addView(title)

        val subtitle = TextView(this).apply {
            text = "Requires usage-access permission (grant it from the main screen first)."
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }
        list.addView(subtitle)

        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString()
            val checkbox = CheckBox(this).apply {
                text = label
                isChecked = excluded.contains(pkg)
                setOnCheckedChangeListener { _, isChecked ->
                    val current = Prefs.getExcludedApps(this@AppListActivity)
                    if (isChecked) current.add(pkg) else current.remove(pkg)
                    Prefs.setExcludedApps(this@AppListActivity, current)
                }
            }
            list.addView(checkbox)
        }

        scroll.addView(list)
        setContentView(scroll)
    }
}
