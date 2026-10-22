package com.atr.launcher

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File

/**
 * Personal values that must never go in the public repo: Notion key and IDs, calendar link.
 * The PC drops /sdcard/ATR/secrets.json on the TV; the app copies it into private storage and deletes the file.
 */
object Secrets {
    private const val PREFS = "atr_secrets"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun importIfPresent(ctx: Context): Boolean {
        @Suppress("DEPRECATION")
        val f = File(Environment.getExternalStorageDirectory(), "ATR/secrets.json")
        if (!f.exists()) return false
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return false
        val e = p(ctx).edit()
        o.keys().forEach { k -> val v = o.optString(k).trim(); if (v.isNotBlank()) e.putString(k, v) }
        e.apply()
        runCatching { f.delete() }
        return true
    }

    fun get(ctx: Context, key: String): String = p(ctx).getString(key, "") ?: ""
    fun has(ctx: Context, key: String) = get(ctx, key).isNotBlank()
    fun clear(ctx: Context) = p(ctx).edit().clear().apply()

    const val NOTION_TOKEN = "notionToken"
    const val NOTION_DATA_SOURCE = "notionDataSourceId"
    const val NOTION_DATABASE = "notionDatabaseId"
    const val CALENDAR_ICS = "calendarIcalUrl"
}
