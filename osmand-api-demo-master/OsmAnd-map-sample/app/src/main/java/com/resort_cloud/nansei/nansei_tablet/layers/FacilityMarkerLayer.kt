package com.resort_cloud.nansei.nansei_tablet.layers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import net.osmand.data.RotatedTileBox
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.plus.views.layers.base.OsmandMapLayer.DrawSettings
import com.resort_cloud.nansei.nansei_tablet.data.MarkerDataConstants
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom layer to display facility markers on map
 * Based on miyako_map.html marker logic
 * Uses constant data from MarkerDataConstants instead of API data
 */
class FacilityMarkerLayer(
    context: Context
) : OsmandMapLayer(context) {

    private val iconCache: MutableMap<String, Bitmap?> = ConcurrentHashMap()
    private val baseIconSizeMap: MutableMap<String, Pair<Int, Int>> = ConcurrentHashMap()
    
    // Get all markers from constants
    private val markers = MarkerDataConstants.getAllMarkers()
    
    // Scale factor for icon size (increase from original HTML sizes)
    // Reduced to 1.5x to make icons smaller
    private val iconScaleFactor = 1.5f

    init {
        // Initialize base icon sizes based on miyako_map.html (will be scaled)
        baseIconSizeMap["hotel"] = Pair(20, 25)
        baseIconSizeMap["restaurant"] = Pair(20, 25)
        baseIconSizeMap["facility"] = Pair(20, 25)
        baseIconSizeMap["shop"] = Pair(20, 25)
        baseIconSizeMap["beach"] = Pair(20, 25)
        baseIconSizeMap["lift"] = Pair(20, 25)
        baseIconSizeMap["golf"] = Pair(20, 25)
        baseIconSizeMap["parking"] = Pair(15, 18)
        baseIconSizeMap["chapel"] = Pair(20, 25)
        baseIconSizeMap["inn"] = Pair(20, 25) // Same as hotel
        baseIconSizeMap["amusement"] = Pair(20, 25)
        baseIconSizeMap["swim"] = Pair(20, 25) // Same as beach
        baseIconSizeMap["shopping"] = Pair(20, 25) // Same as shop
    }

    override fun onDraw(
        canvas: Canvas?,
        tileBox: RotatedTileBox?,
        settings: DrawSettings?
    ) {
        if (canvas == null || tileBox == null || markers.isEmpty()) {
            return
        }

        // Lấy kích thước màn hình hiện tại
        val viewWidth = tileBox.pixWidth
        val viewHeight = tileBox.pixHeight

        // Thêm vùng đệm (buffer) khoảng 100px để marker ở mép không bị cắt đột ngột
        val buffer = 100f

        val density = view?.density ?: 1.0f
        val scaleFactor = iconScaleFactor * density

        // Tính toán các giá trị không đổi ngoài vòng lặp
        val parkingAnchorY = 10f * scaleFactor
        val defaultAnchorY = 20f * scaleFactor
        val baseAnchorX = 10f * scaleFactor

        for (marker in markers) {
            val lat = marker.latitude
            val lon = marker.longitude

            // 1. Tính toán tọa độ màn hình trực tiếp
            // Lưu ý: OsmAnd tính toán location rất nhanh, nên tính trước rồi lọc sau sẽ mượt hơn
            val x = tileBox.getPixXFromLatLon(lat, lon)
            val y = tileBox.getPixYFromLatLon(lat, lon)

            // 2. Kiểm tra xem tọa độ Pixel có nằm trong màn hình (+ buffer) không
            // Cách này nhanh hơn và chính xác hơn so với so sánh Lat/Lon khi đang zoom/pan
            if (x >= -buffer && x <= viewWidth + buffer &&
                y >= -buffer && y <= viewHeight + buffer) {

                // 3. Chỉ lấy bitmap khi chắc chắn cần vẽ
                val iconBitmap = getIconBitmapForFacility(marker.facilityKind, scaleFactor)

                iconBitmap?.let { bitmap ->
                    val isParking = marker.facilityKind.equals("parking", ignoreCase = true)

                    // Tính anchor đã scale
                    val anchorX = baseAnchorX.toInt()
                    val anchorY = (if (isParking) parkingAnchorY else defaultAnchorY).toInt()

                    val left = x - anchorX
                    val top = y - anchorY

                    canvas.drawBitmap(
                        bitmap,
                        left,
                        top,
                        null
                    )
                }
            }
        }
    }

    /**
     * Get icon bitmap for facility kind with scale factor
     * Maps facility kinds to icon names based on miyako_map.html
     */
    private fun getIconBitmapForFacility(facilityKind: String, scaleFactor: Float): Bitmap? {
        val iconName = getIconNameForFacilityKind(facilityKind.lowercase())
        if (iconName == null) {
            return null
        }

        // Use fixed cache key based on density only (not scaleFactor)
        // This ensures icons are cached once per density and update position in real-time during zoom
        val density = view?.density ?: 1.0f
        val cacheKey = "${iconName}_${density}"
        
        // Check cache first
        if (iconCache.containsKey(cacheKey)) {
            // Return cached bitmap - position will be recalculated in onDraw for real-time updates
            return iconCache[cacheKey]
        }

        // Load directly from drawable resources (icons are in drawable folder)
        val resId = context.resources.getIdentifier(
            iconName,  // Direct name: hotel, restaurant, etc.
            "drawable",
            context.packageName
        )
        
        var bitmap: Bitmap? = null
        if (resId != 0) {
            bitmap = ContextCompat.getDrawable(context, resId)?.let { drawable ->
                val (width, height) = getIconSize(facilityKind, scaleFactor)
                // Scale drawable to required size
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    val canvas = Canvas(this)
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(canvas)
                }
            }
        }

        // If still null, create a placeholder bitmap
        if (bitmap == null) {
            val (width, height) = getIconSize(facilityKind, scaleFactor)
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // Fill with transparent color (or you can add a default icon)
        }

        // Cache the bitmap with density-based key
        iconCache[cacheKey] = bitmap
        return bitmap
    }

    /**
     * Map facility kind to icon name based on miyako_map.html
     */
    private fun getIconNameForFacilityKind(facilityKind: String): String? {
        return when (facilityKind) {
            "hotel", "inn" -> "hotel"
            "restaurant" -> "restaurant"
            "facility" -> "facility"
            "shop", "shopping" -> "shop"
            "beach", "swim" -> "beach"
            "lift" -> "lift"
            "golf" -> "golf"
            "parking" -> "parking"
            "chapel" -> "chapel"
            "amusement" -> "facility" // Use facility icon as fallback
            else -> null
        }
    }

    /**
     * Get icon size for facility kind with scale factor
     */
    private fun getIconSize(facilityKind: String, scaleFactor: Float): Pair<Int, Int> {
        val key = when (facilityKind.lowercase()) {
            "parking" -> "parking"
            "hotel", "inn" -> "hotel"
            "restaurant" -> "restaurant"
            "facility" -> "facility"
            "shop", "shopping" -> "shop"
            "beach", "swim" -> "beach"
            "lift" -> "lift"
            "golf" -> "golf"
            "chapel" -> "chapel"
            "amusement" -> "facility"
            else -> "facility"
        }
        val baseSize = baseIconSizeMap[key] ?: Pair(20, 25)
        return Pair(
            (baseSize.first * scaleFactor).toInt(),
            (baseSize.second * scaleFactor).toInt()
        )
    }

    override fun drawInScreenPixels(): Boolean {
        return true
    }

}

