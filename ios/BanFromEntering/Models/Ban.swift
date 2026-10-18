import Foundation
import SwiftData

/// A place the user is banned from entering, modelled as a circle around a center point.
@Model
final class Ban {
    @Attribute(.unique) var id: UUID
    var name: String
    var address: String
    var latitude: Double
    var longitude: Double
    var radiusMeters: Double
    var notes: String
    /// End of the ban (exclusive), or nil if it never expires.
    var validUntil: Date?
    var active: Bool
    var createdAt: Date

    static let defaultRadiusMeters = 100.0

    init(
        name: String,
        address: String = "",
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = Ban.defaultRadiusMeters,
        notes: String = "",
        validUntil: Date? = nil,
        active: Bool = true
    ) {
        self.id = UUID()
        self.name = name
        self.address = address
        self.latitude = latitude
        self.longitude = longitude
        self.radiusMeters = radiusMeters
        self.notes = notes
        self.validUntil = validUntil
        self.active = active
        self.createdAt = Date()
    }

    var region: BanRegion {
        BanRegion(
            id: id,
            name: name,
            latitude: latitude,
            longitude: longitude,
            radiusMeters: radiusMeters,
            validUntil: validUntil,
            active: active
        )
    }
}
