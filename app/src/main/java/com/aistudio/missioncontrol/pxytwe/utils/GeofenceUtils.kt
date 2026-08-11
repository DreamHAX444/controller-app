package com.aistudio.missioncontrol.pxytwe.utils

import org.osmdroid.util.GeoPoint
import androidx.core.graphics.toColorInt

data class GeofenceData(val points: List<GeoPoint>, val colorArgb: Int, val name: String = "Unnamed Zone")

object GeofenceUtils {

    /**
     * Ray-Casting algorithm to determine if a given point is inside a polygon.
     * @param point The point to check.
     * @param polygon The list of points defining the polygon.
     * @return true if the point is inside the polygon, false otherwise.
     */
    fun isPointInPolygon(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        var isInside = false
        var i = 0
        var j = polygon.size - 1

        while (i < polygon.size) {
            val xi = polygon[i].latitude
            val yi = polygon[i].longitude
            val xj = polygon[j].latitude
            val yj = polygon[j].longitude

            val intersect = ((yi > point.longitude) != (yj > point.longitude)) &&
                    (point.latitude < (xj - xi) * (point.longitude - yi) / (yj - yi) + xi)
            
            if (intersect) {
                isInside = !isInside
            }
            
            j = i++
        }

        return isInside
    }

    fun getCentroid(points: List<GeoPoint>): GeoPoint {
        if (points.isEmpty()) return GeoPoint(0.0, 0.0)
        var sumLat = 0.0
        var sumLon = 0.0
        for (point in points) {
            sumLat += point.latitude
            sumLon += point.longitude
        }
        return GeoPoint(sumLat / points.size, sumLon / points.size)
    }

    fun serializeGeofences(geofences: List<GeofenceData>): String {
        return geofences.joinToString(separator = ";") { fence ->
            val pointsStr = fence.points.joinToString(separator = "|") { point ->
                "${point.latitude},${point.longitude}"
            }
            "$pointsStr~${fence.colorArgb}^${fence.name}"
        }
    }

    fun deserializeGeofences(data: String): List<GeofenceData> {
        if (data.isBlank()) return emptyList()
        val defaultColor = "#D4AF37".toColorInt() // Default Gold
        return data.split(";").mapNotNull { polygonStr ->
            if (polygonStr.isBlank()) null
            else {
                val mainParts = polygonStr.split("~")
                val pointsStr = mainParts[0]
                
                var color = defaultColor
                var name = "Unnamed Zone"
                
                if (mainParts.size > 1) {
                    val metadataParts = mainParts[1].split("^")
                    color = metadataParts[0].toIntOrNull() ?: defaultColor
                    if (metadataParts.size > 1) {
                        name = metadataParts[1]
                    }
                }
                
                val points = pointsStr.split("|").mapNotNull { pointStr ->
                    val coordinates = pointStr.split(",")
                    if (coordinates.size == 2) {
                        val lat = coordinates[0].toDoubleOrNull()
                        val lon = coordinates[1].toDoubleOrNull()
                        if (lat != null && lon != null) GeoPoint(lat, lon) else null
                    } else null
                }
                if (points.isNotEmpty()) GeofenceData(points, color, name) else null
            }
        }
    }
}
