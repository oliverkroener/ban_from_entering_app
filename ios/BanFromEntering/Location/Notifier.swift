import Foundation
import UserNotifications

enum Notifier {
    static func requestAuthorization() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }

    static func notify(_ alert: ProximityAlert) {
        let content = UNMutableNotificationContent()
        switch alert.zone {
        case .inside:
            content.title = "BANNED AREA: \(alert.region.name)"
            content.body = "You are inside a place you are banned from entering. Leave now."
        default:
            content.title = "Approaching: \(alert.region.name)"
            content.body = "You are \(DistanceFormat.string(alert.distanceToBoundaryMeters)) from a place you are banned from entering."
        }
        content.sound = .default

        // One notification per ban, so a later "inside" alert replaces the earlier warning.
        let request = UNNotificationRequest(identifier: "ban-\(alert.region.id.uuidString)", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
