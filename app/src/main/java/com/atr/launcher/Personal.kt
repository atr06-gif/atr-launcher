package com.atr.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class CalEvent(val title: String, val location: String, val start: ZonedDateTime, val end: ZonedDateTime)
data class Ring(val metric: String, val current: Double, val goal: Double, val color: Int, val group: String)

/**
 * Google Calendar through its private iCal link: no Google login on the TV.
 * Handles one off events and weekly or daily repeats (how class schedules are stored), skipped days, and moved classes.
 */
object CalendarFeed {
    private val zone: ZoneId = ZoneId.of("America/Chicago")

    fun upcoming(ctx: Context, feeds: Feeds, days: Long = 14): List<CalEvent> {
        val url = Secrets.get(ctx, Secrets.CALENDAR_ICS).ifBlank { return emptyList() }
        val body = feeds.get(url, 30) ?: return emptyList()
        val now = ZonedDateTime.now(zone)
        val from = now.minusHours(12); val to = now.plusDays(days)
        val lines = unfold(body)

        data class Raw(val p: Map<String, Pair<String, String>>, val exdates: List<Pair<String, String>>)
        val raws = mutableListOf<Raw>()
        var cur: MutableMap<String, Pair<String, String>>? = null
        var ex = mutableListOf<Pair<String, String>>()
        for (l in lines) {
            when {
                l == "BEGIN:VEVENT" -> { cur = mutableMapOf(); ex = mutableListOf() }
                l == "END:VEVENT" -> { cur?.let { raws += Raw(it, ex) }; cur = null }
                cur != null -> {
                    val colon = l.indexOf(':'); if (colon < 0) continue
                    val head = l.substring(0, colon); val value = l.substring(colon + 1)
                    val name = head.substringBefore(';'); val params = head.substringAfter(';', "")
                    if (name == "EXDATE") value.split(',').forEach { ex += params to it } else cur!![name] = params to value
                }
            }
        }

        // Moved or edited single occurrences: RECURRENCE-ID marks which original date they replace
        val overridden = mutableSetOf<String>()
        raws.forEach { r -> r.p["RECURRENCE-ID"]?.let { (pa, v) -> parseDt(pa, v)?.let { overridden += r.p["UID"]?.second + "@" + it.toEpochSecond() } } }

        val out = mutableListOf<CalEvent>()
        for (r in raws) {
            if (r.p["STATUS"]?.second == "CANCELLED") continue
            val (sp, sv) = r.p["DTSTART"] ?: continue
            val start = parseDt(sp, sv) ?: continue
            val end = r.p["DTEND"]?.let { (ep, ev) -> parseDt(ep, ev) } ?: start.plusHours(1)
            val len = java.time.Duration.between(start, end)
            val title = unescape(r.p["SUMMARY"]?.second ?: "Event")
            val loc = unescape(r.p["LOCATION"]?.second ?: "")
            val uid = r.p["UID"]?.second ?: ""
            val exSet = r.exdates.mapNotNull { (pa, v) -> parseDt(pa, v)?.toEpochSecond() }.toSet()
            val rule = r.p["RRULE"]?.second
            val starts = if (rule == null || r.p.containsKey("RECURRENCE-ID")) listOf(start) else expand(start, rule, to)
            for (s in starts) {
                if (s.toEpochSecond() in exSet) continue
                if (rule != null && "$uid@${s.toEpochSecond()}" in overridden) continue
                val e = s.plus(len)
                if (e.isAfter(from) && s.isBefore(to)) out += CalEvent(title, loc, s, e)
            }
        }
        return out.sortedBy { it.start.toEpochSecond() }
    }

    private fun expand(start: ZonedDateTime, rule: String, to: ZonedDateTime): List<ZonedDateTime> {
        val r = rule.split(';').associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val interval = r["INTERVAL"]?.toIntOrNull() ?: 1
        val count = r["COUNT"]?.toIntOrNull()
        val until = r["UNTIL"]?.let { parseDt(if (it.endsWith("Z")) "" else "TZID=America/Chicago", it) }
        val limit = if (until != null && until.isBefore(to)) until else to
        val out = mutableListOf<ZonedDateTime>()
        when (r["FREQ"]) {
            "DAILY" -> { var d = start; while (!d.isAfter(limit) && (count == null || out.size < count)) { out += d; d = d.plusDays(interval.toLong()) } }
            "WEEKLY" -> {
                val days = r["BYDAY"]?.split(',')?.mapNotNull { dow(it.takeLast(2)) }?.ifEmpty { null } ?: listOf(start.dayOfWeek)
                var weekStart = start.with(DayOfWeek.MONDAY).toLocalDate()
                var n = 0
                loop@ while (true) {
                    for (dw in days.sortedBy { it.value }) {
                        val d = ZonedDateTime.of(weekStart.plusDays((dw.value - 1).toLong()), start.toLocalTime(), start.zone)
                        if (d.isBefore(start)) continue
                        if (d.isAfter(limit)) break@loop
                        out += d; n++
                        if (count != null && n >= count) break@loop
                    }
                    weekStart = weekStart.plusWeeks(interval.toLong())
                    if (weekStart.isAfter(limit.toLocalDate().plusDays(7))) break
                }
            }
            else -> out += start
        }
        return out
    }

    private fun dow(s: String) = when (s) {
        "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY; "WE" -> DayOfWeek.WEDNESDAY; "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY; "SA" -> DayOfWeek.SATURDAY; "SU" -> DayOfWeek.SUNDAY; else -> null
    }

    /** DTSTART;TZID=America/Chicago:20260825T091000 or DTSTART:20260825T141000Z or DTSTART;VALUE=DATE:20260825 */
    private fun parseDt(params: String, v: String): ZonedDateTime? = runCatching {
        val tz = params.split(';').firstOrNull { it.startsWith("TZID=") }?.substringAfter('=')?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
        when {
            v.length == 8 -> LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(tz)
            v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).atZone(ZoneId.of("UTC")).withZoneSameInstant(zone)
            else -> LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).atZone(tz)
        }.withZoneSameInstant(zone)
    }.getOrNull()

    private fun unfold(s: String): List<String> {
        val out = mutableListOf<String>()
        s.replace("\r\n", "\n").split('\n').forEach { l ->
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) out[out.size - 1] = out.last() + l.substring(1) else out += l
        }
        return out
    }

    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\,", ",").replace("\;", ";").replace("\\\\", "\\")
}

/** ATR Global rings, read live from the Ring Inputs table in Notion. */
object NotionFeed {
    fun rings(ctx: Context): List<Ring> {
        val token = Secrets.get(ctx, Secrets.NOTION_TOKEN).ifBlank { return emptyList() }
        val cache = File(ctx.cacheDir, "notion_rings.json")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 10 * 60_000
        val body = if (fresh) cache.readText() else (query(ctx, token)?.also { cache.writeText(it) } ?: if (cache.exists()) cache.readText() else return emptyList())
        val results = runCatching { JSONObject(body).getJSONArray("results") }.getOrNull() ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val props = results.getJSONObject(i).optJSONObject("properties") ?: return@mapNotNull null
            val metric = text(props.optJSONObject("Metric"), "title")
            if (metric.isBlank()) return@mapNotNull null
            val color = runCatching { android.graphics.Color.parseColor(text(props.optJSONObject("Color"), "rich_text")) }.getOrDefault(android.graphics.Color.parseColor("#1E6BFF"))
            Triple(props.optJSONObject("Order")?.optDouble("number", 99.0) ?: 99.0, props.optJSONObject("Group")?.optJSONObject("select")?.optString("name") ?: "",
                Ring(metric, num(props, "Current"), num(props, "Goal"), color, props.optJSONObject("Group")?.optJSONObject("select")?.optString("name") ?: ""))
        }.sortedWith(compareBy({ groupOrder(it.second) }, { it.first })).map { it.third }
    }

    private fun groupOrder(g: String) = when (g) { "Academics" -> 0; "Career" -> 1; "Ventures" -> 2; else -> 3 }
    private fun num(p: JSONObject, k: String) = p.optJSONObject(k)?.let { if (it.isNull("number")) 0.0 else it.optDouble("number", 0.0) } ?: 0.0
    private fun text(o: JSONObject?, key: String): String {
        val a: JSONArray = o?.optJSONArray(key) ?: return ""
        return (0 until a.length()).joinToString("") { a.getJSONObject(it).optString("plain_text") }
    }

    /** Tries the current Notion API (data sources) first, then the older database endpoint. */
    private fun query(ctx: Context, token: String): String? {
        val ds = Secrets.get(ctx, Secrets.NOTION_DATA_SOURCE)
        val db = Secrets.get(ctx, Secrets.NOTION_DATABASE)
        val body = """{"page_size":50}"""
        if (ds.isNotBlank()) post("https://api.notion.com/v1/data_sources/$ds/query", token, "2025-09-03", body)?.let { return it }
        if (db.isNotBlank()) post("https://api.notion.com/v1/databases/$db/query", token, "2022-06-28", body)?.let { return it }
        return null
    }

    private fun post(url: String, token: String, version: String, body: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 10_000
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Notion-Version", version)
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toByteArray()) }
        if (c.responseCode !in 200..299) { c.disconnect(); return@runCatching null }
        c.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
