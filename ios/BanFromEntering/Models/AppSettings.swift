import Foundation

/// UserDefaults-backed settings. Views bind to the same keys via @AppStorage.
enum AppSettings {
    static let trackingEnabledKey = "trackingEnabled"
    static let warningDistanceKey = "warningDistanceMeters"
    static let defaultWarningDistanceMeters = 500.0

    static var trackingEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: trackingEnabledKey) }
        set { UserDefaults.standard.set(newValue, forKey: trackingEnabledKey) }
    }

    /// Extra distance outside a ban's radius at which the user is warned.
    static var warningDistanceMeters: Double {
        get {
            let value = UserDefaults.standard.double(forKey: warningDistanceKey)
            return value > 0 ? value : defaultWarningDistanceMeters
        }
        set { UserDefaults.standard.set(newValue, forKey: warningDistanceKey) }
    }
}
