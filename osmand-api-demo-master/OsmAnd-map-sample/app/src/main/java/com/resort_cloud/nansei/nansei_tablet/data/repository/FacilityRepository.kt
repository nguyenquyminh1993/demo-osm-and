package com.resort_cloud.nansei.nansei_tablet.data.repository

import com.google.gson.Gson
import com.resort_cloud.nansei.nansei_tablet.data.api.RetrofitClient
import com.resort_cloud.nansei.nansei_tablet.data.model.ErrorResponse
import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityResponse
import retrofit2.HttpException
import retrofit2.Response

/**
 * Repository for facility data
 * Handles data operations and provides a clean API for the UI layer
 */
class FacilityRepository {

    private val apiService = RetrofitClient.facilityApiService
    private val gson = Gson()

    /**
     * Fetch facilities from the API
     * Returns Result with FacilityResponse on success or ApiException on error
     */
    suspend fun getFacilities(): Result<FacilityResponse> {
        return try {
            val response: Response<FacilityResponse> = apiService.getFacilities()

            if (response.isSuccessful && response.body() != null) {
                // Try to parse as FacilityResponse first
                Result.success(response.body()!!)
            } else {
                val errorMessage =parseErrorResponse(response) ?:""

                Result.failure(ApiException(errorMessage, response.code()))
            }

        } catch (e: Exception) {
            // Network or other errors
            Result.failure(e)
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

    /**
     * Parse error from HttpException
     */
    private fun parseErrorFromHttpException(exception: HttpException): String? {
        return try {
            val errorBody = exception.response()?.errorBody()?.string()
            if (errorBody != null) {
                val errorResponse = gson.fromJson(errorBody, ErrorResponse::class.java)
                errorResponse.errorMsg
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Custom exception for API errors
 * Contains HTTP status code and original exception if available
 */
class ApiException(
    message: String,
    val statusCode: Int? = null,
) : Exception(message)
