package com.resort_cloud.nansei.nansei_tablet.utils

import android.content.Context
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * GPX Parser to read track data from GPX files
 */
class GpxParser {

    data class GpxTrack(
        val name: String,
        val segments: List<TrackSegment>
    )

    data class TrackSegment(
        val points: List<TrackPoint>
    )

    data class TrackPoint(
        val lat: Double,
        val lon: Double
    )

    companion object {
        private const val TAG = "GpxParser"

        /**
         * Parse GPX file from assets
         */
        fun parseGpxFromAssets(context: Context, fileName: String): List<GpxTrack> {
            val tracks = mutableListOf<GpxTrack>()
            
            try {
                val inputStream: InputStream = context.assets.open(fileName)
                val factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
                val parser = factory.newPullParser()
                parser.setInput(inputStream, "UTF-8")

                var eventType = parser.eventType
                var currentTrack: GpxTrack? = null
                var currentSegment: TrackSegment? = null
                val currentPoints = mutableListOf<TrackPoint>()
                val currentSegments = mutableListOf<TrackSegment>()
                var trackName = ""

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "trk" -> {
                                    // Start of a new track
                                    trackName = ""
                                    currentSegments.clear()
                                }
                                "name" -> {
                                    // Track name
                                    if (parser.next() == XmlPullParser.TEXT) {
                                        trackName = parser.text
                                    }
                                }
                                "trkseg" -> {
                                    // Start of a new track segment
                                    currentPoints.clear()
                                }
                                "trkpt" -> {
                                    // Track point
                                    val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                    val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                    
                                    if (lat != null && lon != null) {
                                        currentPoints.add(TrackPoint(lat, lon))
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "trkseg" -> {
                                    // End of track segment
                                    if (currentPoints.isNotEmpty()) {
                                        currentSegments.add(TrackSegment(currentPoints.toList()))
                                    }
                                }
                                "trk" -> {
                                    // End of track
                                    if (currentSegments.isNotEmpty()) {
                                        tracks.add(GpxTrack(trackName, currentSegments.toList()))
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }

                inputStream.close()
                Log.d(TAG, "Parsed ${tracks.size} tracks from $fileName")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing GPX file: $fileName", e)
            }

            return tracks
        }
    }
}
