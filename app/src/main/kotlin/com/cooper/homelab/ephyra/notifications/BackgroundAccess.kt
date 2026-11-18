package com.cooper.homelab.ephyra.notifications

import android.content.Context
import android.os.PowerManager

/** Whether Android holds this app's background work back to save battery, which is what delays the poll. Seamed for tests. */
interface BackgroundAccess {
    /** True when the user has set this app to Unrestricted, so Doze and app standby no longer hold its checks back. */
    fun isUnrestricted(): Boolean
}

/** What the system says about this app's battery restriction, read from [PowerManager] on each call. */
class AndroidBackgroundAccess(
    private val context: Context,
) : BackgroundAccess {
    override fun isUnrestricted(): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }
}
