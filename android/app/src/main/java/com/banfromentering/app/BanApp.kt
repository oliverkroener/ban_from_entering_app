package com.banfromentering.app

import android.app.Application
import com.banfromentering.app.data.AppDatabase
import com.banfromentering.app.data.SettingsRepository
import com.banfromentering.app.tracking.Notifier

class BanApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}
