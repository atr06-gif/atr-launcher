package com.atr.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS always opens Amazon's home screen when Home is pressed and won't let another app be the default.
 * This service watches for that screen and immediately brings the ATR Launcher forward instead.
 * It only listens to com.amazon.tv.launcher and reads nothing on screen.
 */
class HomeWatcherService : AccessibilityService() {
    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (e.packageName?.toString() != AMAZON_HOME) return
        if (e.className?.toString()?.contains("HomeActivity") != true) return
        val now = System.currentTimeMillis()
        if (now < HomeState.allowAmazonUntil) return
        if (now - HomeState.lastRedirect < 700) return
        HomeState.lastRedirect = now
        startActivity(Intent(this, LauncherActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    override fun onInterrupt() {}

    companion object { const val AMAZON_HOME = "com.amazon.tv.launcher" }
}
