package com.atr.launcher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager

data class InputEntry(val id: String, val label: String)

/** HDMI ports, so the Switch or a console is one click from home. */
object Inputs {
    fun load(ctx: Context): List<InputEntry> {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        return runCatching {
            tim.tvInputList.filter { it.isPassthroughInput }.map {
                val custom = it.loadCustomLabel(ctx)?.toString()?.takeIf { s -> s.isNotBlank() }
                InputEntry(it.id, custom ?: it.loadLabel(ctx).toString())
            }
        }.getOrDefault(emptyList())
    }

    fun open(ctx: Context, id: String) = runCatching {
        val uri = TvContract.buildChannelUriForPassthroughInput(id)
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
