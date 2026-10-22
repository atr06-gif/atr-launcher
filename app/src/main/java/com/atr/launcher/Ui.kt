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
class TileView(ctx: Context, val label: String, private val banner: Drawable?, private val icon: Drawable?, private val glyph: String? = null) : View(ctx) {
    var theme: Theme = Config.theme
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
                tp.color = theme.accent; tp.typeface = U.bold; tp.textSize = U.pxf(64)
                c.drawText(glyph, w / 2, h * 0.52f, tp)
                tp.color = theme.cardText; tp.typeface = U.medium; tp.textSize = U.pxf(26)
                c.drawText(label, w / 2, h * 0.82f, tp)
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

    fun add(tile: View, w: Int = 300, h: Int = 169, gap: Int = 26) {
        strip.addView(tile, U.lp(U.px(w), U.px(h), 0, 0, U.px(gap), 0))
        U.focusGrow(tile) { has ->
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
            (background as LayerDrawable).getDrawable(1).let { (it as GradientDrawable).setStroke(if (has) U.px(4) else 0, theme.accent) }
        }
    }

    fun set(b: String, l1: String = "", l2: String = "", l3: String = "") {
        big.text = b; line1.text = l1; line2.text = l2; line3.text = l3
        line3.visibility = if (l3.isBlank()) View.GONE else View.VISIBLE
    }
}
