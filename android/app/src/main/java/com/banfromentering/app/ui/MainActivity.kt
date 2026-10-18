package com.banfromentering.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.banfromentering.app.BanApp
import com.banfromentering.app.tracking.LocationTrackingService
import com.banfromentering.app.tracking.Permissions
import com.banfromentering.app.ui.theme.BanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Make sure monitoring is running if the user switched it on earlier (e.g. after a force stop).
        val app = application as BanApp
        if (app.settings.trackingEnabled.value && Permissions.hasForegroundLocation(this)) {
            LocationTrackingService.start(this)
        }

        setContent {
            BanTheme {
                AppNavigation()
            }
        }
    }
}
