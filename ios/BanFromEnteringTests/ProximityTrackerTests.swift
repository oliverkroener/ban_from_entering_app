import XCTest
@testable import BanFromEntering

final class ProximityTrackerTests: XCTestCase {
    // ~111 m per 0.001° of latitude.
    private let ban = BanRegion(
        id: UUID(), name: "Store", latitude: 48.0, longitude: 11.0,
        radiusMeters: 100, validUntil: nil, active: true
    )
    private let warning = 500.0
    private var tracker = ProximityTracker()

    override func setUp() {
        tracker = ProximityTracker()
    }

    private func at(_ latOffset: Double, accuracy: Double? = nil, regions: [BanRegion]? = nil, now: Date = Date()) -> [ProximityAlert] {
        tracker.update(
            latitude: 48.0 + latOffset, longitude: 11.0, accuracyMeters: accuracy,
            regions: regions ?? [ban], warningDistanceMeters: warning, now: now
        )
    }

    func testHaversineMatchesKnownDistance() {
        // Berlin → Munich ≈ 504 km
        let d = Geo.distanceMeters(lat1: 52.5200, lon1: 13.4050, lat2: 48.1351, lon2: 11.5820)
        XCTAssertEqual(d, 504_000, accuracy: 2_000)
    }

    func testFarAwayRaisesNothing() {
        XCTAssertTrue(at(0.1).isEmpty)
    }

    func testApproachingEscalatesWarningThenInside() {
        XCTAssertTrue(at(0.01).isEmpty)
        XCTAssertEqual(at(0.005).map(\.zone), [.warning])
        XCTAssertTrue(at(0.004).isEmpty)
        let inside = at(0.0005)
        XCTAssertEqual(inside.map(\.zone), [.inside])
        XCTAssertEqual(inside.first?.distanceToBoundaryMeters, 0)
    }

    func testHysteresisPreventsFlappingAtBoundary() {
        XCTAssertEqual(at(0.0053).map(\.zone), [.warning])
        XCTAssertTrue(at(0.0055).isEmpty)
        XCTAssertTrue(at(0.0053).isEmpty)
        XCTAssertTrue(at(0.0070).isEmpty)
        XCTAssertEqual(at(0.0053).map(\.zone), [.warning])
    }

    func testPoorAccuracyErrsOnTheSideOfWarning() {
        XCTAssertEqual(at(0.0060, accuracy: 80).map(\.zone), [.warning])
    }

    func testExpiredAndInactiveBansAreIgnored() {
        let expired = BanRegion(id: ban.id, name: ban.name, latitude: 48, longitude: 11, radiusMeters: 100,
                                validUntil: Date(timeIntervalSince1970: 1_000), active: true)
        XCTAssertTrue(at(0, regions: [expired], now: Date(timeIntervalSince1970: 2_000)).isEmpty)
        let inactive = BanRegion(id: ban.id, name: ban.name, latitude: 48, longitude: 11, radiusMeters: 100,
                                 validUntil: nil, active: false)
        XCTAssertTrue(at(0, regions: [inactive]).isEmpty)
    }

    func testNearestBoundaryAndCadence() {
        XCTAssertNil(ProximityTracker.nearestWarningBoundaryMeters(latitude: 0, longitude: 0, regions: [], warningDistanceMeters: warning))
        let d = ProximityTracker.nearestWarningBoundaryMeters(latitude: 48.1, longitude: 11.0, regions: [ban], warningDistanceMeters: warning)!
        XCTAssertEqual(d, 11_119 - 600, accuracy: 50)
        XCTAssertEqual(TrackingCadence.forDistance(d), .medium)
        XCTAssertEqual(TrackingCadence.forDistance(0), .near)
        XCTAssertEqual(TrackingCadence.forDistance(nil), .far)
    }
}
