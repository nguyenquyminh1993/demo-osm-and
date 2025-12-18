package com.resort_cloud.nansei.nansei_tablet.utils

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityKind
import com.resort_cloud.nansei.nansei_tablet.data.model.FacilityResponse
import com.resort_cloud.nansei.nansei_tablet.data.repository.FacilityRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for MainActivity
 * Handles facility data loading and error management
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FacilityRepository(application.applicationContext)
    
    // Facility kinds data
    private val _facilityKinds = MutableLiveData<List<FacilityKind>>(emptyList())
    val facilityKinds: LiveData<List<FacilityKind>> = _facilityKinds

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Error handling - expose exception for ErrorHandler
    private val _facilityError = MutableLiveData<Throwable?>()
    val facilityError: LiveData<Throwable?> = _facilityError
    
    /**
     * Load facilities from API
     * Results are exposed through LiveData
     */
    fun loadFacilities() {
        if (_isLoading.value == true) {
            return
        }

        viewModelScope.launch {
            _isLoading.postValue(true)
            _facilityError.postValue(null)
            val result = repository.getFacilities()
            result.onSuccess { response ->
                handleSuccess(response)
            }.onFailure { exception ->
                handleError(exception)
            }
            _isLoading.postValue(false)
        }
    }
    
    /**
     * Handle successful API response
     */
    private fun handleSuccess(response: FacilityResponse) {
        _facilityKinds.postValue(response.payload.facilityKinds)
    }
    
    /**
     * Handle API error
     * Error is exposed through LiveData for UI to handle
     */
    private fun handleError(exception: Throwable) {
        _facilityError.postValue(exception)
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _facilityError.postValue(null)
    }
}