package com.resort_cloud.nansei.nansei_tablet.utils

import net.osmand.data.LatLon

/**
 * Constants for map bounds
 * Based on miyako_map.html getMapArea() and getMapAreaToShowCaution()
 */
object MapBoundsConstants {
    
    /**
     * Main map area bounds (getMapArea)
     * Format: minLat, minLng, maxLat, maxLng
     * "24.716021,125.33220,24.726354,125.35021"
     */
    val MAP_MIN_LAT = 24.716021
    val MAP_MIN_LNG = 125.33220
    val MAP_MAX_LAT = 24.726354
    val MAP_MAX_LNG = 125.35021
    
    /**
     * Caution area bounds (getMapAreaToShowCaution)
     * Format: minLat, minLng, maxLat, maxLng
     * "24.71780,125.33220,24.72635,125.35028"
     */
    val CAUTION_MIN_LAT = 24.71780
    val CAUTION_MIN_LNG = 125.33220
    val CAUTION_MAX_LAT = 24.72635
    val CAUTION_MAX_LNG = 125.35028
    
    /**
     * Check if location is within main map bounds
     */
    fun isLocationInMapBounds(lat: Double, lng: Double): Boolean {
        return lat > MAP_MIN_LAT && lat < MAP_MAX_LAT &&
               lng > MAP_MIN_LNG && lng < MAP_MAX_LNG
    }
    
    /**
     * Check if location is within caution bounds
     */
    fun isLocationInCautionBounds(lat: Double, lng: Double): Boolean {
        return lat > CAUTION_MIN_LAT && lat < CAUTION_MAX_LAT &&
               lng > CAUTION_MIN_LNG && lng < CAUTION_MAX_LNG
    }
    
    /**
     * Check if location is outside map bounds (needs dialog warning)
     */
    fun isLocationOutOfMapBounds(lat: Double, lng: Double): Boolean {
        return !isLocationInMapBounds(lat, lng)
    }
    
    /**
     * Check if location is outside caution bounds (needs text warning + beep)
     */
    fun isLocationOutOfCautionBounds(lat: Double, lng: Double): Boolean {
        return !isLocationInCautionBounds(lat, lng)
    }
}

