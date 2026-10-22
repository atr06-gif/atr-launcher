package com.atr.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

data class AppEntry(val pkg: String, val label: String, val banner: Drawable?, val icon: Drawable?)

/** Lists every app a person can open on the TV, preferring the wide TV banner over the square icon. */
object AppRepo {
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, ResolveInfo>()
        fun query(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        query(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach { found[it.activityInfo.packageName] = it }
        query(Intent.CATEGORY_LAUNCHER).forEach { found.putIfAbsent(it.activityInfo.packageName, it) }
        found.remove(ctx.packageName)
        return found.values.map { ri ->
            val ai = ri.activityInfo
            AppEntry(
                ai.packageName,
                ri.loadLabel(pm).toString(),
                runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull(),
                runCatching { ri.loadIcon(pm) }.getOrNull()
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { ctx.startActivity(i); true }.getOrDefault(false)
    }

    /** Opens [link] inside [pkg] when given (deep link), otherwise just launches the app. */
    fun open(ctx: Context, pkg: String, link: String?): Boolean {
        if (!link.isNullOrBlank()) {
            val ok = runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            }.getOrDefault(false)
            if (ok) return true
        }
        return launch(ctx, pkg)
    }

    fun isInstalled(ctx: Context, pkg: String) =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    fun openAppInfo(ctx: Context, pkg: String) = runCatching {
        ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Suppress("unused")
    private fun PackageManager.safeLabel(pkg: String) = runCatching { getApplicationLabel(getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
}
