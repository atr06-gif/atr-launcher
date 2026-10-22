package com.atr.launcher

import android.app.Application
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Config.load(this)
        // TV going to standby locks the ATR side
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) { Lock.lockNow() }
        }, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF))
    }

    companion object {
        lateinit var instance: App
        val io = Executors.newFixedThreadPool(2)
        val main = Handler(Looper.getMainLooper())

        /** Run [work] off the UI thread, then hand the result to [done] on the UI thread. */
        fun <T> bg(work: () -> T, done: (T) -> Unit) {
            io.execute {
                val r: T? = try { work() } catch (e: Exception) { android.util.Log.e("ATR", "bg", e); null }
                if (r != null) main.post { done(r) }
            }
        }
    }
}
