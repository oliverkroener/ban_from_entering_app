package com.banfromentering.app.geo

import com.banfromentering.app.data.Ban
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityTrackerTest {

    // ~111 m per 0.001° of latitude.
    private val ban = Ban(id = 1, name = "Store", latitude = 48.0, longitude = 11.0, radiusMeters = 100.0)
    private val warning = 500.0
    private val tracker = ProximityTracker()

    private fun at(latOffset: Double, accuracy: Double? = null, bans: List<Ban> = listOf(ban), now: Long = 0) =
        tracker.update(48.0 + latOffset, 11.0, accuracy, bans, warning, now)

    @Test
    fun haversineMatchesKnownDistance() {
        // Berlin → Munich ≈ 504 km
        val d = Geo.distanceMeters(52.5200, 13.4050, 48.1351, 11.5820)
        assertEquals(504_000.0, d, 2_000.0)
    }

    @Test
    fun farAwayRaisesNothing() {
        assertTrue(at(0.1).isEmpty())
    }

    @Test
    fun approachingEscalatesWarningThenInside() {
        assertTrue(at(0.01).isEmpty()) // ~1.1 km
        val warn = at(0.005) // ~555 m → inside 100 + 500 warning band
        assertEquals(Zone.WARNING, warn.single().zone)
        assertTrue(at(0.004).isEmpty()) // still warning: no repeat
        val inside = at(0.0005) // ~55 m
        assertEquals(Zone.INSIDE, inside.single().zone)
        assertEquals(0.0, inside.single().distanceToBoundaryMeters, 0.0)
    }

    @Test
    fun hysteresisPreventsFlappingAtBoundary() {
        assertEquals(Zone.WARNING, at(0.0053).single().zone) // ~589 m, just inside 600 m
        assertTrue(at(0.0055).isEmpty()) // ~611 m, inside hysteresis band → still warning
        assertTrue(at(0.0053).isEmpty()) // back in: no second alert
        assertTrue(at(0.0070).isEmpty()) // ~778 m: leaves the zone
        assertEquals(Zone.WARNING, at(0.0053).single().zone) // re-entering warns again
    }

    @Test
    fun poorAccuracyErrsOnTheSideOfWarning() {
        assertEquals(Zone.WARNING, at(0.0060, accuracy = 80.0).single().zone) // ~667 m − 80 m
    }

    @Test
    fun expiredAndInactiveBansAreIgnored() {
        val expired = ban.copy(validUntilEpochMs = 1_000)
        assertTrue(at(0.0, bans = listOf(expired), now = 2_000).isEmpty())
        assertTrue(at(0.0, bans = listOf(ban.copy(active = false))).isEmpty())
    }

    @Test
    fun nearestBoundaryAndCadence() {
        assertNull(ProximityTracker.nearestWarningBoundaryMeters(0.0, 0.0, emptyList(), warning))
        val d = ProximityTracker.nearestWarningBoundaryMeters(48.1, 11.0, listOf(ban), warning)!!
        assertEquals(11_119.0 - 600.0, d, 50.0)
        assertEquals(TrackingCadence.MEDIUM, TrackingCadence.forDistance(d))
        assertEquals(TrackingCadence.NEAR, TrackingCadence.forDistance(0.0))
        assertEquals(TrackingCadence.FAR, TrackingCadence.forDistance(null))
    }
}
