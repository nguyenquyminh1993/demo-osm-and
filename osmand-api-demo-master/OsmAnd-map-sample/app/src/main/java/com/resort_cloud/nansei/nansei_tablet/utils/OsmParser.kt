package com.resort_cloud.nansei.nansei_tablet.utils

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Parser for OSM XML data to extract ways and nodes for polyline rendering
 */
class OsmParser {
    
    companion object {
        private const val TAG = "OsmParser"
    }
    
    data class OsmNode(
        val id: String,
        val lat: Double,
        val lon: Double
    )
    
    data class OsmWay(
        val id: String,
        val nodeRefs: List<String>,
        val tags: Map<String, String>
    )
    
    data class OsmData(
        val nodes: Map<String, OsmNode>,
        val ways: List<OsmWay>
    )
    
    /**
     * Parse OSM XML string and return structured data
     */
    fun parseOsmXml(xmlContent: String): OsmData {
        val nodes = mutableMapOf<String, OsmNode>()
        val ways = mutableListOf<OsmWay>()
        
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))
            
            var eventType = parser.eventType
            var currentWay: MutableList<String>? = null
            var currentWayId: String? = null
            var currentTags = mutableMapOf<String, String>()
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "node" -> {
                                val id = parser.getAttributeValue(null, "id")
                                val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                
                                if (id != null && lat != null && lon != null) {
                                    nodes[id] = OsmNode(id, lat, lon)
                                }
                            }
                            "way" -> {
                                currentWayId = parser.getAttributeValue(null, "id")
                                currentWay = mutableListOf()
                                currentTags = mutableMapOf()
                            }
                            "nd" -> {
                                val ref = parser.getAttributeValue(null, "ref")
                                if (ref != null) {
                                    currentWay?.add(ref)
                                }
                            }
                            "tag" -> {
                                val k = parser.getAttributeValue(null, "k")
                                val v = parser.getAttributeValue(null, "v")
                                if (k != null && v != null) {
                                    currentTags[k] = v
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "way" && currentWayId != null && currentWay != null) {
                            ways.add(OsmWay(currentWayId, currentWay, currentTags))
                            currentWay = null
                            currentWayId = null
                            currentTags = mutableMapOf()
                        }
                    }
                }
                eventType = parser.next()
            }
            
            Log.d(TAG, "Parsed ${nodes.size} nodes and ${ways.size} ways")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OSM XML", e)
        }
        
        return OsmData(nodes, ways)
    }
    
    /**
     * Convert OSM ways to polyline coordinates
     */
    fun waysToPolylines(osmData: OsmData): List<List<Pair<Double, Double>>> {
        val polylines = mutableListOf<List<Pair<Double, Double>>>()
        
        for (way in osmData.ways) {
            val coordinates = mutableListOf<Pair<Double, Double>>()
            
            for (nodeRef in way.nodeRefs) {
                val node = osmData.nodes[nodeRef]
                if (node != null) {
                    coordinates.add(Pair(node.lat, node.lon))
                }
            }
            
            if (coordinates.isNotEmpty()) {
                polylines.add(coordinates)
            }
        }
        
        Log.d(TAG, "Converted ${polylines.size} ways to polylines")
        return polylines
    }
    
    /**
     * Get all coordinates from all ways (flattened)
     */
    fun getAllCoordinates(osmData: OsmData): List<Pair<Double, Double>> {
        val allCoords = mutableListOf<Pair<Double, Double>>()
        
        for (way in osmData.ways) {
            for (nodeRef in way.nodeRefs) {
                val node = osmData.nodes[nodeRef]
                if (node != null) {
                    allCoords.add(Pair(node.lat, node.lon))
                }
            }
        }
        
        return allCoords
    }
}
