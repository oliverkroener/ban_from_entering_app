package com.banfromentering.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.banfromentering.app.tracking.Notifier
import com.banfromentering.app.tracking.Permissions
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: BanViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val tracking by viewModel.settings.trackingEnabled.collectAsStateWithLifecycle()
    val warningDistance by viewModel.settings.warningDistanceMeters.collectAsStateWithLifecycle()
    var sliderValue by remember { mutableFloatStateOf(warningDistance.toFloat()) }
    LaunchedEffect(warningDistance) { sliderValue = warningDistance.toFloat() }

    // Re-read permission state whenever the user comes back from system settings.
    var permissionRefresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        permissionRefresh++
        onPauseOrDispose { }
    }
    val hasBackground = remember(permissionRefresh) { Permissions.hasBackgroundLocation(context) }
    val hasNotifications = remember(permissionRefresh) { Permissions.hasNotifications(context) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionRefresh++
        val locationGranted = Permissions.foregroundLocation.any { result[it] == true } ||
            Permissions.hasForegroundLocation(context)
        if (locationGranted) viewModel.setTrackingEnabled(true)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionRefresh++ }

    fun enableTracking() {
        val needed = buildList {
            if (!Permissions.hasForegroundLocation(context)) addAll(Permissions.foregroundLocation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Permissions.hasNotifications(context)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isEmpty()) viewModel.setTrackingEnabled(true) else foregroundLauncher.launch(needed.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GPS proximity warnings", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Runs in the background with a permanent notification.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = tracking,
                    onCheckedChange = { if (it) enableTracking() else viewModel.setTrackingEnabled(false) },
                )
            }

            HorizontalDivider()

            Text("Warn me within ${Notifier.formatDistance(sliderValue.toDouble())} of a banned area")
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = (it / 50).roundToInt() * 50f },
                onValueChangeFinished = { viewModel.settings.setWarningDistanceMeters(sliderValue.toInt()) },
                valueRange = 50f..5_000f,
            )

            HorizontalDivider()

            if (!hasNotifications) {
                Text(
                    "Notifications are blocked — you won't see warnings.",
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = { context.startActivity(appSettingsIntent(context.packageName)) }) {
                    Text("Open app settings")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackground) {
                Text(
                    "Optional: allow location \"all the time\" so warnings resume automatically after your phone restarts.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                    enabled = Permissions.hasForegroundLocation(context),
                ) { Text("Allow background location") }
            }

            Text(
                "Tip: exclude this app from battery optimisation so the system doesn't stop monitoring.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                Text("Battery optimisation settings")
            }
        }
    }
}

private fun appSettingsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
