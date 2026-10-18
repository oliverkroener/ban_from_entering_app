package com.banfromentering.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banfromentering.app.data.Ban
import com.banfromentering.app.tracking.Permissions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: BanViewModel,
    onBack: () -> Unit,
    onOpen: (Ban) -> Unit,
    onAddAt: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val bans by viewModel.bans.collectAsStateWithLifecycle()
    val warningDistance by viewModel.settings.warningDistanceMeters.collectAsStateWithLifecycle()
    val cameraState = rememberCameraPositionState {
        position = bans.firstOrNull()
            ?.let { CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 12f) }
            ?: CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 1f)
    }
    val now = System.currentTimeMillis()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map · long-press to add") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        GoogleMap(
            modifier = Modifier.fillMaxSize().padding(padding),
            cameraPositionState = cameraState,
            properties = MapProperties(isMyLocationEnabled = Permissions.hasForegroundLocation(context)),
            onMapLongClick = { onAddAt(it.latitude, it.longitude) },
        ) {
            bans.forEach { ban -> key(ban.id) {
                val center = LatLng(ban.latitude, ban.longitude)
                val effective = ban.isEffective(now)
                BanCircle(
                    center = center,
                    radiusMeters = ban.radiusMeters,
                    warningDistanceMeters = if (effective) warningDistance.toDouble() else null,
                    dimmed = !effective,
                )
                Marker(
                    state = rememberUpdatedMarkerState(position = center),
                    title = ban.name,
                    snippet = ban.address.ifBlank { null },
                    onInfoWindowClick = { onOpen(ban) },
                )
            } }
        }
    }
}
