package com.resort_cloud.nansei.nansei_tablet.utils

import android.graphics.Color

/**
 * Utility to get marker colors based on facility kind
 * Colors extracted from miyako_map.html CSS classes
 */
object MarkerColorUtils {
    
    /**
     * Get color for facility kind label
     * Based on CSS classes in miyako_map.html:
     * - .diviconHotel: #7a6c3d
     * - .diviconRestaurant: #ED6C00
     * - .diviconShopFacilty: #E73278 (used for facility, shop, lift)
     * - .diviconBeach: #00AEC4
     * - .diviconGolf: #6FBA2C
     */
    fun getLabelColorForFacilityKind(facilityKind: String): Int {
        return when (facilityKind.lowercase()) {
            "hotel", "inn", "chapel" -> Color.parseColor("#7a6c3d") // Hotel color (chapel uses hotel style)
            "restaurant" -> Color.parseColor("#ED6C00") // Restaurant color
            "facility", "shop", "shopping", "lift" -> Color.parseColor("#E73278") // Shop/Facility color
            "beach", "swim" -> Color.parseColor("#00AEC4") // Beach color
            "golf" -> Color.parseColor("#6FBA2C") // Golf color
            "parking" -> Color.parseColor("#000000") // Parking has no label color, use black as default
            else -> Color.parseColor("#E73278") // Default to facility color
        }
    }
    
    /**
     * Get color name (hex string) for facility kind
     */
    fun getLabelColorHexForFacilityKind(facilityKind: String): String {
        return when (facilityKind.lowercase()) {
            "hotel", "inn", "chapel" -> "#7a6c3d"
            "restaurant" -> "#ED6C00"
            "facility", "shop", "shopping", "lift" -> "#E73278"
            "beach", "swim" -> "#00AEC4"
            "golf" -> "#6FBA2C"
            "parking" -> "#000000"
            else -> "#E73278"
        }
    }
    
    /**
     * Get opacity for label (from CSS: opacity: 0.5)
     */
    fun getLabelOpacity(): Float = 0.5f
}



