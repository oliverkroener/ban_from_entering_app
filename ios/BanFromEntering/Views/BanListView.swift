import SwiftData
import SwiftUI

struct BanListView: View {
    @Environment(\.modelContext) private var context
    @EnvironmentObject private var monitor: LocationMonitor
    @Query(sort: \Ban.name) private var bans: [Ban]
    @AppStorage(AppSettings.trackingEnabledKey) private var trackingEnabled = false

    @State private var showingNew = false
    @State private var showingSettings = false

    var body: some View {
        NavigationStack {
            List {
                if !trackingEnabled {
                    Section {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.red)
                            Text("GPS warnings are off.")
                            Spacer()
                            Button("Turn on") { showingSettings = true }
                        }
                    }
                }

                if bans.isEmpty {
                    ContentUnavailableView(
                        "No bans yet",
                        systemImage: "nosign",
                        description: Text("Tap + to add a place you may not enter.")
                    )
                }

                ForEach(bans) { ban in
                    NavigationLink(value: ban) { BanRow(ban: ban) }
                }
                .onDelete(perform: delete)
            }
            .navigationTitle("Banned places")
            .navigationDestination(for: Ban.self) { BanEditView(ban: $0) }
            .toolbar {
                ToolbarItemGroup(placement: .topBarLeading) {
                    NavigationLink { BanMapView() } label: { Image(systemName: "map") }
                    Button { showingSettings = true } label: { Image(systemName: "gearshape") }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showingNew = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showingNew) {
                NavigationStack { BanEditView(ban: nil) }
            }
            .sheet(isPresented: $showingSettings) {
                NavigationStack { SettingsView() }
            }
        }
    }

    private func delete(at offsets: IndexSet) {
        offsets.map { bans[$0] }.forEach(context.delete)
        try? context.save()
        monitor.bansDidChange()
    }
}

private struct BanRow: View {
    let ban: Ban

    private var status: String {
        if !ban.active { return "Inactive" }
        guard let until = ban.validUntil else { return "Permanent" }
        if until <= Date() { return "Expired" }
        // validUntil is exclusive (start of the following day).
        return "Until " + until.addingTimeInterval(-1).formatted(date: .abbreviated, time: .omitted)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(ban.name).font(.headline)
            if !ban.address.isEmpty {
                Text(ban.address).font(.subheadline)
            }
            Text("Radius \(DistanceFormat.string(ban.radiusMeters)) · \(status)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
