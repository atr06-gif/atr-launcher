package com.atr.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared between the home screen and the Home button watcher. */
object HomeState {
    /** While this is in the future, the watcher lets the Amazon home screen stay open (for the "Amazon Home" tile). */
    @Volatile var allowAmazonUntil = 0L
    @Volatile var lastRedirect = 0L
}

class LauncherActivity : Activity() {
    private lateinit var bg: BackgroundView
    private lateinit var content: LinearLayout
    private lateinit var rowsBox: LinearLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var updatePill: TextView
    private lateinit var logo: ImageView
    private val cards = LinkedHashMap<String, InfoCard>()
    private var ringsCard: RingsCard? = null
    private lateinit var rootFrame: FrameLayout
    private lateinit var weatherLine: TextView
    private var lockView: PinView? = null
    private var th = Config.theme
    private var pendingUpdate: Updater.Info? = null
    private var lastFeeds = 0L
    private var lastRemote = 0L
    private var lastUpdateCheck = 0L

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            if (!Lock.isUnlocked && lockView == null) showLock()
            val now = System.currentTimeMillis()
            if (now - lastFeeds > 10 * 60_000) refreshCards()
            if (now - lastRemote > 30 * 60_000) checkRemoteConfig()
            if (now - lastUpdateCheck > 6 * 3600_000) checkForUpdate()
            App.main.postDelayed(this, 15_000)
        }
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { buildRows() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        U.init(this)
        buildScreen()
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun onDestroy() { runCatching { unregisterReceiver(pkgReceiver) }; super.onDestroy() }

    override fun onResume() {
        super.onResume()
        HomeState.allowAmazonUntil = 0L
        App.bg({ Secrets.importIfPresent(this) }) { imported -> if (imported) { toast("Your keys were loaded"); refreshCards(force = true) } }
        if (!Lock.isUnlocked) showLock() else hideLock()
        App.main.removeCallbacks(tick); App.main.post(tick)
        // Coming back home always lands on the top of the screen
        if (::content.isInitialized) (content.parent as? ScrollView)?.scrollTo(0, 0)
        if (::rowsBox.isInitialized && currentFocus == null) focusFirstTile()
    }

    override fun onPause() { App.main.removeCallbacks(tick); super.onPause() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // On the PIN screen Back leaves the app. Once unlocked, use the Lock tile to leave.
        if (lockView != null) finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean { Lock.touch(); return super.dispatchKeyEvent(event) }

    private fun showLock() {
        if (lockView != null) return
        val creating = !Lock.hasPin(this)
        val v = PinView(this, th, creating) { hideLock(); refreshCards(force = true); focusFirstTile() }
        lockView = v
        // Nothing behind the PIN pad can take focus while locked
        (content.parent as? ScrollView)?.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        rootFrame.addView(v, FrameLayout.LayoutParams(U.MATCH, U.MATCH))
    }

    private fun hideLock() {
        lockView?.let { rootFrame.removeView(it) }; lockView = null
        (content.parent as? ScrollView)?.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    // ------------------------------------------------------------------ layout

    private fun buildScreen() {
        th = Config.theme
        lockView = null
        val root = FrameLayout(this)
        rootFrame = root
        bg = BackgroundView(this).apply { theme = this@LauncherActivity.th }
        root.addView(bg, FrameLayout.LayoutParams(U.MATCH, U.MATCH))

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; clipChildren = false; isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(0, 0, 0, U.px(80)) }
        scroll.addView(content)
        root.addView(scroll, FrameLayout.LayoutParams(U.MATCH, U.MATCH))

        content.addView(header(), U.lp(U.MATCH, U.px(150)))

        val cardRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        cards.clear(); ringsCard = null
        Config.cards.forEachIndexed { i, key ->
            val lp = LinearLayout.LayoutParams(0, U.px(300), if (key == "atr") 1.5f else 1f).apply { if (i > 0) leftMargin = U.px(28) }
            if (key == "atr") {
                val rc = RingsCard(this, th)
                rc.setOnClickListener { refreshCards(force = true) }
                ringsCard = rc; cardRow.addView(rc, lp); return@forEachIndexed
            }
            val title = when (key) { "race" -> "Race weekend"; "class" -> "Next up"; "aggies" -> "Aggie football"; "weather" -> Config.weatherLabel; else -> key }
            val card = InfoCard(this, title, th)
            card.setOnClickListener { Config.cardAction(key)?.let { pkg -> if (!AppRepo.launch(this, pkg)) toast("That app isn't installed") } }
            card.set("…")
            cards[key] = card
            cardRow.addView(card, lp)
        }
        if (cards.isNotEmpty() || ringsCard != null) content.addView(cardRow, U.lp(U.MATCH, U.WRAP, U.px(64), U.px(10), U.px(64), U.px(10)))

        rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        content.addView(rowsBox, U.lp(U.MATCH, U.WRAP))

        setContentView(root)
        buildRows()
        updateClock()
        refreshCards()
        if (!Lock.isUnlocked) showLock()
    }

    private fun header(): View {
        val h = FrameLayout(this)
        logo = ImageView(this).apply {
            setImageResource(R.drawable.atr_logo); adjustViewBounds = true
            colorFilter = PorterDuffColorFilter(th.logo, PorterDuff.Mode.SRC_IN)
        }
        h.addView(logo, FrameLayout.LayoutParams(U.WRAP, U.px(64)).apply { leftMargin = U.px(64); topMargin = U.px(48) })

        val right = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        updatePill = U.text(this, 22, android.graphics.Color.WHITE, U.bold).apply {
            visibility = View.GONE; isFocusable = true; isClickable = true
            setPadding(U.px(22), U.px(12), U.px(22), U.px(12))
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = U.pxf(40); setColor(th.accent) }
            setOnClickListener { pendingUpdate?.let { startUpdate(it) } }
        }
        U.focusGrow(updatePill, 1.08f)
        right.addView(updatePill, U.lp(U.WRAP, U.WRAP, 0, 0, U.px(28), 0))
        val clockCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        clock = U.text(this, 52, th.ink, U.bold)
        date = U.text(this, 22, th.inkMuted, U.medium)
        weatherLine = U.text(this, 22, th.inkMuted, U.medium)
        clockCol.addView(clock); clockCol.addView(date, U.lp(U.WRAP, U.WRAP, 0, U.px(4), 0, 0))
        clockCol.addView(weatherLine, U.lp(U.WRAP, U.WRAP, 0, U.px(4), 0, 0))
        right.addView(clockCol)
        h.addView(right, FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.END or Gravity.TOP).apply { rightMargin = U.px(64); topMargin = U.px(40) })
        return h
    }

    private fun buildRows() {
        App.bg({ Triple(AppRepo.load(this), Inputs.load(this), Config.rows) }) { (apps, inputs, specs) ->
            rowsBox.removeAllViews()
            val hidden = Config.hidden.toSet()
            val byPkg = apps.associateBy { it.pkg }
            val pinned = Config.pinned.mapNotNull { byPkg[it] }.filter { it.pkg !in hidden }
            for (spec in specs) {
                val row = RowView(this, spec.title, th)
                when (spec.type) {
                    "pinned" -> pinned.forEach { row.add(appTile(it, pinned = true)) }
                    "all" -> apps.filter { it.pkg !in hidden && pinned.none { p -> p.pkg == it.pkg } }.forEach { row.add(appTile(it, pinned = false)) }
                    "inputs" -> inputs.forEach { inp ->
                        val t = TileView(this, inp.label, null, null, glyph = "HDMI").apply { theme = this@LauncherActivity.th }
                        t.setOnClickListener { if (!Inputs.open(this, inp.id)) toast("Couldn't open ${inp.label}") }
                        row.add(t)
                    }
                    "system" -> systemTiles().forEach { row.add(it) }
                    "racing" -> racingTiles(row)
                }
                if (row.count > 0 || spec.type == "racing") rowsBox.addView(row, U.lp(U.MATCH, U.WRAP))
            }
            if (currentFocus == null || currentFocus !is TileView) focusFirstTile()
        }
    }

    private fun appTile(a: AppEntry, pinned: Boolean): View {
        val t = TileView(this, a.label, a.banner, a.icon).apply { theme = this@LauncherActivity.th }
        t.setOnClickListener { if (!AppRepo.launch(this, a.pkg)) toast("Couldn't open ${a.label}") }
        t.setOnLongClickListener { appMenu(a, pinned); true }
        t.setOnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_MENU && ev.action == KeyEvent.ACTION_UP) { appMenu(a, pinned); true } else false
        }
        return t
    }

    private fun systemTiles(): List<View> {
        fun tile(label: String, glyph: String, go: () -> Unit) =
            TileView(this, label, null, null, glyph).apply { theme = this@LauncherActivity.th; setOnClickListener { go() } }
        return listOf(
            tile("Fire TV Settings", "⚙") { runCatching { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            tile("ATR Settings", "ATR") { startActivity(Intent(this, SettingsActivity::class.java)) },
            tile("Amazon Home", "a") {
                HomeState.allowAmazonUntil = System.currentTimeMillis() + 30 * 60_000
                AppRepo.launch(this, "com.amazon.tv.launcher").also { ok -> if (!ok) runCatching {
                    startActivity(Intent().setClassName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.HomeActivity_vNext").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } }
            },
            tile("Lock and exit", "LOCK") { Lock.lockNow(); finish() },
            tile("Refresh", "↻") { checkRemoteConfig(force = true); refreshCards(force = true); buildRows(); toast("Refreshing") }
        )
    }

    private fun racingTiles(row: RowView) {
        val feeds = Feeds(this)
        App.bg({ feeds.weekend() to feeds.driverStandings(5) }) { (sessions, standings) ->
            val now = ZonedDateTime.now(java.time.ZoneId.of("America/Chicago"))
            sessions.forEach { s ->
                val done = s.start.isBefore(now.minusHours(2))
                val t = TileView(this, "${s.event} · ${s.start.format(short)}${if (done) " · done" else " · in " + countdown(s.start)}", null, null, s.session)
                    .apply { theme = this@LauncherActivity.th; glyphSize = if (s.session.length > 6) 38f else 56f; alpha = if (done) 0.55f else 1f }
                t.setOnClickListener { AppRepo.launch(this, "com.formulaone.production") }
                row.add(t)
            }
            standings.forEach { d ->
                val t = TileView(this, "${d.name} · ${d.team} · ${d.points} pts", null, null, "P${d.pos} ${d.code}")
                    .apply { theme = this@LauncherActivity.th; glyphSize = 44f }
                t.setOnClickListener { AppRepo.launch(this, "com.formulaone.production") }
                row.add(t)
            }
        }
    }

    private fun appMenu(a: AppEntry, pinned: Boolean) {
        val opts = mutableListOf<Pair<String, () -> Unit>>()
        fun opt(label: String, fn: () -> Unit) { opts.add(Pair(label, fn)) }
        if (pinned) {
            opt("Move left") { Config.movePinned(a.pkg, -1); buildRows() }
            opt("Move right") { Config.movePinned(a.pkg, 1); buildRows() }
            opt("Remove from Favorites") { Config.unpin(a.pkg); buildRows() }
        } else {
            opt("Add to Favorites") { Config.pin(a.pkg); buildRows() }
        }
        opt("Hide this app") { Config.hide(a.pkg); buildRows(); toast("Hidden. Bring it back in ATR Settings.") }
        opt("App info") { AppRepo.openAppInfo(this, a.pkg) }
        AlertDialog.Builder(this, R.style.AtrDialog).setTitle(a.label)
            .setItems(opts.map { it.first }.toTypedArray()) { _, i -> opts[i].second() }
            .show()
    }

    private fun focusFirstTile() {
        rowsBox.post {
            val first = (rowsBox.getChildAt(0) as? RowView)?.strip?.getChildAt(0)
            first?.requestFocus()
        }
    }

    // ------------------------------------------------------------------ live data

    private fun updateClock() {
        val now = ZonedDateTime.now(java.time.ZoneId.of("America/Chicago"))
        clock.text = now.format(DateTimeFormatter.ofPattern(if (Config.clock24h) "HH:mm" else "h:mm", Locale.US))
        date.text = now.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US))
    }

    private fun refreshCards(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - lastFeeds < 60_000) return
        lastFeeds = System.currentTimeMillis()
        val feeds = Feeds(this)
        cards["race"]?.let { card ->
            App.bg({ feeds.races(3) }) { items ->
                val n = items.firstOrNull()
                if (n == null) card.set("No races", "Schedule unavailable") else {
                    val more = items.drop(1).take(2).joinToString("   ") { "${it.session.ifBlank { it.event }} ${it.start.format(short)}" }
                    card.set(countdown(n.start), "${n.series} ${n.event}".trim(), "${n.session.ifBlank { "Green flag" }} · ${n.start.format(long)}", more)
                }
            }
        }
        cards["aggies"]?.let { card ->
            App.bg({ feeds.aggies() }) { (next, last) ->
                if (next == null) card.set("Gig 'em", last ?: "No game scheduled")
                else card.set((if (next.home) "vs " else "at ") + next.opponent, next.start.format(long),
                    "Kickoff in ${countdown(next.start)}" + if (next.tv.isNotBlank()) " · ${next.tv}" else "",
                    last?.let { "Last: $it" } ?: "")
            }
        }
        cards["class"]?.let { card ->
            App.bg({ CalendarFeed.upcoming(this, feeds) to Secrets.has(this, Secrets.CALENDAR_ICS) }) { (events, linked) ->
                val now = ZonedDateTime.now(java.time.ZoneId.of("America/Chicago"))
                val next = events.firstOrNull { it.end.isAfter(now) }
                when {
                    !linked -> card.set("Link calendar", "Run 6 - Send my keys to TV.bat", "on your PC")
                    next == null -> card.set("Free", "Nothing on the calendar", "for the next two weeks")
                    else -> {
                        val live = next.start.isBefore(now)
                        val then = events.firstOrNull { it.start.isAfter(next.start) }
                        card.set(if (live) "NOW" else countdown(next.start), next.title,
                            next.start.format(long) + if (next.location.isNotBlank()) " · ${next.location}" else "",
                            then?.let { "Then: ${it.title} ${it.start.format(short)}" } ?: "")
                    }
                }
            }
        }
        ringsCard?.let { rc ->
            App.bg({ NotionFeed.rings(this) to Secrets.has(this, Secrets.NOTION_TOKEN) }) { (rings, linked) ->
                when {
                    !linked -> rc.set(emptyList(), "Link Notion: run 6 - Send my keys to TV.bat")
                    rings.isEmpty() -> rc.set(emptyList(), "Couldn't reach Notion. Showing nothing yet.")
                    else -> rc.set(rings)
                }
            }
        }
        App.bg({ feeds.weather() ?: Weather(-999, 0, 0, 0, -1) }) { w ->
            weatherLine.text = if (w.nowF == -999) "" else "${w.nowF}° ${Feeds.describe(w.code)} · ${Config.weatherLabel}"
        }
        cards["weather"]?.let { card ->
            App.bg({ feeds.weather() ?: Weather(-999, 0, 0, 0, -1) }) { w ->
                if (w.nowF == -999) card.set("--°", "Weather unavailable")
                else card.set("${w.nowF}°", Feeds.describe(w.code), "H ${w.hiF}°  L ${w.loF}°  Rain ${w.rainPct}%", if (w.rainPct >= 50) "Grab an umbrella" else "")
            }
        }
    }

    private val long = DateTimeFormatter.ofPattern("EEE MMM d · h:mm a", Locale.US)
    private val short = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)

    private fun countdown(t: ZonedDateTime): String {
        val d = Duration.between(ZonedDateTime.now(t.zone), t)
        if (d.isNegative) return "LIVE"
        val days = d.toDays(); val hrs = d.toHours() % 24; val mins = d.toMinutes() % 60
        return when { days > 0 -> "${days}d ${hrs}h"; hrs > 0 -> "${hrs}h ${mins}m"; else -> "${mins}m" }
    }

    // ------------------------------------------------------------------ config and updates

    private fun checkRemoteConfig(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - lastRemote < 60_000) return
        lastRemote = System.currentTimeMillis()
        App.bg({ RemoteConfig.pull(this) }) { changed -> if (changed) { buildScreen(); toast("Home screen updated") } }
    }

    private fun checkForUpdate() {
        lastUpdateCheck = System.currentTimeMillis()
        App.bg({ Updater.check() ?: Updater.NONE }) { info ->
            if (info === Updater.NONE) { updatePill.visibility = View.GONE; return@bg }
            pendingUpdate = info
            updatePill.text = "Update ${info.name} ready · press to install"
            updatePill.visibility = View.VISIBLE
        }
    }

    private fun startUpdate(info: Updater.Info) {
        updatePill.text = "Downloading…"
        App.bg({ Updater.download(this, info) ?: java.io.File("") }) { f ->
            if (!f.exists()) { updatePill.text = "Download failed · try again"; return@bg }
            Updater.install(this, f)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
