package com.atr.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Everything is designed on a 1920 x 1080 grid and scaled to the real screen. */
object U {
    var scale = 1f
    fun init(ctx: Context) { scale = ctx.resources.displayMetrics.widthPixels / 1920f }
    fun px(v: Number) = (v.toFloat() * scale).toInt()
    fun pxf(v: Number) = v.toFloat() * scale
    val bold: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    fun text(ctx: Context, size: Number, color: Int, tf: Typeface = regular, s: String = "") = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, pxf(size)); setTextColor(color); typeface = tf; text = s
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; includeFontPadding = false
    }

    fun lp(w: Int, h: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(l, t, r, b) }

    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    /** Grows a focused view a little, like every good TV UI. */
    fun focusGrow(v: View, amount: Float = 1.08f, onFocus: ((Boolean) -> Unit)? = null) {
        v.setOnFocusChangeListener { _, has ->
            v.animate().scaleX(if (has) amount else 1f).scaleY(if (has) amount else 1f).translationZ(if (has) pxf(12) else 0f).setDuration(140).start()
            onFocus?.invoke(has)
        }
    }
}

/** Light page with soft speed lines and a blue hairline along the top. */
class BackgroundView(ctx: Context) : View(ctx) {
    var theme: Theme = Config.theme; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(theme.page, theme.page, theme.pageLow), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.strokeWidth = U.pxf(3); p.strokeCap = Paint.Cap.ROUND
        val a = theme.accent
        listOf(Triple(860f, 1000f, 180), Triple(1040f, 1030f, 130), Triple(1260f, 970f, 100)).forEach { (x, y, al) ->
            p.shader = LinearGradient(U.pxf(x), U.pxf(y), w, U.pxf(y - 260), Color.argb(0, Color.red(a), Color.green(a), Color.blue(a)),
                Color.argb(al, Color.red(a), Color.green(a), Color.blue(a)), Shader.TileMode.CLAMP)
            c.drawLine(U.pxf(x), U.pxf(y), w, U.pxf(y - 260), p)
        }
        p.shader = null; p.color = a
        c.drawRect(0f, 0f, w, U.pxf(4), p)
    }
}

/** One app, input or shortcut. Wide TV banner when the app has one, otherwise icon plus name on a dark tile. */
@SuppressLint("ViewConstructor")
class TileView(ctx: Context, val label: String, private val banner: Drawable?, private val icon: Drawable?, private val glyph: String? = null, private val sub: String? = null) : View(ctx) {
    var theme: Theme = Config.theme
    var glyphSize = 64f
    private val radius = U.pxf(16)
    private val path = Path()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init { isFocusable = true; isFocusableInTouchMode = true; isClickable = true; contentDescription = label }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        path.reset(); path.addRoundRect(0f, 0f, w, h, radius, radius, Path.Direction.CW)
        c.save(); c.clipPath(path)
        p.color = theme.card; c.drawRect(0f, 0f, w, h, p)
        when {
            banner != null -> { banner.setBounds(0, 0, w.toInt(), h.toInt()); banner.draw(c) }
            glyph != null -> {
                tp.color = theme.accent; tp.typeface = U.bold; tp.textSize = U.pxf(glyphSize)
                c.drawText(glyph, w / 2, h * 0.52f, tp)
                tp.color = theme.cardText; tp.typeface = U.medium; tp.textSize = U.pxf(26)
                c.drawText(fit(sub ?: label, w - U.pxf(24)), w / 2, h * 0.82f, tp)
            }
            else -> {
                val s = (h * 0.46f).toInt()
                icon?.let { it.setBounds(((w - s) / 2).toInt(), (h * 0.14f).toInt(), ((w + s) / 2).toInt(), (h * 0.14f).toInt() + s); it.draw(c) }
                tp.color = theme.cardText; tp.typeface = U.medium; tp.textSize = U.pxf(24)
                c.drawText(fit(label, w - U.pxf(24)), w / 2, h * 0.86f, tp)
            }
        }
        c.restore()
        if (isFocused) {
            p.style = Paint.Style.STROKE; p.strokeWidth = U.pxf(5); p.color = theme.accent
            c.drawRoundRect(U.pxf(2.5), U.pxf(2.5), w - U.pxf(2.5), h - U.pxf(2.5), radius, radius, p)
            p.style = Paint.Style.FILL
        }
    }

    private fun fit(s: String, max: Float): String {
        if (tp.measureText(s) <= max) return s
        var t = s; while (t.length > 1 && tp.measureText("$t…") > max) t = t.dropLast(1); return "$t…"
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect); invalidate()
    }
}

/** A titled, sideways scrolling row of tiles. The focused tile's name shows under the row. */
class RowView(ctx: Context, title: String, theme: Theme) : LinearLayout(ctx) {
    val strip = LinearLayout(ctx).apply { orientation = HORIZONTAL; clipChildren = false; clipToPadding = false; setPadding(U.px(64), U.px(18), U.px(64), U.px(18)) }
    private val caption = U.text(ctx, 24, theme.inkMuted, U.medium)
    private val scroller = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFocusable = false; addView(strip) }

    init {
        orientation = VERTICAL; clipChildren = false; clipToPadding = false
        addView(U.text(ctx, 30, theme.ink, U.bold, title).apply { letterSpacing = 0.02f }, U.lp(U.WRAP, U.WRAP, U.px(64), U.px(26), 0, 0))
        addView(scroller, U.lp(U.MATCH, U.WRAP))
        addView(caption, U.lp(U.WRAP, U.WRAP, U.px(64), 0, 0, 0))
        caption.text = " "
    }

    fun add(tile: View, w: Int = 300, h: Int = 169, gap: Int = 26, grow: Float = 1.08f) {
        strip.addView(tile, U.lp(U.px(w), U.px(h), 0, 0, U.px(gap), 0))
        U.focusGrow(tile, grow) { has ->
            if (has) {
                caption.text = (tile as? TileView)?.label ?: tile.contentDescription ?: " "
                tile.post { smoothCenter(tile) }
            }
        }
    }

    private fun smoothCenter(v: View) {
        val target = v.left - (scroller.width - v.width) / 2
        scroller.smoothScrollTo(target.coerceAtLeast(0), 0)
    }

    val count get() = strip.childCount
}

/** Dark info card: blue label, big headline, two detail lines, accent strip along the bottom. */
class InfoCard(ctx: Context, label: String, private val theme: Theme) : LinearLayout(ctx) {
    val big = U.text(ctx, 64, theme.cardText, U.bold)
    val line1 = U.text(ctx, 28, theme.cardText, U.medium)
    val line2 = U.text(ctx, 24, theme.cardMuted)
    val line3 = U.text(ctx, 22, theme.cardMuted)

    init {
        orientation = VERTICAL
        setPadding(U.px(30), U.px(28), U.px(30), U.px(30))
        val r = U.pxf(22)
        val strip = GradientDrawable().apply { cornerRadius = r; setColor(theme.accent) }
        val body = GradientDrawable().apply { cornerRadius = r; setColor(theme.card) }
        background = LayerDrawable(arrayOf(strip, body)).apply { setLayerInset(1, 0, 0, 0, U.px(10)) }
        val head = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(View(ctx).apply { setBackgroundColor(theme.accent) }, U.lp(U.px(6), U.px(28), 0, 0, U.px(12), 0))
        head.addView(U.text(ctx, 24, theme.accent, U.bold, label.uppercase()).apply { letterSpacing = 0.12f })
        addView(head)
        addView(big, U.lp(U.WRAP, U.WRAP, 0, U.px(16), 0, 0))
        addView(line1, U.lp(U.WRAP, U.WRAP, 0, U.px(10), 0, 0))
        addView(line2, U.lp(U.WRAP, U.WRAP, 0, U.px(8), 0, 0))
        addView(line3, U.lp(U.WRAP, U.WRAP, 0, U.px(10), 0, 0))
        isFocusable = true; isClickable = true
        U.focusGrow(this, 1.03f) { has ->
            (background as LayerDrawable).getDrawable(1).let { (it as GradientDrawable).setStroke(if (has) U.px(4) else 0, if (has) theme.accent else Color.TRANSPARENT) }
        }
    }

    fun set(b: String, l1: String = "", l2: String = "", l3: String = "") {
        big.text = b; line1.text = l1; line2.text = l2; line3.text = l3
        line3.visibility = if (l3.isBlank()) View.GONE else View.VISIBLE
    }

    private var badgeFrac: Float? = null
    private var badgeColor = theme.accent
    private var badgeLabel = ""
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    /** Small progress ring in the top right corner (exam readiness). Null hides it. */
    fun setBadge(frac: Float?, color: Int = theme.accent, label: String = "") {
        badgeFrac = frac; badgeColor = color; badgeLabel = label; invalidate()
    }

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        val f = badgeFrac ?: return
        val d = U.pxf(96); val stroke = U.pxf(10)
        val cx = width - paddingRight - d / 2; val cy = paddingTop + d / 2 + U.pxf(4)
        val r = RectF(cx - d / 2 + stroke / 2, cy - d / 2 + stroke / 2, cx + d / 2 - stroke / 2, cy + d / 2 - stroke / 2)
        bp.style = Paint.Style.STROKE; bp.strokeWidth = stroke; bp.strokeCap = Paint.Cap.ROUND
        bp.color = Color.argb(48, Color.red(theme.cardText), Color.green(theme.cardText), Color.blue(theme.cardText))
        c.drawArc(r, 0f, 360f, false, bp)
        bp.color = badgeColor
        if (f > 0f) c.drawArc(r, -90f, 360f * f.coerceIn(0f, 1f), false, bp)
        btp.color = theme.cardText; btp.typeface = U.bold; btp.textSize = U.pxf(26)
        c.drawText("${Math.round(f * 100)}%", cx, cy + U.pxf(9), btp)
        if (badgeLabel.isNotBlank()) { btp.color = theme.cardMuted; btp.typeface = U.medium; btp.textSize = U.pxf(16); c.drawText(badgeLabel, cx, cy + d / 2 + U.pxf(22), btp) }
    }
}

/** ATR Global card: one progress ring per Ring Inputs row, in that row's color. */
class RingsCard(ctx: Context, private val theme: Theme) : LinearLayout(ctx) {
    private val rings = RingsView(ctx, theme)
    private val status = U.text(ctx, 22, theme.cardMuted)

    init {
        orientation = VERTICAL
        setPadding(U.px(30), U.px(28), U.px(30), U.px(26))
        val r = U.pxf(22)
        val strip = GradientDrawable().apply { cornerRadius = r; setColor(theme.accent) }
        val body = GradientDrawable().apply { cornerRadius = r; setColor(theme.card) }
        background = LayerDrawable(arrayOf(strip, body)).apply { setLayerInset(1, 0, 0, 0, U.px(10)) }
        val head = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(View(ctx).apply { setBackgroundColor(theme.accent) }, U.lp(U.px(6), U.px(28), 0, 0, U.px(12), 0))
        head.addView(U.text(ctx, 24, theme.accent, U.bold, "ATR GLOBAL").apply { letterSpacing = 0.12f })
        addView(head)
        addView(status, U.lp(U.WRAP, U.WRAP, 0, U.px(10), 0, 0))
        addView(rings, LayoutParams(U.MATCH, 0, 1f).apply { topMargin = U.px(6) })
        isFocusable = true; isClickable = true
        U.focusGrow(this, 1.03f) { has -> body.setStroke(if (has) U.px(4) else 0, if (has) theme.accent else Color.TRANSPARENT) }
    }

    /** Up to 4 big rings, no rotation. Pick which ones by clicking the card. */
    fun set(list: List<Ring>, message: String = "") {
        rings.list = list.take(4)
        status.text = message
        status.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }
}

class RingsView(ctx: Context, private val theme: Theme) : View(ctx) {
    var list: List<Ring> = emptyList(); set(v) { field = v.take(4); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val arc = RectF()

    override fun onDraw(c: Canvas) {
        if (list.isEmpty()) return
        val cols = list.size
        val cw = width.toFloat() / cols; val h = height.toFloat()
        val label = U.pxf(21); val sub = U.pxf(17)
        val d = minOf(cw * 0.78f, h - label * 1.6f - sub * 1.6f - U.pxf(10)).coerceAtLeast(U.pxf(40))
        val stroke = d * 0.11f
        list.forEachIndexed { i, r ->
            val cx = cw * i + cw / 2; val top = U.pxf(4)
            arc.set(cx - d / 2 + stroke / 2, top + stroke / 2, cx + d / 2 - stroke / 2, top + d - stroke / 2)
            p.style = Paint.Style.STROKE; p.strokeWidth = stroke; p.strokeCap = Paint.Cap.ROUND
            p.color = Color.argb(48, Color.red(theme.cardText), Color.green(theme.cardText), Color.blue(theme.cardText))
            c.drawArc(arc, 0f, 360f, false, p)
            val frac = if (r.goal > 0) (r.current / r.goal).coerceIn(0.0, 1.0).toFloat() else 0f
            p.color = r.color
            if (frac > 0f) c.drawArc(arc, -90f, 360f * frac, false, p)
            p.style = Paint.Style.FILL
            tp.color = theme.cardText; tp.typeface = U.bold; tp.textSize = d * 0.25f
            c.drawText("${Math.round(frac * 100)}%", cx, top + d / 2 + tp.textSize * 0.36f, tp)
            tp.color = theme.cardText; tp.typeface = U.medium; tp.textSize = label
            c.drawText(fit(r.metric, cw - U.pxf(12)), cx, top + d + label * 1.3f, tp)
            tp.color = theme.cardMuted; tp.typeface = U.regular; tp.textSize = sub
            val cur = if (r.current % 1.0 == 0.0) r.current.toInt().toString() else "%.1f".format(r.current)
            val goal = if (r.goal % 1.0 == 0.0) r.goal.toInt().toString() else "%.1f".format(r.goal)
            c.drawText("$cur / $goal", cx, top + d + label * 1.3f + sub * 1.4f, tp)
        }
    }

    private fun fit(s: String, max: Float): String {
        if (tp.measureText(s) <= max) return s
        var t = s; while (t.length > 1 && tp.measureText("$t…") > max) t = t.dropLast(1); return "$t…"
    }
}

/** Weather widget: now on the left, hour by hour temperature line and rain chance bars on the right. */
class WeatherCard(ctx: Context, label: String, private val theme: Theme) : LinearLayout(ctx) {
    private val now = U.text(ctx, 88, theme.cardText, U.bold)
    private val cond = U.text(ctx, 26, theme.cardText, U.medium)
    private val hilo = U.text(ctx, 22, theme.cardMuted)
    private val note = U.text(ctx, 20, theme.accent, U.medium)
    private val chart = HourlyChart(ctx, theme)

    init {
        orientation = VERTICAL
        setPadding(U.px(30), U.px(28), U.px(30), U.px(26))
        val r = U.pxf(22)
        val strip = GradientDrawable().apply { cornerRadius = r; setColor(theme.accent) }
        val body = GradientDrawable().apply { cornerRadius = r; setColor(theme.card) }
        background = LayerDrawable(arrayOf(strip, body)).apply { setLayerInset(1, 0, 0, 0, U.px(10)) }
        val head = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(View(ctx).apply { setBackgroundColor(theme.accent) }, U.lp(U.px(6), U.px(28), 0, 0, U.px(12), 0))
        head.addView(U.text(ctx, 24, theme.accent, U.bold, label.uppercase()).apply { letterSpacing = 0.12f })
        addView(head)
        val body2 = LinearLayout(ctx).apply { orientation = HORIZONTAL }
        val left = LinearLayout(ctx).apply { orientation = VERTICAL }
        left.addView(now, U.lp(U.WRAP, U.WRAP, 0, U.px(6), 0, 0))
        left.addView(cond, U.lp(U.WRAP, U.WRAP, 0, U.px(4), 0, 0))
        left.addView(hilo, U.lp(U.WRAP, U.WRAP, 0, U.px(8), 0, 0))
        left.addView(note, U.lp(U.WRAP, U.WRAP, 0, U.px(8), 0, 0))
        body2.addView(left, LayoutParams(U.px(230), U.MATCH))
        body2.addView(chart, LayoutParams(0, U.MATCH, 1f).apply { leftMargin = U.px(16) })
        addView(body2, LayoutParams(U.MATCH, 0, 1f).apply { topMargin = U.px(4) })
        isFocusable = true; isClickable = true
        U.focusGrow(this, 1.02f) { has -> body.setStroke(if (has) U.px(4) else 0, if (has) theme.accent else Color.TRANSPARENT) }
        now.text = "--°"
    }

    fun set(w: Weather?, hours: List<Hour>) {
        if (w == null) { now.text = "--°"; cond.text = "Weather unavailable"; hilo.text = ""; note.text = "" }
        else {
            now.text = "${w.nowF}°"; cond.text = Feeds.describe(w.code)
            hilo.text = "H ${w.hiF}°  L ${w.loF}°"
            val wet = hours.take(12).firstOrNull { it.rainPct >= 50 }
            note.text = when {
                wet != null -> "Rain likely around ${wet.time.format(java.time.format.DateTimeFormatter.ofPattern("h a", java.util.Locale.US))}"
                w.rainPct >= 50 -> "Grab an umbrella"
                else -> ""
            }
        }
        chart.hours = hours
    }
}

class HourlyChart(ctx: Context, private val theme: Theme) : View(ctx) {
    var hours: List<Hour> = emptyList(); set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hourFmt = java.time.format.DateTimeFormatter.ofPattern("h a", java.util.Locale.US)

    override fun onDraw(c: Canvas) {
        val hs = hours; if (hs.size < 2) return
        val w = width.toFloat(); val h = height.toFloat()
        val labelH = U.pxf(26); val tempLabel = U.pxf(28); val barMax = h * 0.22f
        val top = tempLabel + U.pxf(6); val lineBottom = h - labelH - barMax - U.pxf(10)
        val lo = hs.minOf { it.tempF }; val hi = hs.maxOf { it.tempF }; val span = (hi - lo).coerceAtLeast(4)
        val step = w / hs.size
        fun x(i: Int) = step * i + step / 2
        fun y(t: Int) = lineBottom - (t - lo).toFloat() / span * (lineBottom - top)

        // rain chance bars along the bottom
        hs.forEachIndexed { i, hr ->
            if (hr.rainPct <= 0) return@forEachIndexed
            val bh = barMax * hr.rainPct / 100f
            p.style = Paint.Style.FILL
            p.color = Color.argb(60 + hr.rainPct * 150 / 100, Color.red(theme.accent), Color.green(theme.accent), Color.blue(theme.accent))
            c.drawRoundRect(x(i) - step * 0.28f, h - labelH - bh, x(i) + step * 0.28f, h - labelH, U.pxf(4), U.pxf(4), p)
        }
        // temperature line
        val path = Path()
        hs.forEachIndexed { i, hr -> if (i == 0) path.moveTo(x(i), y(hr.tempF)) else path.lineTo(x(i), y(hr.tempF)) }
        p.style = Paint.Style.STROKE; p.strokeWidth = U.pxf(4); p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.color = theme.cardText
        c.drawPath(path, p)
        // dots, temps and hour labels every 3 hours (first point is "Now")
        p.style = Paint.Style.FILL
        hs.forEachIndexed { i, hr ->
            if (i % 3 != 0) return@forEachIndexed
            p.color = if (i == 0) theme.accent else theme.cardText
            c.drawCircle(x(i), y(hr.tempF), U.pxf(if (i == 0) 8 else 6), p)
            tp.typeface = U.bold; tp.textSize = U.pxf(24); tp.color = theme.cardText
            c.drawText("${hr.tempF}°", x(i), y(hr.tempF) - U.pxf(14), tp)
            tp.typeface = U.medium; tp.textSize = U.pxf(19); tp.color = theme.cardMuted
            c.drawText(if (i == 0) "Now" else hr.time.format(hourFmt), x(i), h - U.pxf(4), tp)
        }
    }
}


/** Shared dark card shell: rounded body, accent strip along the bottom, blue label, focus outline. */
open class CardShell(ctx: Context, label: String, protected val theme: Theme) : LinearLayout(ctx) {
    protected val body = GradientDrawable().apply { cornerRadius = U.pxf(22); setColor(theme.card) }
    init {
        orientation = VERTICAL
        setPadding(U.px(30), U.px(26), U.px(30), U.px(24))
        val strip = GradientDrawable().apply { cornerRadius = U.pxf(22); setColor(theme.accent) }
        background = LayerDrawable(arrayOf(strip, body)).apply { setLayerInset(1, 0, 0, 0, U.px(10)) }
        val head = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(View(ctx).apply { setBackgroundColor(theme.accent) }, U.lp(U.px(6), U.px(28), 0, 0, U.px(12), 0))
        head.addView(U.text(ctx, 24, theme.accent, U.bold, label.uppercase()).apply { letterSpacing = 0.12f })
        addView(head)
        isFocusable = true; isClickable = true
        U.focusGrow(this, 1.02f) { has -> body.setStroke(if (has) U.px(4) else 0, if (has) theme.accent else Color.TRANSPARENT) }
    }
}

/** Championship: drivers on the left, constructors on the right, each with a team color bar. */
class StandingsCard(ctx: Context, theme: Theme) : CardShell(ctx, "Championship", theme) {
    private val cols = LinearLayout(ctx).apply { orientation = HORIZONTAL }
    private val drivers = LinearLayout(ctx).apply { orientation = VERTICAL }
    private val teams = LinearLayout(ctx).apply { orientation = VERTICAL }
    init {
        cols.addView(drivers, LayoutParams(0, U.WRAP, 1.1f))
        cols.addView(teams, LayoutParams(0, U.WRAP, 1f).apply { leftMargin = U.px(36) })
        addView(cols, U.lp(U.MATCH, U.WRAP, 0, U.px(12), 0, 0))
    }

    private fun row(pos: Int, color: Int, name: String, sub: String, pts: String): View {
        val r = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r.addView(U.text(context, 26, theme.cardText, U.bold, "$pos").apply { gravity = Gravity.END }, U.lp(U.px(34), U.WRAP))
        r.addView(View(context).apply { background = GradientDrawable().apply { cornerRadius = U.pxf(3); setColor(color) } }, U.lp(U.px(8), U.px(30), U.px(14), 0, U.px(14), 0))
        val nameCol = LinearLayout(context).apply { orientation = VERTICAL }
        nameCol.addView(U.text(context, 24, theme.cardText, U.medium, name))
        if (sub.isNotBlank()) nameCol.addView(U.text(context, 16, theme.cardMuted, U.regular, sub))
        r.addView(nameCol, LayoutParams(0, U.WRAP, 1f))
        r.addView(U.text(context, 22, theme.cardMuted, U.medium, pts), U.lp(U.WRAP, U.WRAP, U.px(10), 0, 0, 0))
        return r
    }

    fun set(ds: List<Feeds.Standing>, ts: List<Feeds.TeamStanding>) {
        drivers.removeAllViews(); teams.removeAllViews()
        drivers.addView(U.text(context, 18, theme.cardMuted, U.bold, "DRIVERS").apply { letterSpacing = 0.1f }, U.lp(U.WRAP, U.WRAP, 0, 0, 0, U.px(6)))
        teams.addView(U.text(context, 18, theme.cardMuted, U.bold, "CONSTRUCTORS").apply { letterSpacing = 0.1f }, U.lp(U.WRAP, U.WRAP, 0, 0, 0, U.px(6)))
        if (ds.isEmpty()) drivers.addView(U.text(context, 22, theme.cardMuted, U.regular, "Standings loading…"))
        ds.forEach { d -> drivers.addView(row(d.pos, Feeds.teamColor(d.teamId), d.name, d.team, d.points), U.lp(U.MATCH, U.WRAP, 0, U.px(5), 0, U.px(5))) }
        ts.forEach { t -> teams.addView(row(t.pos, Feeds.teamColor(t.teamId), t.name, "", t.points), U.lp(U.MATCH, U.WRAP, 0, U.px(9), 0, U.px(9))) }
    }
}

data class WeekItem(val start: java.time.ZonedDateTime, val text: String, val color: Int)

/** Seven day strip: one column per day, up to four events each, colored by source. */
class WeekCard(ctx: Context, theme: Theme) : CardShell(ctx, "Week ahead", theme) {
    private val days = LinearLayout(ctx).apply { orientation = HORIZONTAL }
    private val legend = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    init {
        addView(days, LayoutParams(U.MATCH, 0, 1f).apply { topMargin = U.px(10) })
        addView(legend, U.lp(U.WRAP, U.WRAP, 0, U.px(6), 0, 0))
    }

    fun set(items: List<WeekItem>, keys: List<Pair<String, Int>>) {
        days.removeAllViews(); legend.removeAllViews()
        val zone = java.time.ZoneId.of("America/Chicago"); val today = java.time.LocalDate.now(zone)
        val tf = java.time.format.DateTimeFormatter.ofPattern("h:mm", java.util.Locale.US)
        for (i in 0 until 7) {
            val day = today.plusDays(i.toLong())
            val col = LinearLayout(context).apply { orientation = VERTICAL; setPadding(U.px(8), 0, U.px(8), 0) }
            val name = if (i == 0) "Today" else day.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", java.util.Locale.US))
            col.addView(U.text(context, 20, if (i == 0) theme.accent else theme.cardText, U.bold, name))
            val list = items.filter { it.start.toLocalDate() == day }.sortedBy { it.start.toEpochSecond() }
            list.take(4).forEach { it2 ->
                val r = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                r.addView(View(context).apply { background = GradientDrawable().apply { cornerRadius = U.pxf(2); setColor(it2.color) } }, U.lp(U.px(5), U.px(34), 0, 0, U.px(8), 0))
                val t = LinearLayout(context).apply { orientation = VERTICAL }
                t.addView(U.text(context, 15, theme.cardMuted, U.medium, it2.start.format(tf)))
                t.addView(U.text(context, 17, theme.cardText, U.regular, it2.text))
                r.addView(t)
                col.addView(r, U.lp(U.MATCH, U.WRAP, 0, U.px(8), 0, 0))
            }
            if (list.size > 4) col.addView(U.text(context, 15, theme.cardMuted, U.regular, "+${list.size - 4} more"), U.lp(U.WRAP, U.WRAP, 0, U.px(6), 0, 0))
            if (list.isEmpty()) col.addView(U.text(context, 16, theme.cardMuted, U.regular, "Free"), U.lp(U.WRAP, U.WRAP, 0, U.px(8), 0, 0))
            days.addView(col, LayoutParams(0, U.MATCH, 1f))
            if (i < 6) days.addView(View(context).apply { setBackgroundColor(Color.argb(30, 255, 255, 255)) }, LayoutParams(U.px(1), U.MATCH))
        }
        keys.forEach { (label, color) ->
            legend.addView(View(context).apply { background = GradientDrawable().apply { cornerRadius = U.pxf(2); setColor(color) } }, U.lp(U.px(14), U.px(14), 0, 0, U.px(6), 0))
            legend.addView(U.text(context, 15, theme.cardMuted, U.medium, label), U.lp(U.WRAP, U.WRAP, 0, 0, U.px(20), 0))
        }
    }
}

/** Today's tasks from Notion. */
class TasksCard(ctx: Context, theme: Theme) : CardShell(ctx, "Today", theme) {
    private val list = LinearLayout(ctx).apply { orientation = VERTICAL }
    init { addView(list, U.lp(U.MATCH, U.WRAP, 0, U.px(12), 0, 0)) }

    fun set(tasks: List<TaskItem>?, message: String = "") {
        list.removeAllViews()
        if (tasks == null || tasks.isEmpty()) {
            list.addView(U.text(context, 32, theme.cardText, U.bold, if (tasks == null) "Link tasks" else "All clear"))
            list.addView(U.text(context, 20, theme.cardMuted, U.regular, message.ifBlank { "Nothing due or scheduled today" }).apply { maxLines = 2 }, U.lp(U.MATCH, U.WRAP, 0, U.px(8), 0, 0))
            return
        }
        val zone = java.time.ZoneId.of("America/Chicago"); val today = java.time.LocalDate.now(zone)
        val tf = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US)
        tasks.take(5).forEach { t ->
            val r = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val overdue = t.due != null && t.due.isBefore(today)
            val dot = if (overdue) Color.parseColor("#FF4D4D") else if (t.priority == "High") theme.accent else theme.cardMuted
            r.addView(View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dot) } }, U.lp(U.px(10), U.px(10), 0, 0, U.px(12), 0))
            val col = LinearLayout(context).apply { orientation = VERTICAL }
            col.addView(U.text(context, 21, theme.cardText, U.medium, t.title))
            val when_ = when { overdue -> "Overdue since ${t.due!!.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.US))}"
                t.scheduled != null && t.scheduled.toLocalDate() == today && (t.scheduled.hour != 0 || t.scheduled.minute != 0) -> t.scheduled.format(tf)
                t.due != null && t.due == today -> "Due today"; else -> "Today" }
            col.addView(U.text(context, 16, if (overdue) Color.parseColor("#FF8A8A") else theme.cardMuted, U.regular, when_))
            r.addView(col, LayoutParams(0, U.WRAP, 1f))
            list.addView(r, U.lp(U.MATCH, U.WRAP, 0, U.px(6), 0, U.px(6)))
        }
        if (tasks.size > 5) list.addView(U.text(context, 16, theme.cardMuted, U.regular, "+${tasks.size - 5} more in Notion"))
    }
}

/**
 * Holds several card rows in one slot and crossfades between them every [seconds].
 * It never switches while something inside it has focus, so it won't move under the remote.
 */
class RotatorView(ctx: Context, private val seconds: Int) : android.widget.FrameLayout(ctx) {
    private val pages = mutableListOf<View>()
    private var index = 0
    private val dots = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private val next = object : Runnable {
        override fun run() {
            if (pages.size > 1 && !hasFocus()) show((index + 1) % pages.size)
            postDelayed(this, seconds * 1000L)
        }
    }

    init { clipChildren = false; clipToPadding = false }

    fun addPage(v: View) {
        (v as? android.view.ViewGroup)?.clipChildren = false
        pages += v
        addView(v, LayoutParams(U.MATCH, U.MATCH))
        v.visibility = if (pages.size == 1) View.VISIBLE else View.GONE
        drawDots()
    }

    private fun show(i: Int) {
        val old = pages[index]; val new = pages[i]; index = i
        new.alpha = 0f; new.visibility = View.VISIBLE
        new.animate().alpha(1f).setDuration(500).start()
        old.animate().alpha(0f).setDuration(500).withEndAction { old.visibility = View.GONE; old.alpha = 1f }.start()
        drawDots()
    }

    private fun drawDots() {
        dots.removeAllViews()
        if (pages.size < 2) return
        pages.indices.forEach { i ->
            dots.addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i == index) Config.theme.accent else Color.argb(90, 128, 128, 128)) }
            }, U.lp(U.px(10), U.px(10), U.px(5), 0, U.px(5), 0))
        }
    }

    /** Right on the last card goes to the next page, Left on the first goes back. Wraps around. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == android.view.KeyEvent.ACTION_DOWN && pages.size > 1 &&
            (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT || code == android.view.KeyEvent.KEYCODE_DPAD_LEFT)) {
            val page = pages[index] as? android.view.ViewGroup
            val focused = findFocus()
            if (page != null && focused != null) {
                val dir = if (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) View.FOCUS_RIGHT else View.FOCUS_LEFT
                if (android.view.FocusFinder.getInstance().findNextFocus(page, focused, dir) == null) {
                    val ni = if (dir == View.FOCUS_RIGHT) (index + 1) % pages.size else (index - 1 + pages.size) % pages.size
                    show(ni)
                    val target = pages[ni] as? android.view.ViewGroup
                    val kids = (0 until (target?.childCount ?: 0)).mapNotNull { target?.getChildAt(it) }.filter { it.isFocusable }
                    (if (dir == View.FOCUS_RIGHT) kids.firstOrNull() else kids.lastOrNull())?.requestFocus()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** The little page dots, placed by the caller under the rotator. */
    fun dotsView(): View = dots

    override fun onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(next); postDelayed(next, seconds * 1000L) }
    override fun onDetachedFromWindow() { removeCallbacks(next); super.onDetachedFromWindow() }
}

/** One race day in the Racing hub: day name, then each session with its time and countdown. */
class DayCard(ctx: Context, title: String, theme: Theme) : CardShell(ctx, title, theme) {
    private val list = LinearLayout(ctx).apply { orientation = VERTICAL }
    init { addView(list, U.lp(U.MATCH, U.WRAP, 0, U.px(14), 0, 0)) }

    fun add(session: String, time: String, status: String, done: Boolean) {
        val r = LinearLayout(context).apply { orientation = VERTICAL; alpha = if (done) 0.5f else 1f }
        val top = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.BOTTOM }
        top.addView(U.text(context, 40, theme.cardText, U.bold, session))
        top.addView(U.text(context, 22, theme.accent, U.bold, status), U.lp(U.WRAP, U.WRAP, U.px(14), 0, 0, U.px(6)))
        r.addView(top)
        r.addView(U.text(context, 22, theme.cardMuted, U.medium, time))
        list.addView(r, U.lp(U.MATCH, U.WRAP, 0, U.px(8), 0, U.px(12)))
    }
}
