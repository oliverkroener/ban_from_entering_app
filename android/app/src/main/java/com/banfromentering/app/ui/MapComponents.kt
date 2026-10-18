package com.banfromentering.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.banfromentering.app.ui.theme.BanRed
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle

/** Red filled circle for the banned area, with an optional dashed-looking outer warning ring. */
@Composable
fun BanCircle(center: LatLng, radiusMeters: Double, warningDistanceMeters: Double? = null, dimmed: Boolean = false) {
    val alpha = if (dimmed) 0.4f else 1f
    if (warningDistanceMeters != null && warningDistanceMeters > 0) {
        Circle(
            center = center,
            radius = radiusMeters + warningDistanceMeters,
            fillColor = Color(0xFFFFA000).copy(alpha = 0.12f * alpha),
            strokeColor = Color(0xFFFFA000).copy(alpha = alpha),
            strokeWidth = 2f,
        )
    }
    Circle(
        center = center,
        radius = radiusMeters,
        fillColor = BanRed.copy(alpha = 0.3f * alpha),
        strokeColor = BanRed.copy(alpha = alpha),
        strokeWidth = 4f,
    )
}
