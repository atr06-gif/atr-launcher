package com.atr.launcher

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Colors for the whole UI. Every value comes from config.json so the look can change without an app update. */
data class Theme(
    val page: Int, val pageLow: Int, val ink: Int, val inkMuted: Int,
    val card: Int, val cardText: Int, val cardMuted: Int, val accent: Int, val logo: Int
) {
    companion object {
        private fun c(o: JSONObject?, k: String, d: String) = try { Color.parseColor(o?.optString(k, d) ?: d) } catch (e: Exception) { Color.parseColor(d) }
        fun from(o: JSONObject?) = Theme(
            c(o, "page", "#F4F6FA"), c(o, "pageLow", "#E3EAF6"), c(o, "ink", "#05070B"), c(o, "inkMuted", "#4B5568"),
            c(o, "card", "#05070B"), c(o, "cardText", "#FFFFFF"), c(o, "cardMuted", "#9AA6BD"), c(o, "accent", "#1E6BFF"),
            c(o, "logo", "#05070B")
        )
    }
}

data class RowSpec(val title: String, val type: String)
data class ExtraEvent(val name: String, val start: String)

/**
 * The launcher's whole setup lives in one JSON file:
 *  - shipped default: assets/default_config.json
 *  - local copy (with edits made on the TV): files/config.json
 *  - optional remote copy (configUrl): replaces the local one whenever its "version" goes up
 */
object Config {
    private lateinit var file: File
    lateinit var json: JSONObject; private set

    fun load(ctx: Context) {
        file = File(ctx.filesDir, "config.json")
        json = runCatching { JSONObject(file.readText()) }.getOrNull()
            ?: JSONObject(ctx.assets.open("default_config.json").bufferedReader().readText())
        // Fill in keys added by newer app versions without wiping local edits
        val def = JSONObject(ctx.assets.open("default_config.json").bufferedReader().readText())
        def.keys().forEach { k -> if (!json.has(k)) json.put(k, def.get(k)) }
    }

    fun save() { runCatching { file.writeText(json.toString(2)) } }

    fun replaceWith(newJson: JSONObject) { json = newJson; save() }

    val version get() = json.optInt("version", 1)
    val theme get() = Theme.from(json.optJSONObject("theme"))
    val clock24h get() = json.optBoolean("clock24h", false)
    val cards: List<String> get() = json.optJSONArray("cards").strings()
    fun cardAction(card: String): String? = json.optJSONObject("cardActions")?.optString(card)?.takeIf { it.isNotBlank() }
    val weatherLabel get() = json.optJSONObject("weather")?.optString("label") ?: "Weather"
    val lat get() = json.optJSONObject("weather")?.optDouble("lat", 30.628) ?: 30.628
    val lon get() = json.optJSONObject("weather")?.optDouble("lon", -96.3344) ?: -96.3344
    val extraEvents: List<ExtraEvent> get() = json.optJSONArray("extraEvents")?.let { a ->
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { ExtraEvent(it.optString("name"), it.optString("start")) }
    } ?: emptyList()
    val rows: List<RowSpec> get() = json.optJSONArray("rows")?.let { a ->
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { RowSpec(it.optString("title"), it.optString("type")) }
    } ?: emptyList()
    val configUrl get() = json.optString("configUrl").ifBlank { BuildConfig.CONFIG_URL }
    val updateUrl get() = json.optString("updateUrl").ifBlank { BuildConfig.UPDATE_URL }

    var pinned: MutableList<String>
        get() = json.optJSONArray("pinned").strings().toMutableList()
        set(v) { json.put("pinned", JSONArray(v)); save() }
    var hidden: MutableList<String>
        get() = json.optJSONArray("hidden").strings().toMutableList()
        set(v) { json.put("hidden", JSONArray(v)); save() }

    fun pin(pkg: String) { pinned = pinned.apply { remove(pkg); add(pkg) } }
    fun unpin(pkg: String) { pinned = pinned.apply { remove(pkg) } }
    fun hide(pkg: String) { hidden = hidden.apply { if (!contains(pkg)) add(pkg) }; unpin(pkg) }
    fun unhide(pkg: String) { hidden = hidden.apply { remove(pkg) } }
    fun movePinned(pkg: String, delta: Int) {
        val p = pinned; val i = p.indexOf(pkg); if (i < 0) return
        val j = (i + delta).coerceIn(0, p.size - 1); p.removeAt(i); p.add(j, pkg); pinned = p
    }

    private fun JSONArray?.strings(): List<String> = this?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
}
