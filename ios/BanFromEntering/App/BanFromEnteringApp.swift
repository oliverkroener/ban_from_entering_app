import SwiftData
import SwiftUI
import UserNotifications

@main
struct BanFromEnteringApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            BanListView()
                .environmentObject(LocationMonitor.shared)
        }
        .modelContainer(AppModel.container)
    }
}

enum AppModel {
    @MainActor static let container: ModelContainer = {
        do {
            return try ModelContainer(for: Ban.self)
        } catch {
            fatalError("Could not open the ban database: \(error)")
        }
    }()
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        // Also runs when iOS relaunches the app in the background for a location event.
        LocationMonitor.shared.start()
        return true
    }

    /// Show warnings as banners even while the app is open.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}
