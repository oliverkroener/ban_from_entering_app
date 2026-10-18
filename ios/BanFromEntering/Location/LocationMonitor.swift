import CoreLocation
import SwiftData

/// Keeps receiving location in the background and warns when the user approaches or enters a banned place.
///
/// Three layers keep this reliable on iOS:
/// 1. Continuous location updates (background mode `location`) checked against *all* bans — no count limit.
///    Accuracy/distance filter adapt to how close the nearest ban is, to save battery.
/// 2. `CLMonitor` circles around the 20 nearest bans (the iOS limit). These relaunch the app
///    even after it was terminated, so a warning still fires.
/// 3. Significant-location-change monitoring, which relaunches the app after larger moves so the
///    set of 20 monitored circles is refreshed as the user travels worldwide.
@MainActor
final class LocationMonitor: NSObject, ObservableObject {
    static let shared = LocationMonitor()

    @Published private(set) var authorization: CLAuthorizationStatus
    @Published private(set) var lastLocation: CLLocation?

    private static let monitorName = "BanRegions"
    private static let maxMonitoredConditions = 20
    private static let conditionRefreshDistanceMeters = 1_000.0

    private let manager = CLLocationManager()
    private let tracker = ProximityTracker()
    private var regions: [BanRegion] = []
    private var cadence: TrackingCadence?
    private var monitor: CLMonitor?
    private var monitorTask: Task<Void, Never>?
    private var lastConditionRefresh: CLLocation?
    private var conditionsDirty = true

    override private init() {
        authorization = manager.authorizationStatus
        super.init()
        manager.delegate = self
    }

    // MARK: Control

    func requestPermission() {
        // From "not determined" iOS first grants While Using and later offers to upgrade to Always.
        switch authorization {
        case .notDetermined, .authorizedWhenInUse: manager.requestAlwaysAuthorization()
        default: break
        }
    }

    func start() {
        guard AppSettings.trackingEnabled else { return }
        reloadRegions()
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
        manager.showsBackgroundLocationIndicator = true
        cadence = nil
        apply(.near)
        manager.startUpdatingLocation()
        manager.startMonitoringSignificantLocationChanges()
        startConditionMonitor()
    }

    func stop() {
        manager.stopUpdatingLocation()
        manager.stopMonitoringSignificantLocationChanges()
        monitorTask?.cancel()
        monitorTask = nil
        if let monitor {
            Task {
                for id in await monitor.identifiers { await monitor.remove(id) }
            }
        }
        monitor = nil
    }

    /// Call after bans were added, edited or deleted.
    func bansDidChange() {
        reloadRegions()
        conditionsDirty = true
        if let lastLocation { handle(lastLocation) }
    }

    /// Call after the warning distance changed.
    func settingsDidChange() {
        conditionsDirty = true
        if let lastLocation { handle(lastLocation) }
    }

    /// One-shot location for the "use my current location" button.
    func currentLocation() async -> CLLocation? {
        if let recent = manager.location, recent.timestamp.timeIntervalSinceNow > -60 { return recent }
        do {
            for try await update in CLLocationUpdate.liveUpdates() {
                if let location = update.location { return location }
            }
        } catch {}
        return nil
    }

    // MARK: Internals

    private func reloadRegions() {
        let bans = (try? AppModel.container.mainContext.fetch(FetchDescriptor<Ban>())) ?? []
        regions = bans.map(\.region)
    }

    private func handle(_ location: CLLocation) {
        lastLocation = location
        let warning = AppSettings.warningDistanceMeters
        let coordinate = location.coordinate
        let accuracy = location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : nil

        tracker.update(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            accuracyMeters: accuracy,
            regions: regions,
            warningDistanceMeters: warning
        ).forEach(Notifier.notify)

        let nearest = ProximityTracker.nearestWarningBoundaryMeters(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            regions: regions,
            warningDistanceMeters: warning
        )
        apply(TrackingCadence.forDistance(nearest))
        refreshMonitoredConditions(around: location)
    }

    private func apply(_ newCadence: TrackingCadence) {
        guard newCadence != cadence else { return }
        cadence = newCadence
        manager.distanceFilter = newCadence.distanceFilterMeters
        switch newCadence {
        case .near: manager.desiredAccuracy = kCLLocationAccuracyBest
        case .medium: manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        case .far: manager.desiredAccuracy = kCLLocationAccuracyKilometer
        }
    }

    private func startConditionMonitor() {
        guard monitorTask == nil else { return }
        monitorTask = Task { [weak self] in
            let monitor = await CLMonitor(Self.monitorName)
            self?.monitor = monitor
            if let location = self?.lastLocation { self?.refreshMonitoredConditions(around: location) }
            do {
                for try await event in await monitor.events where event.state == .satisfied {
                    // Entered the warning circle of a nearby ban (possibly after a relaunch):
                    // make sure precise updates are running so the next fix raises the alert.
                    self?.cadence = nil
                    self?.apply(.near)
                    self?.manager.startUpdatingLocation()
                }
            } catch {}
        }
    }

    /// Keeps the `CLMonitor` circles on the 20 bans nearest to the user.
    private func refreshMonitoredConditions(around location: CLLocation) {
        guard let monitor else { return }
        if !conditionsDirty, let last = lastConditionRefresh,
           last.distance(from: location) < Self.conditionRefreshDistanceMeters { return }
        conditionsDirty = false
        lastConditionRefresh = location

        let warning = AppSettings.warningDistanceMeters
        let maxRadius = manager.maximumRegionMonitoringDistance
        let lat = location.coordinate.latitude
        let lon = location.coordinate.longitude
        let nearest = regions
            .filter { $0.isEffective(at: Date()) }
            .sorted { $0.distanceMeters(latitude: lat, longitude: lon) < $1.distanceMeters(latitude: lat, longitude: lon) }
            .prefix(Self.maxMonitoredConditions)

        // The radius is part of the identifier, so changing a ban's radius or the warning distance replaces the circle.
        var desired: [String: CLMonitor.CircularGeographicCondition] = [:]
        for region in nearest {
            let radius = min(region.radiusMeters + warning, maxRadius)
            desired["\(region.id.uuidString)|\(Int(radius))"] = CLMonitor.CircularGeographicCondition(
                center: CLLocationCoordinate2D(latitude: region.latitude, longitude: region.longitude),
                radius: radius
            )
        }

        Task {
            let existing = Set(await monitor.identifiers)
            for id in existing.subtracting(desired.keys) { await monitor.remove(id) }
            for (id, condition) in desired where !existing.contains(id) {
                await monitor.add(condition, identifier: id)
            }
        }
    }
}

extension LocationMonitor: CLLocationManagerDelegate {
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        Task { @MainActor in self.handle(location) }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorization = status
            if status == .authorizedAlways || status == .authorizedWhenInUse { self.start() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Transient errors (e.g. kCLErrorLocationUnknown) are expected; updates keep coming.
    }
}
