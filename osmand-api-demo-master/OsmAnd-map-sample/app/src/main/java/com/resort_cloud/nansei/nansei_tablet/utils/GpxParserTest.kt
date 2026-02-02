package com.resort_cloud.nansei.nansei_tablet.utils

import android.content.Context
import android.util.Log

/**
 * Test utility to verify GPX parsing
 */
object GpxParserTest {
    
    fun testParsing(context: Context) {
        try {
            val tracks = GpxParser.parseGpxFromAssets(context, "data_internal.gpx")
            
            Log.d("GpxParserTest", "=== GPX Parsing Test ===")
            Log.d("GpxParserTest", "Total tracks: ${tracks.size}")
            
            var totalSegments = 0
            var totalPoints = 0
            
            tracks.forEachIndexed { index, track ->
                val segmentCount = track.segments.size
                val pointCount = track.segments.sumOf { it.points.size }
                
                totalSegments += segmentCount
                totalPoints += pointCount
                
                if (index < 5) { // Show first 5 tracks
                    Log.d("GpxParserTest", "Track $index: ${track.name}")
                    Log.d("GpxParserTest", "  - Segments: $segmentCount")
                    Log.d("GpxParserTest", "  - Points: $pointCount")
                    
                    if (track.segments.isNotEmpty() && track.segments[0].points.isNotEmpty()) {
                        val firstPoint = track.segments[0].points[0]
                        Log.d("GpxParserTest", "  - First point: (${firstPoint.lat}, ${firstPoint.lon})")
                    }
                }
            }
            
            Log.d("GpxParserTest", "=== Summary ===")
            Log.d("GpxParserTest", "Total segments: $totalSegments")
            Log.d("GpxParserTest", "Total points: $totalPoints")
            Log.d("GpxParserTest", "Average points per track: ${totalPoints / tracks.size}")
            
        } catch (e: Exception) {
            Log.e("GpxParserTest", "Error testing GPX parsing", e)
        }
    }
}
