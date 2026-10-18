package com.banfromentering.app.ui

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.banfromentering.app.data.Ban
import com.banfromentering.app.geo.AddressResult
import com.banfromentering.app.geo.AddressSearch
import com.banfromentering.app.tracking.Notifier
import com.banfromentering.app.tracking.Permissions
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanEditScreen(
    viewModel: BanViewModel,
    banId: Long,
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var existing by remember { mutableStateOf<Ban?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf(initialLatitude) }
    var longitude by rememberSaveable { mutableStateOf(initialLongitude) }
    var radius by rememberSaveable { mutableStateOf(Ban.DEFAULT_RADIUS_METERS.toFloat()) }
    var validUntil by rememberSaveable { mutableStateOf<Long?>(null) }
    var active by rememberSaveable { mutableStateOf(true) }

    var searchResults by remember { mutableStateOf<List<AddressResult>>(emptyList()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val position = if (latitude != null && longitude != null) LatLng(latitude!!, longitude!!) else null
    val cameraState = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(position ?: LatLng(20.0, 0.0), if (position != null) 15f else 1f)
    }
    val markerState = remember { MarkerState() }

    fun moveTo(lat: Double, lng: Double, animate: Boolean = true) {
        latitude = lat
        longitude = lng
        if (animate) scope.launch { cameraState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f)) }
    }

    LaunchedEffect(position) { position?.let { markerState.position = it } }

    LaunchedEffect(banId) {
        if (banId == 0L) return@LaunchedEffect
        viewModel.get(banId)?.let { ban ->
            existing = ban
            name = ban.name
            address = ban.address
            notes = ban.notes
            radius = ban.radiusMeters.toFloat()
            validUntil = ban.validUntilEpochMs
            active = ban.active
            moveTo(ban.latitude, ban.longitude, animate = false)
            cameraState.position = CameraPosition.fromLatLngZoom(LatLng(ban.latitude, ban.longitude), 15f)
        }
    }

    @SuppressLint("MissingPermission") // only called once permission is granted
    fun useCurrentLocation() {
        scope.launch {
            val location = LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .await()
            location?.let { moveTo(it.latitude, it.longitude) }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.any { it }) useCurrentLocation() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (banId == 0L) "New ban" else "Edit ban") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    existing?.let { ban ->
                        IconButton(onClick = { viewModel.delete(ban); onDone() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (e.g. store, venue, person's home)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    scope.launch { searchResults = AddressSearch.search(context, address) }
                }),
                trailingIcon = {
                    IconButton(onClick = { scope.launch { searchResults = AddressSearch.search(context, address) } }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search address")
                    }
                },
            )
            searchResults.forEach { result ->
                ListItem(
                    headlineContent = { Text(result.label) },
                    modifier = Modifier.padding(0.dp),
                    trailingContent = {
                        TextButton(onClick = {
                            address = result.label
                            moveTo(result.latitude, result.longitude)
                            searchResults = emptyList()
                        }) { Text("Use") }
                    },
                )
                HorizontalDivider()
            }

            Text("Tap the map to set the center of the banned area.", style = MaterialTheme.typography.bodySmall)
            GoogleMap(
                modifier = Modifier.fillMaxWidth().height(280.dp),
                cameraPositionState = cameraState,
                properties = MapProperties(isMyLocationEnabled = Permissions.hasForegroundLocation(context)),
                uiSettings = MapUiSettings(zoomControlsEnabled = true),
                onMapClick = { moveTo(it.latitude, it.longitude, animate = false) },
            ) {
                if (position != null) {
                    Marker(state = markerState, title = name.ifBlank { null })
                    BanCircle(center = position, radiusMeters = radius.toDouble())
                }
            }
            OutlinedButton(
                onClick = {
                    if (Permissions.hasForegroundLocation(context)) useCurrentLocation()
                    else locationPermissionLauncher.launch(Permissions.foregroundLocation)
                },
            ) {
                Icon(Icons.Filled.Place, contentDescription = null)
                Text("Use my current location", Modifier.padding(start = 8.dp))
            }

            Text("Banned radius: ${Notifier.formatDistance(radius.toDouble())}")
            Slider(value = radius, onValueChange = { radius = it }, valueRange = 25f..5_000f)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    validUntil?.let { "Valid until " + DateFormat.getDateInstance().format(Date(it - 1)) } ?: "No end date",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showDatePicker = true }) { Text("Set end date") }
                if (validUntil != null) TextButton(onClick = { validUntil = null }) { Text("Clear") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active", modifier = Modifier.weight(1f))
                Switch(checked = active, onCheckedChange = { active = it })
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (case number, issuing authority, …)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Button(
                onClick = {
                    val lat = latitude ?: return@Button
                    val lng = longitude ?: return@Button
                    val base = existing ?: Ban(name = name, latitude = lat, longitude = lng)
                    viewModel.save(
                        base.copy(
                            name = name.trim(),
                            address = address.trim(),
                            latitude = lat,
                            longitude = lng,
                            radiusMeters = radius.toDouble(),
                            notes = notes.trim(),
                            validUntilEpochMs = validUntil,
                            active = active,
                        ),
                    )
                    onDone()
                },
                enabled = name.isNotBlank() && position != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = validUntil?.minus(1))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The ban is valid through the whole selected day.
                    validUntil = pickerState.selectedDateMillis?.plus(TimeUnit.DAYS.toMillis(1))
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}
