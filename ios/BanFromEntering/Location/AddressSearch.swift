import MapKit

/// Worldwide address / place lookup via Apple Maps.
enum AddressSearch {
    struct Result: Identifiable {
        let id = UUID()
        let title: String
        let subtitle: String
        let coordinate: CLLocationCoordinate2D
    }

    static func search(_ query: String) async -> [Result] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        request.resultTypes = [.address, .pointOfInterest]
        guard let response = try? await MKLocalSearch(request: request).start() else { return [] }
        return response.mapItems.prefix(5).map { item in
            Result(
                title: item.name ?? query,
                subtitle: item.placemark.title ?? "",
                coordinate: item.placemark.coordinate
            )
        }
    }
}
