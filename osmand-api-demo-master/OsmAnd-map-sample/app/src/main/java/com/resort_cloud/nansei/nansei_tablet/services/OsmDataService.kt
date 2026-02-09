package com.resort_cloud.nansei.nansei_tablet.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service to fetch OSM data from Overpass API or load from offline cache
 */
class OsmDataService(private val context: Context) {
    
    companion object {
        private const val TAG = "OsmDataService"
        private const val OVERPASS_API_URL = "https://overpass-api.de/api/interpreter"
        private const val CACHE_FILE_NAME = "osm_golf_cart_routes.osm"
        
        // Overpass QL query for golf cart routes
        private const val OVERPASS_QUERY = """
            [out:xml][timeout:125];
            (
              way["highway"="service"]["golf_cart"="yes"](24.71,125.31,24.73,125.36);
            );
            (._;>;);
            out body;
        """
    }
    
    /**
     * Fetch OSM data - online if available, otherwise offline cache
     */
    suspend fun fetchOsmData(): String? = withContext(Dispatchers.IO) {
        try {
            // Check network availability
            if (isNetworkAvailable()) {
                Log.d(TAG, "Network available, fetching from Overpass API...")
                val onlineData = fetchFromOverpassAPI()
                if (onlineData != null) {
                    // Save to cache for offline use
                    saveToCache(onlineData)
                    Log.d(TAG, "✅ OSM data fetched and cached")
                    return@withContext onlineData
                }
            }
            
            // Fallback to offline cache
            Log.d(TAG, "Loading from offline cache...")
            val offlineData = loadFromCache()
            if (offlineData != null) {
                Log.d(TAG, "✅ OSM data loaded from cache")
                return@withContext offlineData
            }
            
            Log.w(TAG, "⚠️ No OSM data available (online or offline)")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching OSM data", e)
            // Try offline cache as fallback
            loadFromCache()
        }
    }
    
    /**
     * Fetch data from Overpass API
     */
    private fun fetchFromOverpassAPI(): String? {
        return try {
            val url = URL(OVERPASS_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            
            // Send query
            connection.outputStream.use { os ->
                val postData = "data=${java.net.URLEncoder.encode(OVERPASS_QUERY, "UTF-8")}"
                os.write(postData.toByteArray(Charsets.UTF_8))
            }
            
            // Read response
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.e(TAG, "Overpass API error: HTTP $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Overpass API", e)
            null
        }
    }
    
    /**
     * Save OSM data to cache file
     */
    private fun saveToCache(data: String) {
        try {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            FileOutputStream(cacheFile).use { fos ->
                fos.write(data.toByteArray(Charsets.UTF_8))
            }
            Log.d(TAG, "OSM data saved to cache: ${cacheFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to cache", e)
        }
    }
    
    /**
     * Load OSM data from cache file
     */
    private fun loadFromCache(): String? {
        return try {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) {
                cacheFile.readText(Charsets.UTF_8)
            } else {
                // Try loading from assets as fallback
                loadFromAssets()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from cache", e)
            loadFromAssets()
        }
    }
    
    /**
     * Load OSM data from assets (initial fallback)
     */
    private fun loadFromAssets(): String? {
        return try {
            context.assets.open("export.osm").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from assets", e)
            null
        }
    }
    
    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo
            activeNetwork?.isConnected == true
        } catch (e: Exception) {
            false
        }
    }
}
