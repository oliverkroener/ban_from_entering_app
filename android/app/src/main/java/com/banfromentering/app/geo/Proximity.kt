package com.banfromentering.app.geo

import com.banfromentering.app.data.Ban
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Great-circle distance (haversine), accurate enough worldwide for proximity warnings. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }
}

/** Ordered by severity, so `zone > previous` means the situation got worse. */
enum class Zone { OUTSIDE, WARNING, INSIDE }

data class ProximityAlert(
    val ban: Ban,
    val zone: Zone,
    /** Distance to the edge of the banned area; 0 when inside. */
    val distanceToBoundaryMeters: Double,
)

/**
 * Tracks which zone the user is in for every ban and reports escalations only
 * (outside → warning, anything → inside), so the user isn't spammed on every GPS fix.
 *
 * - GPS uncertainty is subtracted from the distance (capped), so a poor fix errs on the side of warning.
 * - Hysteresis: leaving a zone requires moving [hysteresisMeters] beyond its edge,
 *   so jitter right at the boundary doesn't re-trigger alerts.
 */
class ProximityTracker(
    private val hysteresisMeters: Double = 50.0,
    private val maxAccuracySlackMeters: Double = 100.0,
) {
    private val zones = mutableMapOf<Long, Zone>()

    fun update(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?,
        bans: List<Ban>,
        warningDistanceMeters: Double,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<ProximityAlert> {
        val slack = min(accuracyMeters ?: 0.0, maxAccuracySlackMeters)
        val effective = bans.filter { it.isEffective(nowEpochMs) }
        zones.keys.retainAll(effective.map { it.id }.toSet())

        val alerts = mutableListOf<ProximityAlert>()
        for (ban in effective) {
            val distance = max(0.0, Geo.distanceMeters(latitude, longitude, ban.latitude, ban.longitude) - slack)
            val previous = zones[ban.id] ?: Zone.OUTSIDE
            val zone = classify(distance, ban.radiusMeters, warningDistanceMeters, previous)
            zones[ban.id] = zone
            if (zone > previous) {
                alerts += ProximityAlert(ban, zone, max(0.0, distance - ban.radiusMeters))
            }
        }
        return alerts
    }

    private fun classify(distance: Double, radius: Double, warning: Double, previous: Zone): Zone {
        val stickyInside = if (previous == Zone.INSIDE) hysteresisMeters else 0.0
        if (distance <= radius + stickyInside) return Zone.INSIDE
        val stickyWarning = if (previous >= Zone.WARNING) hysteresisMeters else 0.0
        if (distance <= radius + warning + stickyWarning) return Zone.WARNING
        return Zone.OUTSIDE
    }

    companion object {
        /** Distance from the given point to the nearest warning boundary, or null if there are no effective bans. */
        fun nearestWarningBoundaryMeters(
            latitude: Double,
            longitude: Double,
            bans: List<Ban>,
            warningDistanceMeters: Double,
            nowEpochMs: Long = System.currentTimeMillis(),
        ): Double? = bans
            .filter { it.isEffective(nowEpochMs) }
            .minOfOrNull {
                max(
                    0.0,
                    Geo.distanceMeters(latitude, longitude, it.latitude, it.longitude) -
                        it.radiusMeters - warningDistanceMeters,
                )
            }
    }
}

/**
 * GPS sampling rate based on how far the nearest warning zone is.
 * Sampling slowly when far away saves battery; intervals are chosen so that even at
 * motorway speed (~130 km/h) the next fix arrives before the user can reach a warning zone.
 */
enum class TrackingCadence(val intervalMs: Long, val minDistanceMeters: Float, val highAccuracy: Boolean) {
    NEAR(intervalMs = 10_000, minDistanceMeters = 10f, highAccuracy = true),
    MEDIUM(intervalMs = 30_000, minDistanceMeters = 50f, highAccuracy = false),
    FAR(intervalMs = 120_000, minDistanceMeters = 250f, highAccuracy = false);

    companion object {
        fun forDistance(nearestBoundaryMeters: Double?): TrackingCadence = when {
            nearestBoundaryMeters == null -> FAR
            nearestBoundaryMeters < 5_000 -> NEAR
            nearestBoundaryMeters < 30_000 -> MEDIUM
            else -> FAR
        }
    }
}
