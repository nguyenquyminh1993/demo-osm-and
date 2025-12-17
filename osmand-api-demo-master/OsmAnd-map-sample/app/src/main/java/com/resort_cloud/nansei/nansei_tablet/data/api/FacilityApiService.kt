package com.resort_cloud.nansei.nansei_tablet.data.api

import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API service interface for facility endpoints
 */
interface FacilityApiService {
    
    /**
     * Get facilities data
     * Example endpoint - adjust the path according to your actual API
     */
    @GET("/api/v1/route/tablet/facility_shigira")
    suspend fun getFacilities(): Response<FacilityResponse>
    
    // Add more API endpoints as needed
}
