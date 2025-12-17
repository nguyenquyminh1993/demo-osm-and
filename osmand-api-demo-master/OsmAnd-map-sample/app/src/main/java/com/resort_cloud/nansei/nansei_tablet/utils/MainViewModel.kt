package com.resort_cloud.nansei.nansei_tablet.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityResponse
import com.resort_cloud.nansei.nansei_tablet.data.repository.FacilityRepository
import kotlinx.coroutines.launch

/**
 * Example ViewModel showing how to use the FacilityRepository
 * This is just a reference implementation
 */
class MainViewModel : ViewModel() {

    private val repository = FacilityRepository()

    /**
     * Example: Fetch facilities
     */
    fun loadFacilities() {
        viewModelScope.launch {
            val result = repository.getFacilities()
            result.onSuccess { response ->
                handleSuccess(response)
            }.onFailure { exception ->
                handleError(exception)
            }
        }
    }

    private fun handleSuccess(response: FacilityResponse) {
        // Handle successful response
        println("Success: ${response.success}")
        println("Timestamp: ${response.timestamp}")

        // Access facility kinds
        response.payload.facilityKinds.forEach { facilityKind ->
            println("Facility Kind: ${facilityKind.name} (${facilityKind.facilityKind})")

            // Access individual facilities
            facilityKind.facilities.forEach { facility ->
                println("  - ${facility.listData.name}")
                println("    Location: ${facility.markerData.latitude}, ${facility.markerData.longitude}")
            }
        }
    }

    private fun handleError(exception: Throwable) {
        // Handle error
        println("Error: ${exception.message}")
        exception.printStackTrace()
    }
}