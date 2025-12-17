package com.resort_cloud.nansei.nansei_tablet.data.model

import com.google.gson.annotations.SerializedName

/**
 * Main API Response wrapper
 */
data class FacilityResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("timestamp")
    val timestamp: String,
    
    @SerializedName("payload")
    val payload: Payload
)

/**
 * Payload containing all facility data
 */
data class Payload(
    @SerializedName("facility_kinds")
    val facilityKinds: List<FacilityKind>,
    
    @SerializedName("route_waypoints")
    val routeWaypoints: List<Any>, // Empty in the example, can be defined later if needed
    
    @SerializedName("current_position_route_reference_ids")
    val currentPositionRouteReferenceIds: List<CurrentPositionRouteReference>,
    
    @SerializedName("caveat_areas")
    val caveatAreas: List<Any>, // Empty in the example
    
    @SerializedName("entrances")
    val entrances: List<Any> // Empty in the example
)

/**
 * Facility kind grouping (e.g., inn, restaurant, facility, etc.)
 */
data class FacilityKind(
    @SerializedName("facility_kind")
    val facilityKind: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("facilities")
    val facilities: List<Facility>
)

/**
 * Individual facility data
 */
data class Facility(
    @SerializedName("facility_id")
    val facilityId: Int,
    
    @SerializedName("route_reference_id")
    val routeReferenceId: Int,
    
    @SerializedName("facility_kind")
    val facilityKind: String,
    
    @SerializedName("list_data")
    val listData: ListData,
    
    @SerializedName("marker_data")
    val markerData: MarkerData,
    
    @SerializedName("route_info")
    val routeInfo: String
)

/**
 * List display data for a facility
 */
data class ListData(
    @SerializedName("visible")
    val visible: Boolean,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("name_kana")
    val nameKana: String
)

/**
 * Marker data for map display
 */
data class MarkerData(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("latitude_dummy")
    val latitudeDummy: Double,
    
    @SerializedName("longitude_dummy")
    val longitudeDummy: Double,
    
    @SerializedName("label_style")
    val labelStyle: String,
    
    @SerializedName("contents")
    val contents: List<String>
)

/**
 * Route reference for current position
 */
data class CurrentPositionRouteReference(
    @SerializedName("route_reference_id")
    val routeReferenceId: Int,
    
    @SerializedName("latitude_center")
    val latitudeCenter: Double,
    
    @SerializedName("longitude_center")
    val longitudeCenter: Double,
    
    @SerializedName("latitude_delta_bottom")
    val latitudeDeltaBottom: Double,
    
    @SerializedName("latitude_delta_top")
    val latitudeDeltaTop: Double,
    
    @SerializedName("longitude_delta_left")
    val longitudeDeltaLeft: Double,
    
    @SerializedName("longitude_delta_right")
    val longitudeDeltaRight: Double
)
