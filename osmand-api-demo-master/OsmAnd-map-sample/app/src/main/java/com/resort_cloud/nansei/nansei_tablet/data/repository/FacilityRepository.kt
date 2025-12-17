package com.resort_cloud.nansei.nansei_tablet.data.repository

import com.resort_cloud.nansei.nansei_tablet.data.api.RetrofitClient
import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityResponse
import retrofit2.Response

/**
 * Repository for facility data
 * Handles data operations and provides a clean API for the UI layer
 */
class FacilityRepository {
    
    private val apiService = RetrofitClient.facilityApiService
    
    /**
     * Fetch facilities from the API
     */
    suspend fun getFacilities(): Result<FacilityResponse> {
        return try {
            val response: Response<FacilityResponse> = apiService.getFacilities()
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
