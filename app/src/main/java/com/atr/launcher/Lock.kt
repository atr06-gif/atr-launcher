package com.atr.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PIN lock for the ATR side. The unlocked state only lives in memory, so it resets to locked
 * whenever the TV turns off, the app restarts, or nobody touches it for [Config.lockMinutes].
 */
object Lock {
    private const val PREFS = "atr_lock"
    @Volatile private var unlockedUntil = 0L
    private var failures = 0
    private var cooldownUntil = 0L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun hasPin(ctx: Context) = prefs(ctx).contains("hash")
    val isUnlocked get() = System.currentTimeMillis() < unlockedUntil

    fun touch() { if (isUnlocked) unlockedUntil = System.currentTimeMillis() + Config.lockMinutes * 60_000L }
    fun lockNow() { unlockedUntil = 0L }
    private fun unlock() { failures = 0; unlockedUntil = System.currentTimeMillis() + Config.lockMinutes * 60_000L }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs(ctx).edit().putString("salt", salt).putString("hash", hash(salt, pin)).apply()
        unlock()
    }

    /** Returns null on success, or a message to show. */
    fun tryPin(ctx: Context, pin: String): String? {
        val now = System.currentTimeMillis()
        if (now < cooldownUntil) return "Too many tries. Wait ${(cooldownUntil - now) / 1000 + 1}s"
        val p = prefs(ctx)
        if (hash(p.getString("salt", "") ?: "", pin) == p.getString("hash", null)) { unlock(); return null }
        failures++
        if (failures >= 5) { cooldownUntil = now + 30_000; failures = 0; return "Too many tries. Wait 30s" }
        return "Wrong PIN"
    }
}

/**
 * Full screen PIN pad driven by the remote: arrows to move, OK to press.
 * Mode CREATE asks twice, mode ENTER checks against the saved PIN.
 */
class PinView(ctx: Context, private val th: Theme, private val creating: Boolean, private val onDone: () -> Unit) : FrameLayout(ctx) {
    private var entry = ""
    private var firstPin: String? = null
    private val title = U.text(ctx, 44, th.ink, U.bold)
    private val dots = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private val msg = U.text(ctx, 24, th.inkMuted, U.medium).apply { gravity = Gravity.CENTER }
    private val len = 4

    init {
        isClickable = true; isFocusable = false
        addView(BackgroundView(ctx).apply { theme = th }, LayoutParams(U.MATCH, U.MATCH))
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(android.widget.ImageView(ctx).apply {
            setImageResource(R.drawable.atr_logo); adjustViewBounds = true
            colorFilter = android.graphics.PorterDuffColorFilter(th.logo, android.graphics.PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(U.WRAP, U.px(72)).apply { bottomMargin = U.px(40) })
        col.addView(title, U.lp(U.WRAP, U.WRAP))
        col.addView(dots, U.lp(U.WRAP, U.px(40), 0, U.px(28), 0, U.px(10)))
        col.addView(msg, U.lp(U.WRAP, U.px(36), 0, 0, 0, U.px(24)))

        val grid = GridLayout(ctx).apply { columnCount = 3; rowCount = 4 }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        keys.forEach { k -> grid.addView(key(ctx, k), GridLayout.LayoutParams().apply {
            width = U.px(150); height = U.px(110); setMargins(U.px(10), U.px(10), U.px(10), U.px(10))
        }) }
        // Fixed size so the parent can't squeeze the keypad: 3 keys of 150 plus 10 margin each side, 4 rows of 110
        col.addView(grid, LinearLayout.LayoutParams(U.px(3 * 170), U.px(4 * 130)))
        addView(col, LayoutParams(U.MATCH, U.WRAP, Gravity.CENTER))
        title.text = if (creating) "Create your ATR PIN" else "Enter PIN"
        msg.text = if (creating) "4 digits. Only you can open this side." else " "
        drawDots()
        post { grid.getChildAt(4)?.requestFocus() }
    }

    private fun key(ctx: Context, k: String): View = TextView(ctx).apply {
        text = k; gravity = Gravity.CENTER; typeface = U.bold
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, U.pxf(if (k == "OK") 34 else 44))
        setTextColor(th.cardText)
        val bg = GradientDrawable().apply { cornerRadius = U.pxf(18); setColor(if (k == "OK") th.accent else th.card) }
        background = bg
        isFocusable = true; isClickable = true
        setOnFocusChangeListener { v, has ->
            if (has) bg.setStroke(U.px(5), if (k == "OK") th.cardText else th.accent) else bg.setStroke(0, android.graphics.Color.TRANSPARENT)
            v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(110).start()
        }
        setOnClickListener { press(k) }
    }

    private fun press(k: String) {
        when (k) {
            "⌫" -> entry = entry.dropLast(1)
            "OK" -> submit()
            else -> if (entry.length < len) { entry += k; if (entry.length == len) postDelayed({ submit() }, 120) }
        }
        drawDots()
    }

    private fun submit() {
        if (entry.length < len) { msg.text = "Enter all $len digits"; return }
        val pin = entry; entry = ""
        if (creating) {
            val first = firstPin
            if (first == null) { firstPin = pin; title.text = "Type it again"; msg.text = " " }
            else if (first == pin) { Lock.setPin(context, pin); onDone() }
            else { firstPin = null; title.text = "Create your ATR PIN"; msg.text = "Didn't match. Start over."; shake() }
        } else {
            val err = Lock.tryPin(context, pin)
            if (err == null) onDone() else { msg.text = err; shake() }
        }
        drawDots()
    }

    private fun drawDots() {
        dots.removeAllViews()
        repeat(len) { i ->
            dots.addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (i < entry.length) setColor(th.accent) else { setColor(0); setStroke(U.px(3), th.ink) }
                }
            }, U.lp(U.px(26), U.px(26), U.px(12), 0, U.px(12), 0))
        }
    }

    private fun shake() {
        dots.animate().translationX(U.pxf(18)).setDuration(50).withEndAction {
            dots.animate().translationX(-U.pxf(18)).setDuration(50).withEndAction { dots.animate().translationX(0f).setDuration(50).start() }.start()
        }.start()
    }
}
