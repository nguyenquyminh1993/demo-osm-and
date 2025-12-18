package com.resort_cloud.nansei.nansei_tablet.data.repository

import android.content.Context
import com.google.gson.Gson
import com.resort_cloud.nansei.nansei_tablet.data.api.RetrofitClient
import com.resort_cloud.nansei.nansei_tablet.data.model.ErrorResponse
import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityResponse
import com.resort_cloud.nansei.nansei_tablet.utils.FileStorageUtils
import com.resort_cloud.nansei.nansei_tablet.utils.NetworkUtils
import retrofit2.HttpException
import retrofit2.Response

/**
 * Repository for facility data
 * Handles data operations and provides a clean API for the UI layer
 * Supports offline mode with local cache
 */
class FacilityRepository(private val context: Context) {

    private val apiService = RetrofitClient.facilityApiService
    private val gson = Gson()

    /**
     * Fetch facilities from the API or cache
     * - If network available: Call API, save to cache, return data
     * - If network unavailable: Load from cache file
     * Returns Result with FacilityResponse on success or ApiException on error
     */
    suspend fun getFacilities(): Result<FacilityResponse> {
        return if (NetworkUtils.isNetworkAvailable(context)) {
            // Has network: Call API
            fetchFromApi()
        } else {
            // No network: Load from cache
            loadFromCache()
        }
    }
    
    /**
     * Fetch facilities from API and save to cache
     */
    private suspend fun fetchFromApi(): Result<FacilityResponse> {
        return try {
            val response: Response<FacilityResponse> = apiService.getFacilities()

            if (response.isSuccessful && response.body() != null) {
                val facilityResponse = response.body()!!
                
                // Save to cache file
                val saved = FileStorageUtils.saveFacilities(context, facilityResponse)
                if (saved) {
                    android.util.Log.d("FacilityRepository", "Cache saved to: ${FileStorageUtils.getCacheFilePath(context)}")
                }
                
                Result.success(facilityResponse)
            } else {
                val errorMessage = parseErrorResponse(response) ?: ""
                
                // If API fails but we have cached data, return cached data
                val cachedData = FileStorageUtils.loadFacilities(context)
                if (cachedData != null) {
                    Result.success(cachedData)
                } else {
                    Result.failure(ApiException(errorMessage, response.code()))
                }
            }
        } catch (e: Exception) {
            // Network error: Try to load from cache
            val cachedData = FileStorageUtils.loadFacilities(context)
            if (cachedData != null) {
                Result.success(cachedData)
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Load facilities from cache file
     */
    private fun loadFromCache(): Result<FacilityResponse> {
        val cachedData = FileStorageUtils.loadFacilities(context)
        return if (cachedData != null) {
            Result.success(cachedData)
        } else {
            Result.failure(ApiException(null, null))
        }
    }

    /**
     * Parse error response from Response
     */
    private fun parseErrorResponse(response: Response<*>): String? {
        return try {
            val errorBody = response.errorBody()?.string()
            if (errorBody != null) {
                val errorResponse = gson.fromJson(errorBody, ErrorResponse::class.java)
                errorResponse.errorMsg
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

}

/**
 * Custom exception for API errors
 * Contains HTTP status code and original exception if available
 */
class ApiException(
    message: String?,
    val statusCode: Int? = null,
) : Exception(message)
