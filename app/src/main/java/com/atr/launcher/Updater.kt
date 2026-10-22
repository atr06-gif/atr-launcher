package com.atr.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Over the air updates. The build server publishes version.json next to the APK:
 *   { "versionCode": 7, "versionName": "1.3.0", "apk": "https://.../atr-launcher.apk", "notes": "..." }
 * The launcher checks it every few hours and shows an "Update ready" pill. One click installs it.
 */
object Updater {
    data class Info(val code: Int, val name: String, val apk: String, val notes: String)
    val NONE = Info(0, "", "", "")

    fun check(): Info? {
        val url = Config.updateUrl.ifBlank { return null }
        val o = runCatching { JSONObject(fetch(url)) }.getOrNull() ?: return null
        val info = Info(o.optInt("versionCode"), o.optString("versionName"), o.optString("apk"), o.optString("notes"))
        return if (info.code > BuildConfig.VERSION_CODE && info.apk.startsWith("https://")) info else null
    }

    fun download(ctx: Context, info: Info): File? = runCatching {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "atr-launcher.apk")
        val c = open(info.apk, readTimeout = 60_000)
        c.inputStream.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        c.disconnect()
        out
    }.getOrNull()

    fun install(ctx: Context, apk: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(ctx, "Allow ATR Launcher to install apps, then press Update again", Toast.LENGTH_LONG).show()
            runCatching {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        val uri = Uri.parse("content://com.atr.launcher.apk/${apk.name}")
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun fetch(url: String): String {
        val c = open(url, readTimeout = 10_000)
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    /** Opens a connection and follows redirects by hand (GitHub release links jump to a storage host). */
    private fun open(url: String, readTimeout: Int): HttpURLConnection {
        var u = URL(url)
        repeat(6) {
            val c = u.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000
            c.readTimeout = readTimeout
            c.setRequestProperty("Cache-Control", "no-cache")
            c.setRequestProperty("User-Agent", "ATR-Launcher/${BuildConfig.VERSION_NAME}")
            val code = c.responseCode
            if (code in 300..399) { u = URL(u, c.getHeaderField("Location")); c.disconnect(); return@repeat }
            if (code !in 200..299) { c.disconnect(); throw Exception("HTTP $code for $u") }
            return c
        }
        throw Exception("Too many redirects")
    }
}

/** Hands the downloaded APK to the system installer without exposing any other file. */
class ApkProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/vnd.android.package-archive"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw java.io.FileNotFoundException()
        if (name.contains("/") || !name.endsWith(".apk")) throw SecurityException("bad path")
        val f = File(File(context!!.cacheDir, "updates"), name)
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/** Pulls config.json from the internet. A higher "version" number replaces the TV's copy. */
object RemoteConfig {
    fun pull(@Suppress("UNUSED_PARAMETER") ctx: Context): Boolean {
        val url = Config.configUrl.ifBlank { return false }
        val remote = runCatching { JSONObject(Updater.fetch(url)) }.getOrNull() ?: return false
        if (remote.optInt("version", 0) <= Config.version) return false
        // Keep the URLs the TV is already using unless the remote file sets new ones
        if (!remote.has("configUrl")) remote.put("configUrl", Config.json.optString("configUrl"))
        if (!remote.has("updateUrl")) remote.put("updateUrl", Config.json.optString("updateUrl"))
        Config.replaceWith(remote)
        return true
    }
}
