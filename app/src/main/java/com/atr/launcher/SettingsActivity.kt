package com.atr.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** ATR Settings: updates, config, hidden apps, Home button status. Built in code, same look as home. */
class SettingsActivity : Activity() {
    private lateinit var list: LinearLayout
    private val th get() = Config.theme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        U.init(this)
        val root = FrameLayout(this)
        root.addView(BackgroundView(this), FrameLayout.LayoutParams(U.MATCH, U.MATCH))
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(U.px(160), U.px(70), U.px(160), U.px(80)) }
        scroll.addView(list); root.addView(scroll)
        setContentView(root)
        build()
    }

    override fun onResume() { super.onResume(); if (::list.isInitialized) build() }

    private fun build() {
        list.removeAllViews()
        list.addView(U.text(this, 56, th.ink, U.bold, "ATR Settings"))
        list.addView(U.text(this, 24, th.inkMuted, U.medium, "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"), U.lp(U.WRAP, U.WRAP, 0, U.px(8), 0, U.px(30)))

        section("Updates")
        button("Check for updates now") {
            toast("Checking…")
            App.bg({ Updater.check() ?: Updater.NONE }) { info ->
                if (info === Updater.NONE) toast("You're on the latest version")
                else App.bg({ Updater.download(this, info) ?: File("") }) { f -> if (f.exists()) Updater.install(this, f) else toast("Download failed") }
            }
        }
        button("Pull latest look and layout from the internet") {
            App.bg({ RemoteConfig.pull(this) }) { changed -> toast(if (changed) "Updated. Press Home to see it." else "Already up to date") }
        }

        section("Lock")
        info("Locks after ${Config.lockMinutes} minutes without a button press, and whenever the TV turns off.")
        button("Lock now") { Lock.lockNow(); finishAffinity() }
        button("Change PIN") {
            getSharedPreferences("atr_lock", MODE_PRIVATE).edit().clear().apply()
            Lock.lockNow(); toast("Open ATR Launcher to set a new PIN"); finishAffinity()
        }

        section("Your keys")
        info("Notion (ATR Global rings): " + if (Secrets.has(this, Secrets.NOTION_TOKEN)) "linked" else "not linked")
        info("Google Calendar (Next up card): " + if (Secrets.has(this, Secrets.CALENDAR_ICS)) "linked" else "not linked")
        info("Robotics calendar (Week ahead): " + if (Secrets.has(this, Secrets.ROBOTICS_ICS)) "linked" else "not linked")
        info("Notion tasks (Today card): " + if (Secrets.has(this, Secrets.NOTION_TASKS_DATA_SOURCE)) "linked" else "not linked")
        info("To change these, edit my_keys.json on your PC and run 7 - Finish setup.")
        button("Forget my keys on this TV") { Secrets.clear(this); toast("Keys removed"); build() }

        section("Home button")
        val on = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(packageName)
        info(if (on) "While unlocked, Home brings you back here. When locked, Home goes to Amazon for everyone else." else "Home button helper is OFF. Turn on \"ATR Home button\" in Accessibility.")
        if (!on) button("Open Accessibility settings") { runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }

        section("ATR Global rings")
        info("Click the ATR Global card on the home screen to pick up to 4 rings. With none picked it shows your exam prep rings.")
        if (Config.ringPicks.isNotEmpty()) button("Go back to exam prep rings") { Config.ringPicks = mutableListOf(); toast("Done. Press Home."); build() }

        section("Hidden apps")
        val hidden = Config.hidden
        if (hidden.isEmpty()) info("Nothing hidden")
        hidden.forEach { pkg ->
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            button("Show $label again") { Config.unhide(pkg); build() }
        }

        section("Reset")
        button("Reset layout to default") {
            File(filesDir, "config.json").delete(); Config.load(this); toast("Reset. Press Home.")
        }
        button("Open Fire TV settings") { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }

        list.getChildAt(2)?.let { list.post { findFirstButton()?.requestFocus() } }
    }

    private fun findFirstButton(): View? = (0 until list.childCount).map { list.getChildAt(it) }.firstOrNull { it.isFocusable }

    private fun section(t: String) = list.addView(U.text(this, 30, th.ink, U.bold, t), U.lp(U.WRAP, U.WRAP, 0, U.px(34), 0, U.px(12)))
    private fun info(t: String) = list.addView(U.text(this, 24, th.inkMuted, U.regular, t).apply { maxLines = 3 }, U.lp(U.MATCH, U.WRAP, 0, 0, 0, U.px(10)))

    private fun button(t: String, go: () -> Unit) {
        val b = TextView(this).apply {
            text = t; setTextColor(th.cardText); typeface = U.medium
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, U.pxf(28))
            setPadding(U.px(32), U.px(22), U.px(32), U.px(22))
            isFocusable = true; isClickable = true
            val bgd = android.graphics.drawable.GradientDrawable().apply { cornerRadius = U.pxf(16); setColor(th.card) }
            background = bgd
            setOnClickListener { go() }
            setOnFocusChangeListener { v, has ->
                if (has) bgd.setStroke(U.px(4), th.accent) else bgd.setStroke(0, android.graphics.Color.TRANSPARENT)
                v.animate().scaleX(if (has) 1.02f else 1f).scaleY(if (has) 1.02f else 1f).setDuration(120).start()
            }
        }
        list.addView(b, U.lp(U.MATCH, U.WRAP, 0, U.px(8), 0, U.px(8)))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
