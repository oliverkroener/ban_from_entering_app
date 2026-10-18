import CoreLocation
import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @EnvironmentObject private var monitor: LocationMonitor
    @AppStorage(AppSettings.trackingEnabledKey) private var trackingEnabled = false
    @AppStorage(AppSettings.warningDistanceKey) private var warningDistance = AppSettings.defaultWarningDistanceMeters

    var body: some View {
        Form {
            Section {
                Toggle("GPS proximity warnings", isOn: $trackingEnabled)
                    .onChange(of: trackingEnabled) { _, enabled in
                        if enabled {
                            monitor.requestPermission()
                            Task { await Notifier.requestAuthorization() }
                            monitor.start()
                        } else {
                            monitor.stop()
                        }
                    }
            } footer: {
                Text("Your location is only processed on this device.")
            }

            Section("Warn me within \(DistanceFormat.string(warningDistance)) of a banned area") {
                Slider(value: $warningDistance, in: 50...5_000, step: 50) { editing in
                    if !editing { monitor.settingsDidChange() }
                }
            }

            Section("Location permission") {
                LabeledContent("Status", value: authorizationText)
                if monitor.authorization != .authorizedAlways {
                    Text("Choose \"Always\" so you are warned even when the app is closed.")
                        .font(.footnote)
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                }
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
        }
    }

    private var authorizationText: String {
        switch monitor.authorization {
        case .authorizedAlways: "Always"
        case .authorizedWhenInUse: "While using the app"
        case .denied: "Denied"
        case .restricted: "Restricted"
        case .notDetermined: "Not asked yet"
        @unknown default: "Unknown"
        }
    }
}
