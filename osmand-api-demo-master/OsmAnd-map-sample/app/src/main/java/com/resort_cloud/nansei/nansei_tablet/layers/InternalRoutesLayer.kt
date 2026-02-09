package com.resort_cloud.nansei.nansei_tablet.layers

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import com.resort_cloud.nansei.nansei_tablet.utils.GpxParser
import kotlinx.coroutines.launch
import net.osmand.core.android.MapRendererView
import net.osmand.core.jni.PointI
import net.osmand.core.jni.QVectorPointI
import net.osmand.core.jni.VectorLineBuilder
import net.osmand.core.jni.VectorLinesCollection
import net.osmand.data.RotatedTileBox
import net.osmand.plus.utils.NativeUtilities
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.util.MapUtils

/**
 * Layer using VectorLine for smooth polyline rendering
 * Same approach as RouteLayer - no delay, auto rotation
 */
class InternalRoutesLayer(context: Context) : OsmandMapLayer(context) {

    private val TAG = "InternalRoutesLayer"
    private val tracks = mutableListOf<GpxParser.GpxTrack>()

    // Separate collections to ensure strict drawing order (Z-index)
    private var bgLinesCollection: VectorLinesCollection? = null // Blue background
    private var fgLinesCollection: VectorLinesCollection? = null // White dashed foreground

    override fun initLayer(view: OsmandMapTileView) {
        super.initLayer(view)
        loadOsmTracks()
    }

    /**
     * Load OSM tracks from API or cache (similar to GPX loading)
     */
    private fun loadOsmTracks() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Loading OSM tracks...")
                
                // Initialize services
                val osmDataService = com.resort_cloud.nansei.nansei_tablet.services.OsmDataService(context)
                val osmParser = com.resort_cloud.nansei.nansei_tablet.utils.OsmParser()
                
                // Fetch OSM data (online or offline)
                val osmXml = osmDataService.fetchOsmData()
                
                if (osmXml != null) {
                    // Parse OSM XML
                    val osmData = osmParser.parseOsmXml(osmXml)
                    
                    // Convert to polylines
                    val polylines = osmParser.waysToPolylines(osmData)
                    
                    if (polylines.isNotEmpty()) {
                        // Convert to GPX track format
                        tracks.clear()
                        for ((index, polyline) in polylines.withIndex()) {
                            if (polyline.isEmpty()) continue
                            
                            val trackPoints = polyline.map { (lat, lon) ->
                                GpxParser.TrackPoint(lat, lon)
                            }
                            
                            val segment = GpxParser.TrackSegment(trackPoints)
                            val track = GpxParser.GpxTrack(
                                name = "OSM Way $index",
                                segments = listOf(segment)
                            )
                            
                            tracks.add(track)
                        }
                        
                        Log.d(TAG, "✅ Loaded ${tracks.size} OSM tracks")
                    } else {
                        Log.w(TAG, "No OSM polylines found")
                    }
                } else {
                    Log.w(TAG, "No OSM data available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading OSM tracks", e)
            }
        }
    }
    
    private fun loadGpxTracks() {
        try {
            // Load standard GPX (lat/lon)
            val gpxTracks = GpxParser.parseGpxFromAssets(context, "data_internal.gpx")
            tracks.clear()
            tracks.addAll(gpxTracks)
            Log.d(TAG, "Loaded ${tracks.size} tracks from GPX file")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading GPX tracks", e)
        }
    }

    override fun onPrepareBufferImage(
        canvas: Canvas?,
        tileBox: RotatedTileBox?,
        settings: DrawSettings?
    ) {
        super.onPrepareBufferImage(canvas, tileBox, settings)

        val mapRenderer = getMapRenderer()
        if (mapRenderer == null) {
            Log.w(TAG, "MapRenderer not available")
            return
        }

        // Initialize VectorLines only once
        if (bgLinesCollection == null && tracks.isNotEmpty()) {
            buildVectorLines(mapRenderer)
        }
    }

    private fun buildVectorLines(mapRenderer: MapRendererView) {
        try {
            val bgCollection = VectorLinesCollection()
            val fgCollection = VectorLinesCollection()

            var lineId = 0
            val baseOrder = getBaseOrder()

            // Colors
            val whiteColor = NativeUtilities.createFColorARGB(0xFFFFFFFF.toInt())
            val blueColor = NativeUtilities.createFColorARGB(0xFF669AFF.toInt())

            // Dash Pattern: [Draw, Space, Draw, Space...]
            // Increased scale for better visibility at map zoom levels
            val dashPattern = net.osmand.core.jni.VectorDouble()
            dashPattern.add(100.0) // Draw 100 pixels
            dashPattern.add(100.0) // Space 100 pixels

            for (track in tracks) {
                for (segment in track.segments) {
                    if (segment.points.size < 2) continue

                    // Convert to native 31-bit coordinates
                    val points31 = QVectorPointI()
                    for (point in segment.points) {
                        val x31 = MapUtils.get31TileNumberX(point.lon)
                        val y31 = MapUtils.get31TileNumberY(point.lat)
                        points31.add(PointI(x31, y31))
                    }

                    // 2. BACKGROUND LINE (blue, Dashed, Narrower)
                    // Added to fgCollection (Strictly on top)
                    val bgBuilder = VectorLineBuilder()
                    bgBuilder.setBaseOrder(baseOrder + 10) // Ensure strictly higher order
                        .setIsHidden(false)
                        .setLineId(lineId++)
                        .setLineWidth(18.0) // Narrower blue line
                        .setPoints(points31)
                        .setFillColor(blueColor)

                    bgBuilder.buildAndAddToCollection(fgCollection)

                    // 1. FOREGROUND LINE (white, Solid, Wide)
                    // Added to bgCollection
                    val fgBuilder = VectorLineBuilder()
                    fgBuilder.setBaseOrder(baseOrder)
                        .setIsHidden(false)
                        .setLineId(lineId++)
                        .setLineWidth(6.0) // Wide white background
                        .setPoints(points31)
                        .setFillColor(whiteColor)
                        .setLineDash(dashPattern)

                    fgBuilder.buildAndAddToCollection(bgCollection)

                }
            }

            // Add collections to renderer in order
            // 1. Add Background first
            mapRenderer.addSymbolsProvider(bgCollection)
            bgLinesCollection = bgCollection

            // 2. Add Foreground second (Draws on top)
            mapRenderer.addSymbolsProvider(fgCollection)
            fgLinesCollection = fgCollection

            Log.d(TAG, "✅ Vector lines built: ${lineId / 2} segments rendered with dual-pass")
        } catch (e: Exception) {
            Log.e(TAG, "Error building vector lines", e)
        }
    }

    override fun onDraw(
        canvas: Canvas?,
        tileBox: RotatedTileBox?,
        settings: DrawSettings?
    ) {
        // Empty - native engine handles rendering
    }

    override fun drawInScreenPixels(): Boolean {
        return false
    }

    override fun destroyLayer() {
        super.destroyLayer()

        val mapRenderer = getMapRenderer()
        if (mapRenderer != null) {
            if (bgLinesCollection != null) mapRenderer.removeSymbolsProvider(bgLinesCollection)
            if (fgLinesCollection != null) mapRenderer.removeSymbolsProvider(fgLinesCollection)
        }

        bgLinesCollection = null
        fgLinesCollection = null
        tracks.clear()
    }

    fun reloadTracks() {
        val mapRenderer = getMapRenderer()
        if (mapRenderer != null) {
            if (bgLinesCollection != null) mapRenderer.removeSymbolsProvider(bgLinesCollection)
            if (fgLinesCollection != null) mapRenderer.removeSymbolsProvider(fgLinesCollection)
        }

        bgLinesCollection = null
        fgLinesCollection = null
        loadGpxTracks()

        if (mapRenderer != null && tracks.isNotEmpty()) {
            buildVectorLines(mapRenderer)
        }

        view?.refreshMap()
    }
}
