package com.banfromentering.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.banfromentering.app.BanApp
import com.banfromentering.app.data.Ban
import com.banfromentering.app.tracking.LocationTrackingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BanViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BanApp
    private val dao = app.database.banDao()
    val settings = app.settings

    val bans: StateFlow<List<Ban>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun get(id: Long): Ban? = dao.get(id)

    fun save(ban: Ban) {
        viewModelScope.launch { dao.upsert(ban) }
    }

    fun delete(ban: Ban) {
        viewModelScope.launch { dao.delete(ban) }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        settings.setTrackingEnabled(enabled)
        if (enabled) LocationTrackingService.start(app) else LocationTrackingService.stop(app)
    }
}
