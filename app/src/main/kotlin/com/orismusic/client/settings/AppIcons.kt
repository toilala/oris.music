package com.orismusic.client.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.orismusic.client.R

/** Launcher icon variants. Each one is an activity-alias in the manifest; exactly one is enabled. */
object AppIcons {
    data class Variant(val id: String, val label: String, val alias: String, val preview: Int)

    val variants = listOf(
        Variant("turntable", "Turntable", "Turntable", R.drawable.ic_v_turntable_fg),
        Variant("paper", "Paper", "Paper", R.drawable.ic_v_paper_fg),
        Variant("spiral", "Spiral", "Spiral", R.drawable.ic_v_spiral_fg),
        Variant("eclipse", "Eclipse", "Eclipse", R.drawable.ic_v_eclipse_fg),
        Variant("ink", "Ink (hand drawn)", "Ink", R.drawable.ic_v_ink_fg),
        Variant("chalk", "Chalk (hand drawn)", "Chalk", R.drawable.ic_v_chalk_fg),
        Variant("dusk", "Dusk", "Dusk", R.drawable.ic_v_dusk_fg),
        Variant("sage", "Sage", "Sage", R.drawable.ic_v_sage_fg),
    )

    private fun component(context: Context, v: Variant) =
        ComponentName(context.packageName, "com.orismusic.client.icon." + v.alias)

    fun current(context: Context): Variant {
        val pm = context.packageManager
        return variants.firstOrNull { pm.getComponentEnabledSetting(component(context, it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
            ?: variants.first()
    }

    /** Enables the chosen alias first, then disables the rest, so the app never loses its launcher entry. */
    fun select(context: Context, target: Variant) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component(context, target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        variants.filter { it != target }.forEach {
            pm.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
