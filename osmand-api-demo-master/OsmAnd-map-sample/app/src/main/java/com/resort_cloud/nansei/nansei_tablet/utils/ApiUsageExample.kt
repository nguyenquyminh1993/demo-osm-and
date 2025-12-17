package com.resort_cloud.nansei.nansei_tablet.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityResponse
import com.resort_cloud.nansei.nansei_tablet.data.repository.FacilityRepository

/**
 * Example ViewModel showing how to use the FacilityRepository
 * This is just a reference implementation
 */
class FacilityViewModel : ViewModel() {
    
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

/**
 * Example usage in an Activity or Fragment
 */
object ApiUsageExample {
    
    /**
     * Example 1: Using in a coroutine scope
     */
    suspend fun exampleUsage1() {
        val repository = FacilityRepository()
        val result = repository.getFacilities()
        
        result.onSuccess { response ->
            // Process the response
            val inns = response.payload.facilityKinds.find { it.facilityKind == "inn" }
            inns?.facilities?.forEach { facility ->
                println("Inn: ${facility.listData.name}")
            }
        }
    }
    
    /**
     * Example 2: Filtering facilities by type
     */
    fun filterFacilitiesByType(response: FacilityResponse, type: String) {
        val filteredFacilities = response.payload.facilityKinds
            .filter { it.facilityKind == type }
            .flatMap { it.facilities }
        
        filteredFacilities.forEach { facility ->
            println("${facility.listData.name} - ${facility.routeInfo}")
        }
    }
    
    /**
     * Example 3: Getting all visible facilities
     */
    fun getVisibleFacilities(response: FacilityResponse) {
        val visibleFacilities = response.payload.facilityKinds
            .flatMap { it.facilities }
            .filter { it.listData.visible }
        
        println("Total visible facilities: ${visibleFacilities.size}")
    }
}
