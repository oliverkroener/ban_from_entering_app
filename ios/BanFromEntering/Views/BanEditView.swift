import MapKit
import SwiftData
import SwiftUI

struct BanEditView: View {
    /// nil creates a new ban.
    let ban: Ban?

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var monitor: LocationMonitor

    @State private var name = ""
    @State private var address = ""
    @State private var notes = ""
    @State private var coordinate: CLLocationCoordinate2D?
    @State private var radius = Ban.defaultRadiusMeters
    @State private var hasEndDate = false
    @State private var endDate = Calendar.current.date(byAdding: .year, value: 1, to: Date()) ?? Date()
    @State private var active = true

    @State private var camera: MapCameraPosition = .automatic
    @State private var searchResults: [AddressSearch.Result] = []
    @State private var confirmingDelete = false

    init(ban: Ban?, initialCoordinate: CLLocationCoordinate2D? = nil) {
        self.ban = ban
        _coordinate = State(initialValue: initialCoordinate)
    }

    var body: some View {
        Form {
            Section("Place") {
                TextField("Name (e.g. store, venue, person's home)", text: $name)
                HStack {
                    TextField("Address", text: $address)
                        .onSubmit(search)
                        .submitLabel(.search)
                    Button(action: search) { Image(systemName: "magnifyingglass") }
                }
                ForEach(searchResults) { result in
                    Button {
                        address = [result.title, result.subtitle].filter { !$0.isEmpty }.joined(separator: ", ")
                        moveTo(result.coordinate)
                        searchResults = []
                    } label: {
                        VStack(alignment: .leading) {
                            Text(result.title)
                            Text(result.subtitle).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }

            Section {
                MapReader { proxy in
                    Map(position: $camera) {
                        UserAnnotation()
                        if let coordinate {
                            Marker(name.isEmpty ? "Ban" : name, systemImage: "nosign", coordinate: coordinate)
                                .tint(.red)
                            MapCircle(center: coordinate, radius: radius)
                                .foregroundStyle(.red.opacity(0.25))
                                .stroke(.red, lineWidth: 2)
                        }
                    }
                    .onTapGesture { point in
                        if let tapped = proxy.convert(point, from: .local) { coordinate = tapped }
                    }
                }
                .frame(height: 280)
                .listRowInsets(EdgeInsets())

                Button {
                    Task { if let location = await monitor.currentLocation() { moveTo(location.coordinate) } }
                } label: {
                    Label("Use my current location", systemImage: "location.fill")
                }
            } header: {
                Text("Location")
            } footer: {
                Text("Tap the map to set the center of the banned area.")
            }

            Section("Banned radius: \(DistanceFormat.string(radius))") {
                Slider(value: $radius, in: 25...5_000, step: 25)
            }

            Section("Validity") {
                Toggle("Has end date", isOn: $hasEndDate)
                if hasEndDate {
                    DatePicker("Valid until", selection: $endDate, displayedComponents: .date)
                }
                Toggle("Active", isOn: $active)
            }

            Section("Notes") {
                TextField("Case number, issuing authority, …", text: $notes, axis: .vertical)
                    .lineLimit(3...8)
            }

            if ban != nil {
                Section {
                    Button("Delete ban", role: .destructive) { confirmingDelete = true }
                }
            }
        }
        .navigationTitle(ban == nil ? "New ban" : "Edit ban")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if ban == nil {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save", action: save)
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || coordinate == nil)
            }
        }
        .confirmationDialog("Delete this ban?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive, action: delete)
        }
        .onAppear(perform: load)
    }

    private func load() {
        guard let ban else {
            if let coordinate { camera = .region(MKCoordinateRegion(center: coordinate, latitudinalMeters: 1_500, longitudinalMeters: 1_500)) }
            return
        }
        name = ban.name
        address = ban.address
        notes = ban.notes
        radius = ban.radiusMeters
        active = ban.active
        if let until = ban.validUntil {
            hasEndDate = true
            endDate = until.addingTimeInterval(-1)
        }
        moveTo(CLLocationCoordinate2D(latitude: ban.latitude, longitude: ban.longitude))
    }

    private func moveTo(_ newCoordinate: CLLocationCoordinate2D) {
        coordinate = newCoordinate
        camera = .region(MKCoordinateRegion(center: newCoordinate, latitudinalMeters: 1_500, longitudinalMeters: 1_500))
    }

    private func search() {
        Task { searchResults = await AddressSearch.search(address) }
    }

    private func save() {
        guard let coordinate else { return }
        // The ban is valid through the whole selected day.
        let validUntil = hasEndDate
            ? Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: endDate))
            : nil
        let target = ban ?? Ban(name: name, latitude: coordinate.latitude, longitude: coordinate.longitude)
        target.name = name.trimmingCharacters(in: .whitespaces)
        target.address = address.trimmingCharacters(in: .whitespaces)
        target.latitude = coordinate.latitude
        target.longitude = coordinate.longitude
        target.radiusMeters = radius
        target.notes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        target.validUntil = validUntil
        target.active = active
        if ban == nil { context.insert(target) }
        try? context.save()
        monitor.bansDidChange()
        dismiss()
    }

    private func delete() {
        guard let ban else { return }
        context.delete(ban)
        try? context.save()
        monitor.bansDidChange()
        dismiss()
    }
}
