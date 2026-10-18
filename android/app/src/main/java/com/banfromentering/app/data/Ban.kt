package com.banfromentering.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A place the user is banned from entering, modelled as a circle around a center point. */
@Entity(tableName = "bans")
data class Ban(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    val notes: String = "",
    /** End of the ban (exclusive), or null if it never expires. */
    val validUntilEpochMs: Long? = null,
    val active: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
    fun isEffective(nowEpochMs: Long): Boolean =
        active && (validUntilEpochMs == null || validUntilEpochMs > nowEpochMs)

    companion object {
        const val DEFAULT_RADIUS_METERS = 100.0
    }
}
