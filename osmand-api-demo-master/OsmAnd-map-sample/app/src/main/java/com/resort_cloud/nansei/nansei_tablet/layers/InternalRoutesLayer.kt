package com.resort_cloud.nansei.nansei_tablet.layers

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import com.resort_cloud.nansei.nansei_tablet.utils.GpxParser
import net.osmand.core.android.MapRendererView
import net.osmand.core.jni.PointI
import net.osmand.core.jni.QVectorPointI
import net.osmand.core.jni.VectorLine
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
    private var linesCollection: VectorLinesCollection? = null

    override fun initLayer(view: OsmandMapTileView) {
        super.initLayer(view)
        loadGpxTracks()
    }

    private fun loadGpxTracks() {
        try {
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
        if (linesCollection == null && tracks.isNotEmpty()) {
            buildVectorLines(mapRenderer)
        }
    }

    private fun buildVectorLines(mapRenderer: MapRendererView) {
        try {
            val collection = VectorLinesCollection()
            var lineId = 0
            val baseOrder = getBaseOrder()

            // Orange color (ARGB format)
            val orangeColor = NativeUtilities.createFColorARGB(0xFFFF6B35.toInt())
            // White outline
            val whiteColor = NativeUtilities.createFColorARGB(0xAAFFFFFF.toInt())

            for (track in tracks) {
                for (segment in track.segments) {
                    if (segment.points.size < 2) continue

                    // Convert points to 31-bit coordinates
                    val points31 = QVectorPointI()
                    for (point in segment.points) {
                        val x31 = MapUtils.get31TileNumberX(point.lon)
                        val y31 = MapUtils.get31TileNumberY(point.lat)
                        points31.add(PointI(x31, y31))
                    }

                    // Create VectorLine
                    val builder = VectorLineBuilder()
                    builder.setBaseOrder(baseOrder)
                        .setIsHidden(false)
                        .setLineId(lineId++)
                        .setLineWidth(8.0) // Main line width
                        .setOutlineWidth(2.0) // Outline width
                        .setPoints(points31)
                        .setFillColor(orangeColor)
                        .setOutlineColor(whiteColor)

                    builder.buildAndAddToCollection(collection)
                }
            }

            // Add to map renderer
            mapRenderer.addSymbolsProvider(collection)
            linesCollection = collection

            Log.d(TAG, "✅ Added ${lineId} vector lines to map renderer")
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
        if (mapRenderer != null && linesCollection != null) {
            mapRenderer.removeSymbolsProvider(linesCollection)
        }

        linesCollection = null
        tracks.clear()
    }

    fun reloadTracks() {
        val mapRenderer = getMapRenderer()
        if (mapRenderer != null && linesCollection != null) {
            mapRenderer.removeSymbolsProvider(linesCollection)
        }

        linesCollection = null
        loadGpxTracks()

        if (mapRenderer != null && tracks.isNotEmpty()) {
            buildVectorLines(mapRenderer)
        }

        view?.refreshMap()
    }
}
