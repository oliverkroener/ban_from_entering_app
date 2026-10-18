package com.banfromentering.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banfromentering.app.data.Ban
import com.banfromentering.app.tracking.Notifier
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanListScreen(
    viewModel: BanViewModel,
    onAdd: () -> Unit,
    onOpen: (Ban) -> Unit,
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val bans by viewModel.bans.collectAsStateWithLifecycle()
    val tracking by viewModel.settings.trackingEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Banned places") },
                actions = {
                    IconButton(onClick = onOpenMap) { Icon(Icons.Filled.LocationOn, contentDescription = "Map") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add ban") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!tracking) {
                item { TrackingOffBanner(onOpenSettings) }
            }
            if (bans.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text("No bans yet. Tap + to add a place you may not enter.")
                    }
                }
            }
            items(bans, key = { it.id }) { ban -> BanCard(ban, onClick = { onOpen(ban) }) }
        }
    }
}

@Composable
private fun TrackingOffBanner(onOpenSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(
                "GPS warnings are off.",
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            TextButton(onClick = onOpenSettings) { Text("Turn on") }
        }
    }
}

@Composable
private fun BanCard(ban: Ban, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(ban.name, style = MaterialTheme.typography.titleMedium)
            if (ban.address.isNotBlank()) {
                Text(ban.address, style = MaterialTheme.typography.bodyMedium)
            }
            val status = when {
                !ban.active -> "Inactive"
                !ban.isEffective(now) -> "Expired"
                ban.validUntilEpochMs != null ->
                    "Until " + DateFormat.getDateInstance().format(Date(ban.validUntilEpochMs))
                else -> "Permanent"
            }
            Text(
                "Radius ${Notifier.formatDistance(ban.radiusMeters)} · $status",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
