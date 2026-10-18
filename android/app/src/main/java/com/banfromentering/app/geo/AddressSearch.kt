package com.banfromentering.app.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class AddressResult(val label: String, val latitude: Double, val longitude: Double)

/** Worldwide address lookup via the platform geocoder (no API key needed). */
object AddressSearch {

    suspend fun search(context: Context, query: String, maxResults: Int = 5): List<AddressResult> {
        if (query.isBlank() || !Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context)
        val addresses: List<Address> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, maxResults, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                        override fun onError(errorMessage: String?) = cont.resume(emptyList())
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, maxResults).orEmpty()
                }
            }
        } catch (e: java.io.IOException) {
            emptyList()
        }
        return addresses.map { address ->
            val label = (0..address.maxAddressLineIndex).joinToString(", ") { address.getAddressLine(it) }
            AddressResult(label.ifBlank { query }, address.latitude, address.longitude)
        }
    }
}
