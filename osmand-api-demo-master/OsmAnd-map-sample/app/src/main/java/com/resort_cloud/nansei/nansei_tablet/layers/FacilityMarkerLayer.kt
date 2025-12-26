package com.resort_cloud.nansei.nansei_tablet.layers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.resort_cloud.nansei.nansei_tablet.data.MarkerDataConstants
import net.osmand.core.android.MapRendererContext
import net.osmand.core.android.MapRendererView
import net.osmand.core.jni.MapMarker
import net.osmand.core.jni.MapTiledCollectionProvider
import net.osmand.core.jni.PointI
import net.osmand.core.jni.QListMapTiledCollectionPoint
import net.osmand.core.jni.QListPointI
import net.osmand.core.jni.SingleSkImage
import net.osmand.core.jni.SwigUtilities
import net.osmand.core.jni.TextRasterizer
import net.osmand.core.jni.TileId
import net.osmand.core.jni.ZoomLevel
import net.osmand.core.jni.interface_MapTiledCollectionProvider
import net.osmand.data.RotatedTileBox
import net.osmand.plus.utils.NativeUtilities
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.views.layers.MapTextLayer
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.util.MapUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * OFFICIAL APPROACH: Use MapTiledCollectionProvider
 * Markers are rendered as part of map tiles, no need to redraw on pan/zoom
 * Similar to default POI markers and Favorites markers in OsmAnd
 */
class FacilityMarkerLayer(context: Context) : OsmandMapLayer(context) {

    private var facilityTileProvider: FacilityTileProvider? = null
    private val markers = MarkerDataConstants.getAllMarkers()

    override fun initLayer(view: OsmandMapTileView) {
        super.initLayer(view)
    }


    /**
     * IMPORTANT: Initialize provider only once in onPrepareBufferImage
     * Native engine will automatically render and update position on pan/zoom
     */
    override fun onPrepareBufferImage(
        canvas: Canvas?,
        tileBox: RotatedTileBox?,
        settings: DrawSettings?
    ) {
        super.onPrepareBufferImage(canvas, tileBox, settings)

        val mapRenderer = getMapRenderer()
        if (mapRenderer == null) {
            // No OpenGL renderer - fallback to Canvas (see code below)
            return
        }

        // Initialize only once
        if (facilityTileProvider == null) {
            val textScale = getTextScale()
            val density = view?.density ?: 1.0f
            val textStyle = MapTextLayer.getTextStyle(
                context,
                settings?.isNightMode == true,
                textScale,
                density
            ) ?: TextRasterizer.Style()

            facilityTileProvider = FacilityTileProvider(
                context,
                markers,
                getPointsOrder(),
                true, // textVisible
                textStyle,
                textScale,
                density
            )

            // Add to map renderer - ONLY ONCE
            facilityTileProvider?.drawSymbols(mapRenderer)
        }
    }

    /**
     * onDraw() - Leave empty because native engine renders automatically
     */
    override fun onDraw(
        canvas: Canvas?,
        tileBox: RotatedTileBox?,
        settings: DrawSettings?
    ) {
        // Native engine automatically renders markers
        // No need to draw anything here
    }

    override fun drawInScreenPixels(): Boolean {
        return true
    }

    override fun destroyLayer() {
        super.destroyLayer()
        val mapRenderer = getMapRenderer()
        facilityTileProvider?.clearSymbols(mapRenderer!!)
        facilityTileProvider = null
    }
}

/**
 * Tile Provider for Facility Markers
 * Extends interface_MapTiledCollectionProvider similar to FavoritesTileProvider
 */
class FacilityTileProvider(
    private val context: Context,
    private val markers: List<*>, // List from MarkerDataConstants
    private val baseOrder: Int,
    private val textVisible: Boolean,
    private val textStyle: TextRasterizer.Style,
    private val textScale: Float,
    private val density: Float
) : interface_MapTiledCollectionProvider() {

    private val points31 = QListPointI()
    private val mapLayerDataList = mutableListOf<FacilityMapLayerData>()
    private val bigBitmapCache = ConcurrentHashMap<Long, Bitmap>()
    private val smallBitmapCache = ConcurrentHashMap<Long, Bitmap>()
    private val offset = PointI(0, 0)
    private var providerInstance: MapTiledCollectionProvider? = null

    init {
        // Convert all markers to PointI and store data
        for (marker in markers) {
            try {
                val latField = marker?.javaClass?.getDeclaredField("latitude")
                latField?.isAccessible = true
                val lat = latField?.get(marker) as? Double ?: continue

                val lonField = marker.javaClass.getDeclaredField("longitude")
                lonField.isAccessible = true
                val lon = lonField.get(marker) as? Double ?: continue

                var name: String? = null
                try {
                    val nameField = marker.javaClass.getDeclaredField("name")
                    nameField.isAccessible = true
                    name = nameField.get(marker) as? String
                } catch (_: Exception) {
                }

                var facilityKind: String? = null
                try {
                    val kindField = marker.javaClass.getDeclaredField("facilityKind")
                    kindField.isAccessible = true
                    facilityKind = kindField.get(marker) as? String
                } catch (_: Exception) {
                }

                // Convert lat/lon to PointI (31-bit)
                val x31 = MapUtils.get31TileNumberX(lon)
                val y31 = MapUtils.get31TileNumberY(lat)
                points31.add(PointI(x31, y31))

                // Store data for marker
                mapLayerDataList.add(
                    FacilityMapLayerData(
                        lat = lat,
                        lon = lon,
                        name = name ?: "",
                        facilityKind = facilityKind ?: "facility"
                    )
                )
            } catch (_: Exception) {
                // Skip invalid markers
            }
        }
    }

    fun drawSymbols(mapRenderer: MapRendererView) {
        if (providerInstance == null) {
            providerInstance = instantiateProxy()
        }
        // Add to CUSTOM_SYMBOL_SECTION or create new section
        // Use section similar to FAVORITES_SECTION
        mapRenderer.addSymbolsProvider(
            MapRendererContext.FAVORITES_SECTION, // Or create separate section
            providerInstance
        )
    }

    fun clearSymbols(mapRenderer: MapRendererView) {
        providerInstance?.let {
            mapRenderer.removeSymbolsProvider(it)
            providerInstance = null
        }
    }

    override fun getBaseOrder(): Int {
        return baseOrder
    }

    override fun getPoints31(): QListPointI {
        return points31
    }

    override fun getHiddenPoints(): QListPointI {
        return QListPointI()
    }

    override fun shouldShowCaptions(): Boolean {
        return textVisible
    }

    override fun getCaptionStyle(): TextRasterizer.Style {
        return textStyle
    }

    override fun getCaptionTopSpace(): Double {
        return -4.0 * density
    }

    override fun getReferenceTileSizeOnScreenInPixels(): Float {
        return 256f
    }

    override fun getScale(): Double {
        return 1.0
    }

    override fun getImageBitmap(index: Int, isFullSize: Boolean): SingleSkImage {
        if (index >= mapLayerDataList.size) {
            return SwigUtilities.nullSkImage()
        }

        val data = mapLayerDataList[index]
        val key = data.getKey()

        val bitmap = if (isFullSize) {
            bigBitmapCache.getOrPut(key) {
                createMarkerBitmap(data, true)
            }
        } else {
            smallBitmapCache.getOrPut(key) {
                createMarkerBitmap(data, false)
            }
        }

        return bitmap?.let { NativeUtilities.createSkImageFromBitmap(it) }
            ?: SwigUtilities.nullSkImage()
    }

    @SuppressLint("DiscouragedApi")
    private fun createMarkerBitmap(data: FacilityMapLayerData, isFullSize: Boolean): Bitmap? {
        val iconName = getIconNameForFacilityKind(data.facilityKind)
        if (iconName == null) return null

        val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
        if (resId == 0) return null

        val drawable = ContextCompat.getDrawable(context, resId) ?: return null
        val size = if (isFullSize) 48 else 24 // Adjust sizes as needed

        return createBitmap(size, size).apply {
            val canvas = Canvas(this)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
    }

    override fun getCaption(index: Int): String {
        if (index >= mapLayerDataList.size) {
            return ""
        }
        return mapLayerDataList[index].name
    }

    override fun getTilePoints(tileId: TileId, zoom: ZoomLevel): QListMapTiledCollectionPoint {
        // Return empty - use getPoints31() instead of tile-based
        return QListMapTiledCollectionPoint()
    }

    override fun getMinZoom(): ZoomLevel {
        return ZoomLevel.ZoomLevel6
    }

    override fun getMaxZoom(): ZoomLevel {
        return ZoomLevel.MaxZoomLevel
    }

    override fun supportsNaturalObtainDataAsync(): Boolean {
        return false
    }

    override fun getPinIconVerticalAlignment(): MapMarker.PinIconVerticalAlignment {
        return MapMarker.PinIconVerticalAlignment.Top
    }

    override fun getPinIconHorisontalAlignment(): MapMarker.PinIconHorisontalAlignment {
        return MapMarker.PinIconHorisontalAlignment.CenterHorizontal
    }

    override fun getPinIconOffset(): PointI {
        return offset
    }

    private fun getIconNameForFacilityKind(facilityKind: String): String? {
        return when (facilityKind.lowercase()) {
            "hotel", "inn" -> "hotel"
            "restaurant" -> "restaurant"
            "facility" -> "facility"
            "shop", "shopping" -> "shop"
            "beach", "swim" -> "beach"
            "lift" -> "lift"
            "golf" -> "golf"
            "parking" -> "parking"
            "chapel" -> "chapel"
            "amusement" -> "facility"
            "blocked" -> "blocked"
            "arrow" -> "arrow"
            else -> null
        }
    }

    private data class FacilityMapLayerData(
        val lat: Double,
        val lon: Double,
        val name: String,
        val facilityKind: String
    ) {
        fun getKey(): Long {
            return (lat * 1000000).toLong() + (lon * 1000).toLong() + facilityKind.hashCode()
                .toLong()
        }
    }
}

