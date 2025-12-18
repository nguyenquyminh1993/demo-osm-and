package com.resort_cloud.nansei.nansei_tablet.utils

import android.content.Context
import com.google.gson.Gson
import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityResponse
import java.io.File
import java.io.FileReader
import java.io.FileWriter

/**
 * Utility class for file storage operations
 * Handles saving and loading JSON data to/from internal storage
 */
object FileStorageUtils {
    
    private const val FACILITIES_FILE_NAME = "facilities_cache.json"
    private val gson = Gson()
    
    /**
     * Get the cache file path
     */
    private fun getCacheFile(context: Context): File {
        val cacheDir = context.filesDir
        return File(cacheDir, FACILITIES_FILE_NAME)
    }
    
    /**
     * Get the cache file path as string (for debugging/logging)
     */
    fun getCacheFilePath(context: Context): String {
        return getCacheFile(context).absolutePath
    }
    
    /**
     * Save FacilityResponse to JSON file
     * Deletes existing file before writing to prevent duplicate data
     */
    fun saveFacilities(context: Context, response: FacilityResponse): Boolean {
        return try {
            val file = getCacheFile(context)
            
            // Delete existing file if exists
            if (file.exists()) {
                file.delete()
            }
            
            // Write new data
            val json = gson.toJson(response)
            FileWriter(file, false).use { writer ->
                writer.write(json)
                writer.flush()
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Load FacilityResponse from JSON file
     */
    fun loadFacilities(context: Context): FacilityResponse? {
        return try {
            val file = getCacheFile(context)
            if (!file.exists()) {
                return null
            }
            
            FileReader(file).use { reader ->
                gson.fromJson(reader, FacilityResponse::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Check if cached file exists
     */
    fun hasCachedData(context: Context): Boolean {
        return getCacheFile(context).exists()
    }
    
    /**
     * Delete cached file
     */
    fun clearCache(context: Context): Boolean {
        return try {
            val file = getCacheFile(context)
            if (file.exists()) {
                file.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

