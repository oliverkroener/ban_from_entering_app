import Foundation

// Pure proximity logic (Foundation only, no CoreLocation) — mirrors android/.../geo/Proximity.kt.

enum Geo {
    private static let earthRadiusMeters = 6_371_008.8

    /// Great-circle distance (haversine), accurate enough worldwide for proximity warnings.
    static func distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = pow(sin(dLat / 2), 2) +
            cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * pow(sin(dLon / 2), 2)
        return 2 * earthRadiusMeters * asin(min(1, sqrt(a)))
    }
}

/// Ordered by severity, so `zone > previous` means the situation got worse.
enum Zone: Int, Comparable {
    case outside, warning, inside

    static func < (lhs: Zone, rhs: Zone) -> Bool { lhs.rawValue < rhs.rawValue }
}

/// Value snapshot of a ban, decoupled from SwiftData so it can be evaluated anywhere.
struct BanRegion: Equatable, Sendable {
    let id: UUID
    let name: String
    let latitude: Double
    let longitude: Double
    let radiusMeters: Double
    let validUntil: Date?
    let active: Bool

    func isEffective(at now: Date) -> Bool {
        active && (validUntil.map { $0 > now } ?? true)
    }

    func distanceMeters(latitude: Double, longitude: Double) -> Double {
        Geo.distanceMeters(lat1: latitude, lon1: longitude, lat2: self.latitude, lon2: self.longitude)
    }
}

struct ProximityAlert: Equatable {
    let region: BanRegion
    let zone: Zone
    /// Distance to the edge of the banned area; 0 when inside.
    let distanceToBoundaryMeters: Double
}

/// Tracks which zone the user is in for every ban and reports escalations only
/// (outside → warning, anything → inside), so the user isn't spammed on every GPS fix.
///
/// - GPS uncertainty is subtracted from the distance (capped), so a poor fix errs on the side of warning.
/// - Hysteresis: leaving a zone requires moving `hysteresisMeters` beyond its edge,
///   so jitter right at the boundary doesn't re-trigger alerts.
final class ProximityTracker {
    private let hysteresisMeters: Double
    private let maxAccuracySlackMeters: Double
    private var zones: [UUID: Zone] = [:]

    init(hysteresisMeters: Double = 50, maxAccuracySlackMeters: Double = 100) {
        self.hysteresisMeters = hysteresisMeters
        self.maxAccuracySlackMeters = maxAccuracySlackMeters
    }

    func update(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?,
        regions: [BanRegion],
        warningDistanceMeters: Double,
        now: Date = Date()
    ) -> [ProximityAlert] {
        let slack = min(accuracyMeters ?? 0, maxAccuracySlackMeters)
        let effective = regions.filter { $0.isEffective(at: now) }
        let effectiveIDs = Set(effective.map(\.id))
        zones = zones.filter { effectiveIDs.contains($0.key) }

        var alerts: [ProximityAlert] = []
        for region in effective {
            let distance = max(0, region.distanceMeters(latitude: latitude, longitude: longitude) - slack)
            let previous = zones[region.id] ?? .outside
            let zone = classify(distance, radius: region.radiusMeters, warning: warningDistanceMeters, previous: previous)
            zones[region.id] = zone
            if zone > previous {
                alerts.append(ProximityAlert(
                    region: region,
                    zone: zone,
                    distanceToBoundaryMeters: max(0, distance - region.radiusMeters)
                ))
            }
        }
        return alerts
    }

    private func classify(_ distance: Double, radius: Double, warning: Double, previous: Zone) -> Zone {
        let stickyInside = previous == .inside ? hysteresisMeters : 0
        if distance <= radius + stickyInside { return .inside }
        let stickyWarning = previous >= .warning ? hysteresisMeters : 0
        if distance <= radius + warning + stickyWarning { return .warning }
        return .outside
    }

    /// Distance from the given point to the nearest warning boundary, or nil if there are no effective bans.
    static func nearestWarningBoundaryMeters(
        latitude: Double,
        longitude: Double,
        regions: [BanRegion],
        warningDistanceMeters: Double,
        now: Date = Date()
    ) -> Double? {
        regions
            .filter { $0.isEffective(at: now) }
            .map { max(0, $0.distanceMeters(latitude: latitude, longitude: longitude) - $0.radiusMeters - warningDistanceMeters) }
            .min()
    }
}

/// GPS precision based on how far the nearest warning zone is. Coarse when far away saves battery.
enum TrackingCadence: Equatable {
    case near, medium, far

    /// Minimum movement in meters before iOS delivers the next fix.
    var distanceFilterMeters: Double {
        switch self {
        case .near: 10
        case .medium: 50
        case .far: 250
        }
    }

    static func forDistance(_ nearestBoundaryMeters: Double?) -> TrackingCadence {
        guard let d = nearestBoundaryMeters else { return .far }
        if d < 5_000 { return .near }
        if d < 30_000 { return .medium }
        return .far
    }
}

enum DistanceFormat {
    static func string(_ meters: Double) -> String {
        meters >= 1_000 ? String(format: "%.1f km", meters / 1_000) : "\(Int(meters.rounded())) m"
    }
}
