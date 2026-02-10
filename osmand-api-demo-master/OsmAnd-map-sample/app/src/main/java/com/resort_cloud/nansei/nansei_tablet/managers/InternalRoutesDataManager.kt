package com.resort_cloud.nansei.nansei_tablet.managers

import android.content.Context
import android.util.Log
import com.resort_cloud.nansei.nansei_tablet.services.OsmDataService
import com.resort_cloud.nansei.nansei_tablet.utils.GpxParser
import com.resort_cloud.nansei.nansei_tablet.utils.OsmParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manager to preload and cache internal routes data
 * This allows data to be prepared before map is ready, reducing visible loading time
 */
class InternalRoutesDataManager private constructor() {
    
    companion object {
        private const val TAG = "InternalRoutesDataManager"
        
        @Volatile
        private var instance: InternalRoutesDataManager? = null
        
        fun getInstance(): InternalRoutesDataManager {
            return instance ?: synchronized(this) {
                instance ?: InternalRoutesDataManager().also { instance = it }
            }
        }
    }
    
    // Cached tracks data
    private val tracks = mutableListOf<GpxParser.GpxTrack>()
    
    // Loading state
    @Volatile
    private var isLoading = false
    
    @Volatile
    private var isLoaded = false
    
    private var loadJob: Job? = null
    
    // Callbacks for when data is ready
    private val dataReadyCallbacks = mutableListOf<() -> Unit>()
    
    /**
     * Preload routes data in background
     * This should be called early (e.g., in Activity onCreate)
     */
    fun preloadData(context: Context) {
        if (isLoaded || isLoading) {
            Log.d(TAG, "Data already loaded or loading, skipping preload")
            return
        }
        
        isLoading = true
        Log.d(TAG, "Starting data preload...")
        
        loadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize services
                val osmDataService = OsmDataService(context)
                val osmParser = OsmParser()
                
                // Fetch OSM data (online or offline)
                val osmXml = osmDataService.fetchOsmData()
                
                if (osmXml != null) {
                    // Parse OSM XML
                    val osmData = osmParser.parseOsmXml(osmXml)
                    
                    // Convert to polylines
                    val polylines = osmParser.waysToPolylines(osmData)
                    
                    if (polylines.isNotEmpty()) {
                        // Convert to GPX track format
                        val newTracks = mutableListOf<GpxParser.GpxTrack>()
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
                            
                            newTracks.add(track)
                        }
                        
                        // Update cached tracks
                        synchronized(tracks) {
                            tracks.clear()
                            tracks.addAll(newTracks)
                        }
                        
                        Log.d(TAG, "✅ Preloaded ${tracks.size} OSM tracks")
                    } else {
                        Log.w(TAG, "No OSM polylines found during preload")
                    }
                } else {
                    Log.w(TAG, "No OSM data available during preload")
                }
                
                isLoaded = true
                isLoading = false
                
                // Notify callbacks on main thread
                withContext(Dispatchers.Main) {
                    notifyDataReady()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading OSM tracks", e)
                isLoading = false
            }
        }
    }
    
    /**
     * Get cached tracks (thread-safe)
     * Returns empty list if data not ready yet
     */
    fun getTracks(): List<GpxParser.GpxTrack> {
        return synchronized(tracks) {
            tracks.toList()
        }
    }
    
    /**
     * Check if data is ready
     */
    fun isDataReady(): Boolean = isLoaded
    
    /**
     * Register callback to be notified when data is ready
     * If data is already ready, callback is invoked immediately
     */
    fun onDataReady(callback: () -> Unit) {
        if (isLoaded) {
            callback()
        } else {
            synchronized(dataReadyCallbacks) {
                dataReadyCallbacks.add(callback)
            }
        }
    }
    
    /**
     * Notify all registered callbacks that data is ready
     */
    private fun notifyDataReady() {
        synchronized(dataReadyCallbacks) {
            dataReadyCallbacks.forEach { it() }
            dataReadyCallbacks.clear()
        }
    }
    
    /**
     * Clear cached data and cancel loading
     */
    fun clear() {
        loadJob?.cancel()
        synchronized(tracks) {
            tracks.clear()
        }
        synchronized(dataReadyCallbacks) {
            dataReadyCallbacks.clear()
        }
        isLoaded = false
        isLoading = false
        Log.d(TAG, "Data cleared")
    }
    
    /**
     * Force reload data
     */
    fun reload(context: Context) {
        clear()
        preloadData(context)
    }
}
