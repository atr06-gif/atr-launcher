package com.atr.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Good morning, Anthony": shown the first time the ATR side is unlocked each day. Any button closes it. */
class GreetingView(ctx: Context, th: Theme, private val onClose: () -> Unit) : FrameLayout(ctx) {
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    private val lines = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }

    init {
        isFocusable = true; isFocusableInTouchMode = true; isClickable = true
        addView(BackgroundView(ctx).apply { theme = th }, LayoutParams(U.MATCH, U.MATCH))
        val now = ZonedDateTime.now(ZoneId.of("America/Chicago"))
        val hello = when (now.hour) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
        col.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.atr_logo); adjustViewBounds = true
            colorFilter = PorterDuffColorFilter(th.logo, PorterDuff.Mode.SRC_IN)
        }, LinearLayout.LayoutParams(U.WRAP, U.px(70)).apply { bottomMargin = U.px(36) })
        col.addView(U.text(ctx, 76, th.ink, U.bold, "$hello, Anthony").apply { gravity = Gravity.CENTER })
        col.addView(U.text(ctx, 28, th.accent, U.bold, now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)).uppercase()).apply { letterSpacing = 0.14f },
            U.lp(U.WRAP, U.WRAP, 0, U.px(12), 0, U.px(40)))
        col.addView(lines)
        col.addView(U.text(ctx, 20, th.inkMuted, U.medium, "Press any button to continue"), U.lp(U.WRAP, U.WRAP, 0, U.px(48), 0, 0))
        addView(col, LayoutParams(U.WRAP, U.WRAP, Gravity.CENTER))
        postDelayed({ close() }, 12_000)
    }

    fun addLine(text: String, color: Int) {
        if (text.isBlank()) return
        lines.addView(U.text(context, 34, color, U.medium, text).apply { gravity = Gravity.CENTER }, U.lp(U.WRAP, U.WRAP, 0, U.px(10), 0, U.px(10)))
    }

    private var closed = false
    fun close() { if (!closed) { closed = true; onClose() } }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) close()
        return true
    }
}

/**
 * ATR screensaver: clock, date, next race and weather on black. The block drifts to a new spot every
 * minute so nothing burns into the screen. Applies to the whole TV.
 */
class AtrDream : DreamService() {
    private lateinit var block: LinearLayout
    private lateinit var clock: android.widget.TextView
    private lateinit var date: android.widget.TextView
    private lateinit var info: android.widget.TextView
    private val zone = ZoneId.of("America/Chicago")
    private var race: RaceItem? = null
    private var weatherText = ""

    private val tick = object : Runnable {
        override fun run() {
            val now = ZonedDateTime.now(zone)
            clock.text = now.format(DateTimeFormatter.ofPattern(if (Config.clock24h) "HH:mm" else "h:mm", Locale.US))
            date.text = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)).uppercase()
            val r = race
            val raceText = if (r == null) "" else {
                val d = java.time.Duration.between(now, r.start)
                val cd = if (d.isNegative) "LIVE" else if (d.toDays() > 0) "${d.toDays()}d ${d.toHours() % 24}h" else "${d.toHours()}h ${d.toMinutes() % 60}m"
                "${r.series} ${r.event} ${r.session}  ·  $cd"
            }
            info.text = listOf(raceText, weatherText).filter { it.isNotBlank() }.joinToString("\n")
            App.main.postDelayed(this, 15_000)
        }
    }

    private val drift = object : Runnable {
        override fun run() {
            val root = block.parent as? FrameLayout ?: return
            val maxX = (root.width - block.width).coerceAtLeast(0); val maxY = (root.height - block.height).coerceAtLeast(0)
            block.animate().x((Math.random() * maxX).toFloat()).y((Math.random() * maxY).toFloat()).setDuration(4000).start()
            App.main.postDelayed(this, 60_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false; isFullscreen = true; isScreenBright = false
        U.init(this)
        val th = Theme.dark(null)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(U.px(40), U.px(40), U.px(40), U.px(40)) }
        block.addView(ImageView(this).apply {
            setImageResource(R.drawable.atr_logo); adjustViewBounds = true
            colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN); alpha = 0.9f
        }, LinearLayout.LayoutParams(U.WRAP, U.px(56)).apply { bottomMargin = U.px(20) })
        clock = U.text(this, 200, Color.WHITE, U.bold)
        date = U.text(this, 30, th.accent, U.bold).apply { letterSpacing = 0.16f }
        info = U.text(this, 28, th.cardMuted, U.medium).apply { maxLines = 3; gravity = Gravity.CENTER }
        block.addView(clock); block.addView(date, U.lp(U.WRAP, U.WRAP, 0, U.px(4), 0, U.px(24))); block.addView(info)
        root.addView(block, FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.CENTER))
        setContentView(root)
        val feeds = Feeds(this)
        App.bg({ feeds.races(1).firstOrNull() to feeds.weather() }) { (r, w) ->
            race = r
            weatherText = if (w == null) "" else "${w.nowF}°  ${Feeds.describe(w.code)}  ·  H ${w.hiF}°  L ${w.loF}°"
        }
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); App.main.post(tick); App.main.postDelayed(drift, 60_000) }
    override fun onDreamingStopped() { App.main.removeCallbacks(tick); App.main.removeCallbacks(drift); super.onDreamingStopped() }
}
