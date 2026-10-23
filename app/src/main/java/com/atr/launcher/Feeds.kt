package com.atr.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class RaceItem(val series: String, val event: String, val session: String, val start: ZonedDateTime)
data class Weather(val nowF: Int, val hiF: Int, val loF: Int, val rainPct: Int, val code: Int)
data class Hour(val time: ZonedDateTime, val tempF: Int, val rainPct: Int, val code: Int)
data class Game(val opponent: String, val home: Boolean, val start: ZonedDateTime, val tv: String)

/** Live data for the info cards. Each source keeps its last good copy, so the cards still show something offline. */
class Feeds(ctx: Context) {
    val zone: ZoneId = ZoneId.of("America/Chicago")
    private val cacheDir = File(ctx.cacheDir, "http").apply { mkdirs() }

    fun get(url: String, ttlMin: Long): String? {
        val f = File(cacheDir, Integer.toHexString(url.hashCode()))
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < ttlMin * 60_000) return f.readText()
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "ATR-Launcher/${BuildConfig.VERSION_NAME}")
            if (c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}")
            val body = c.inputStream.bufferedReader().readText()
            f.writeText(body); body
        } catch (e: Exception) {
            if (f.exists()) f.readText() else null
        }
    }

    // ---------- F1 (Jolpica / Ergast) plus custom events like IMSA ----------
    fun races(limit: Int = 3): List<RaceItem> {
        val now = ZonedDateTime.now(zone)
        val out = mutableListOf<RaceItem>()
        for (season in listOf("current", (LocalDate.now(zone).year + 1).toString())) {
            val body = get("https://api.jolpi.ca/ergast/f1/$season/races.json?limit=40", 6 * 60) ?: continue
            val races = runCatching { JSONObject(body).getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races") }.getOrNull() ?: continue
            for (i in 0 until races.length()) {
                val r = races.getJSONObject(i)
                val name = r.optString("raceName").replace(" Grand Prix", " GP")
                val sessions = listOf(
                    "FirstPractice" to "FP1", "SecondPractice" to "FP2", "ThirdPractice" to "FP3",
                    "SprintQualifying" to "Sprint Quali", "SprintShootout" to "Sprint Quali",
                    "Sprint" to "Sprint", "Qualifying" to "Qualifying"
                ).mapNotNull { (k, label) -> r.optJSONObject(k)?.let { s -> parse(s.optString("date"), s.optString("time"))?.let { label to it } } } +
                    listOfNotNull(parse(r.optString("date"), r.optString("time"))?.let { "Race" to it })
                sessions.forEach { (label, t) -> if (t.isAfter(now.minusHours(2))) out += RaceItem("F1", name, label, t) }
            }
            if (out.isNotEmpty()) break
        }
        Config.extraEvents.forEach { e ->
            runCatching { OffsetDateTime.parse(e.start).atZoneSameInstant(zone) }.getOrNull()?.let { t ->
                if (t.isAfter(now.minusHours(2))) out += RaceItem(if (e.name.contains("IMSA", true)) "IMSA" else "", e.name, "", t)
            }
        }
        return out.sortedBy { it.start.toEpochSecond() }.take(limit)
    }

    /** Every session of the next F1 weekend (FP1 to Race), for the Racing hub row. */
    fun weekend(): List<RaceItem> {
        val next = races(1).firstOrNull { it.series == "F1" } ?: return emptyList()
        val body = get("https://api.jolpi.ca/ergast/f1/current/races.json?limit=40", 6 * 60) ?: return emptyList()
        val races = runCatching { JSONObject(body).getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races") }.getOrNull() ?: return emptyList()
        for (i in 0 until races.length()) {
            val r = races.getJSONObject(i)
            if (r.optString("raceName").replace(" Grand Prix", " GP") != next.event) continue
            val keys = listOf("FirstPractice" to "FP1", "SecondPractice" to "FP2", "ThirdPractice" to "FP3",
                "SprintQualifying" to "Sprint Quali", "SprintShootout" to "Sprint Quali", "Sprint" to "Sprint", "Qualifying" to "Quali")
            val list = keys.mapNotNull { (k, label) -> r.optJSONObject(k)?.let { s -> parse(s.optString("date"), s.optString("time"))?.let { RaceItem("F1", next.event, label, it) } } } +
                listOfNotNull(parse(r.optString("date"), r.optString("time"))?.let { RaceItem("F1", next.event, "Race", it) })
            return list.sortedBy { it.start.toEpochSecond() }
        }
        return emptyList()
    }

    data class Standing(val pos: Int, val code: String, val name: String, val team: String, val points: String, val teamId: String = "")
    data class TeamStanding(val pos: Int, val name: String, val points: String, val teamId: String)

    fun driverStandings(top: Int = 5): List<Standing> {
        val body = get("https://api.jolpi.ca/ergast/f1/current/driverStandings.json", 6 * 60) ?: return emptyList()
        val list = runCatching {
            JSONObject(body).getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists").getJSONObject(0).getJSONArray("DriverStandings")
        }.getOrNull() ?: return emptyList()
        return (0 until minOf(top, list.length())).map { i ->
            val d = list.getJSONObject(i); val drv = d.getJSONObject("Driver")
            val con = d.optJSONArray("Constructors")?.optJSONObject(0)
            Standing(d.optInt("position", i + 1), drv.optString("code", drv.optString("familyName").take(3).uppercase()),
                drv.optString("familyName"), con?.optString("name") ?: "", d.optString("points"), con?.optString("constructorId") ?: "")
        }
    }

    fun constructorStandings(top: Int = 5): List<TeamStanding> {
        val body = get("https://api.jolpi.ca/ergast/f1/current/constructorStandings.json", 6 * 60) ?: return emptyList()
        val list = runCatching {
            JSONObject(body).getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists").getJSONObject(0).getJSONArray("ConstructorStandings")
        }.getOrNull() ?: return emptyList()
        return (0 until minOf(top, list.length())).map { i ->
            val d = list.getJSONObject(i); val c = d.getJSONObject("Constructor")
            TeamStanding(d.optInt("position", i + 1), c.optString("name"), d.optString("points"), c.optString("constructorId"))
        }
    }

    /** A&M games in the next [days] days, for the week ahead strip. */
    fun aggieGames(days: Long = 8): List<Game> {
        val body = get("https://site.api.espn.com/apis/site/v2/sports/football/college-football/teams/245/schedule", 3 * 60) ?: return emptyList()
        val ev = runCatching { JSONObject(body).getJSONArray("events") }.getOrNull() ?: return emptyList()
        val now = ZonedDateTime.now(zone); val end = now.plusDays(days)
        return (0 until ev.length()).mapNotNull { i ->
            val e = ev.getJSONObject(i)
            val start = parseEspn(e.optString("date")) ?: return@mapNotNull null
            if (start.isBefore(now.minusHours(4)) || start.isAfter(end)) return@mapNotNull null
            val cs = e.optJSONArray("competitions")?.optJSONObject(0)?.optJSONArray("competitors") ?: return@mapNotNull null
            var home = true; var opp = "TBA"
            for (j in 0 until cs.length()) { val c = cs.getJSONObject(j); val t = c.optJSONObject("team")
                if (t?.optString("id") == "245") home = c.optString("homeAway") == "home" else opp = t?.optString("shortDisplayName")?.ifBlank { null } ?: t?.optString("displayName") ?: "TBA" }
            Game(opp, home, start, "")
        }
    }

    companion object Teams {
        /** Team colors by Ergast constructorId. Config "teamColors" can override any of them. */
        private val defaults = mapOf(
            "red_bull" to "#3671C6", "ferrari" to "#E8002D", "mercedes" to "#27F4D2", "mclaren" to "#FF8000",
            "aston_martin" to "#229971", "alpine" to "#0093CC", "williams" to "#64C4FF", "rb" to "#6692FF",
            "haas" to "#B6BABD", "sauber" to "#52E252", "audi" to "#BB0A30", "cadillac" to "#C9A86A"
        )
        fun describe(code: Int) = when (code) {
            0 -> "Clear"; 1, 2 -> "Partly cloudy"; 3 -> "Overcast"; 45, 48 -> "Fog"
            in 51..57 -> "Drizzle"; in 61..67, in 80..82 -> "Rain"; in 71..77, 85, 86 -> "Snow"; in 95..99 -> "Storms"; else -> ""
        }

        fun teamColor(id: String): Int {
            val hex = Config.teamColors[id] ?: defaults[id] ?: "#9AA6BD"
            return runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(android.graphics.Color.GRAY)
        }
    }

    private fun parse(date: String, time: String): ZonedDateTime? = runCatching {
        if (date.isBlank()) return null
        OffsetDateTime.parse("${date}T${time.ifBlank { "12:00:00Z" }}").atZoneSameInstant(zone)
    }.getOrNull()

    // ---------- Weather (Open-Meteo, no key) ----------
    fun weather(): Weather? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${Config.lat}&longitude=${Config.lon}" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&temperature_unit=fahrenheit&timezone=America%2FChicago&forecast_days=1"
        val o = runCatching { JSONObject(get(url, 20) ?: return null) }.getOrNull() ?: return null
        return runCatching {
            val c = o.getJSONObject("current"); val d = o.getJSONObject("daily")
            Weather(
                Math.round(c.getDouble("temperature_2m")).toInt(),
                Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
                Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                d.getJSONArray("precipitation_probability_max").optInt(0),
                c.getInt("weather_code")
            )
        }.getOrNull()
    }

    /** Today's sunrise and sunset in College Station, for auto dark mode. */
    fun sun(): Pair<ZonedDateTime, ZonedDateTime>? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${Config.lat}&longitude=${Config.lon}&daily=sunrise,sunset&timezone=America%2FChicago&forecast_days=1"
        val o = runCatching { JSONObject(get(url, 6 * 60) ?: return null) }.getOrNull() ?: return null
        return runCatching {
            val d = o.getJSONObject("daily")
            java.time.LocalDateTime.parse(d.getJSONArray("sunrise").getString(0)).atZone(zone) to
                java.time.LocalDateTime.parse(d.getJSONArray("sunset").getString(0)).atZone(zone)
        }.getOrNull()
    }

    /** Hour by hour for the weather widget: from this hour forward, [count] hours. */
    fun hourly(count: Int = 16): List<Hour> {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${Config.lat}&longitude=${Config.lon}" +
            "&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&temperature_unit=fahrenheit&timezone=America%2FChicago&forecast_days=2"
        val o = runCatching { JSONObject(get(url, 30) ?: return emptyList()) }.getOrNull() ?: return emptyList()
        return runCatching {
            val h = o.getJSONObject("hourly")
            val t = h.getJSONArray("time"); val temp = h.getJSONArray("temperature_2m")
            val rain = h.optJSONArray("precipitation_probability"); val code = h.optJSONArray("weather_code")
            val nowHour = ZonedDateTime.now(zone).withMinute(0).withSecond(0).withNano(0)
            (0 until t.length()).map { i ->
                Hour(java.time.LocalDateTime.parse(t.getString(i)).atZone(zone), Math.round(temp.getDouble(i)).toInt(),
                    rain?.optInt(i, 0) ?: 0, code?.optInt(i, 0) ?: 0)
            }.filter { !it.time.isBefore(nowHour) }.take(count)
        }.getOrDefault(emptyList())
    }

    // ---------- Texas A&M football (ESPN team 245) ----------
    fun aggies(): Pair<Game?, String?> {
        val body = get("https://site.api.espn.com/apis/site/v2/sports/football/college-football/teams/245/schedule", 3 * 60) ?: return null to null
        val ev = runCatching { JSONObject(body).getJSONArray("events") }.getOrNull() ?: return null to null
        val now = ZonedDateTime.now(zone)
        var next: Game? = null; var last: String? = null
        for (i in 0 until ev.length()) {
            val e = ev.getJSONObject(i)
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val start = parseEspn(e.optString("date")) ?: continue
            val done = comp.optJSONObject("status")?.optJSONObject("type")?.optBoolean("completed") ?: start.isBefore(now)
            val cs = comp.optJSONArray("competitors") ?: continue
            var us: JSONObject? = null; var them: JSONObject? = null
            for (j in 0 until cs.length()) { val c = cs.getJSONObject(j); if (c.optJSONObject("team")?.optString("id") == "245") us = c else them = c }
            if (us == null || them == null) continue
            val team = them.optJSONObject("team")
            val opp = team?.optString("shortDisplayName")?.ifBlank { null } ?: team?.optString("displayName") ?: "TBA"
            if (done) {
                val a = us.optJSONObject("score")?.optString("displayValue") ?: us.optString("score")
                val b = them.optJSONObject("score")?.optString("displayValue") ?: them.optString("score")
                last = "${if ((a.toIntOrNull() ?: 0) > (b.toIntOrNull() ?: 0)) "W" else "L"} $a to $b vs $opp"
            } else if (next == null) {
                val b0 = comp.optJSONArray("broadcasts")?.optJSONObject(0)
                val tv = b0?.optJSONObject("media")?.optString("shortName")?.takeIf { it.isNotBlank() }
                    ?: b0?.optJSONArray("names")?.optString(0) ?: ""
                next = Game(opp, us.optString("homeAway") == "home", start, tv)
            }
        }
        return next to last
    }

    private fun parseEspn(s: String): ZonedDateTime? =
        runCatching { ZonedDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(zone) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s.replace("Z", ":00Z")).atZoneSameInstant(zone) }.getOrNull()
}
