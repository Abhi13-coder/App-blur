package com.abhiram.appblur

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 80)
        }

        fun heading(text: String, size: Float = 22f) = TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, 32, 0, 12)
        }

        fun body(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setLineSpacing(6f, 1.1f)
        }

        root.addView(TextView(this).apply {
            text = "AppBlur"
            textSize = 28f
        })
        root.addView(body("v1.0 — first release"))

        root.addView(heading("Built by"))
        root.addView(body(
            "Abhiram, an independent 19-year-old developer from Warangal, Telangana, India. " +
            "Built entirely on a Redmi 9A phone — no laptop, no desktop, just Termux and " +
            "GitHub Actions as the build system. This is the first app he's ever shipped, " +
            "start to finish, on his own."
        ))

        root.addView(heading("How it got built"))
        root.addView(body(
            "AppBlur exists because of a genuinely clever workaround: Android doesn't let " +
            "normal apps detect touches happening in other apps, so this app uses a transparent, " +
            "non-touchable overlay window that still gets notified whenever a touch happens " +
            "anywhere on screen. No accessibility service, no screen recording, no special " +
            "system access — just a smart use of an existing Android API."
        ))

        root.addView(heading("AI assistance"))
        root.addView(body(
            "Development was done with Claude (Anthropic) as a coding assistant — pairing on " +
            "architecture, writing and debugging the Kotlin code, and troubleshooting the CI " +
            "build pipeline. Every decision, every build, and every bit of testing on real " +
            "hardware was Abhiram's."
        ))

        root.addView(heading("Privacy"))
        root.addView(body(
            "AppBlur has no internet permission and makes no network calls. Nothing you do " +
            "in this app, or any app it watches over, ever leaves your phone."
        ))

        scroll.addView(root)
        setContentView(scroll)
    }
}
