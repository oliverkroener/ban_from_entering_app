import MapKit
import SwiftData
import SwiftUI

struct BanMapView: View {
    @Query private var bans: [Ban]
    @AppStorage(AppSettings.warningDistanceKey) private var warningDistance = AppSettings.defaultWarningDistanceMeters

    @State private var camera: MapCameraPosition = .userLocation(fallback: .automatic)
    @State private var visibleCenter: CLLocationCoordinate2D?
    @State private var selectedID: UUID?
    @State private var newBanCoordinate: IdentifiableCoordinate?

    var body: some View {
        let now = Date()
        Map(position: $camera, selection: $selectedID) {
            UserAnnotation()
            ForEach(bans) { ban in
                let center = CLLocationCoordinate2D(latitude: ban.latitude, longitude: ban.longitude)
                let effective = ban.region.isEffective(at: now)
                if effective {
                    MapCircle(center: center, radius: ban.radiusMeters + warningDistance)
                        .foregroundStyle(.orange.opacity(0.12))
                        .stroke(.orange, lineWidth: 1)
                }
                MapCircle(center: center, radius: ban.radiusMeters)
                    .foregroundStyle(.red.opacity(effective ? 0.3 : 0.1))
                    .stroke(.red.opacity(effective ? 1 : 0.4), lineWidth: 2)
                Marker(ban.name, systemImage: "nosign", coordinate: center)
                    .tint(effective ? .red : .gray)
                    .tag(ban.id)
            }
        }
        .mapControls {
            MapUserLocationButton()
            MapCompass()
            MapScaleView()
        }
        .onMapCameraChange { visibleCenter = $0.region.center }
        .overlay {
            // Crosshair marks where "Add here" will place the new ban.
            Image(systemName: "plus.circle").font(.title).foregroundStyle(.red).allowsHitTesting(false)
        }
        .navigationTitle("Map")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button("Add here") {
                if let visibleCenter { newBanCoordinate = IdentifiableCoordinate(coordinate: visibleCenter) }
            }
        }
        .sheet(item: $newBanCoordinate) { item in
            NavigationStack { BanEditView(ban: nil, initialCoordinate: item.coordinate) }
        }
        .sheet(item: selectedBan) { ban in
            NavigationStack { BanEditView(ban: ban) }
        }
    }

    private var selectedBan: Binding<Ban?> {
        Binding(
            get: { bans.first { $0.id == selectedID } },
            set: { selectedID = $0?.id }
        )
    }
}

private struct IdentifiableCoordinate: Identifiable {
    let id = UUID()
    let coordinate: CLLocationCoordinate2D
}
