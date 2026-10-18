package com.banfromentering.app.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.banfromentering.app.BanApp

/** Resumes tracking after a reboot or app update if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val app = context.applicationContext as BanApp
        // A location foreground service may only start from the background with "Allow all the time".
        if (app.settings.trackingEnabled.value && Permissions.hasBackgroundLocation(context)) {
            LocationTrackingService.start(context)
        }
    }
}
