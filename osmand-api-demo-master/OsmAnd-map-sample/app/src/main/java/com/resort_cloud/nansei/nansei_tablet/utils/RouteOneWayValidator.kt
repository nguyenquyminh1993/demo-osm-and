package com.resort_cloud.nansei.nansei_tablet.utils

import android.util.Log
import net.osmand.plus.OsmandApplication
import net.osmand.plus.routing.RoutingHelper
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.binary.RouteDataObject
import net.osmand.router.RouteSegmentResult

/**
 * Utility class to validate routes and prevent pedestrian mode from going against one-way roads
 * Similar to how car and bicycle modes respect one-way restrictions
 */
object RouteOneWayValidator {
    
    private const val TAG = "RouteOneWayValidator"
    
    /**
     * Validate if the calculated route respects one-way restrictions for pedestrian mode
     * @param app OsmandApplication instance
     * @param routingHelper RoutingHelper instance
     * @return true if route is valid (respects one-way), false if route violates one-way restrictions
     */
    fun validateRouteForOneWay(
        app: OsmandApplication?,
        routingHelper: RoutingHelper?
    ): Boolean {
        if (app == null || routingHelper == null) {
            return true // Cannot validate, allow route
        }
        
        // Only validate for pedestrian mode
        if (routingHelper.appMode != ApplicationMode.PEDESTRIAN) {
            return true // Other modes are handled by OsmAnd library
        }
        
        if (!routingHelper.isRouteCalculated) {
            return true // No route to validate
        }
        
        try {
            // Get route segments from routing helper
            val route = routingHelper.route

            // Access route segments using reflection if needed
            val routeSegments = getRouteSegments(route)
            if (routeSegments.isEmpty()) {
                return true
            }
            
            // Check each segment in the route
            for (i in routeSegments.indices) {
                val segment = routeSegments[i]
                val road = segment.getObject() ?: continue

                // Get one-way direction: -1 backward, 0 two-way, 1 forward
                val oneway = getOneWayDirection(road)
                
                if (oneway != 0) { // If it's a one-way road
                    // Check if we're going in the correct direction
                    val isGoingForward = isSegmentGoingForward(segment, routeSegments)
                    
                    if (oneway == 1 && !isGoingForward) {
                        // One-way forward but going backward
                        Log.w(TAG, "Route violates one-way restriction: going backward on forward-only road")
                        return false
                    } else if (oneway == -1 && isGoingForward) {
                        // One-way backward but going forward
                        Log.w(TAG, "Route violates one-way restriction: going forward on backward-only road")
                        return false
                    }
                }
            }
            
            return true // Route is valid
        } catch (e: Exception) {
            Log.e(TAG, "Error validating route for one-way restrictions", e)
            return true // On error, allow route (fail-safe)
        }
    }
    
    /**
     * Get route segments from route object using reflection
     */
    private fun getRouteSegments(route: Any): List<RouteSegmentResult> {
        return try {
            // Try to get routeSegments field
            val routeSegmentsField = route.javaClass.getDeclaredField("routeSegments")
            routeSegmentsField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            routeSegmentsField.get(route) as? List<RouteSegmentResult> ?: emptyList()
        } catch (_: Exception) {
            try {
                // Try alternative field name
                val segmentsField = route.javaClass.getDeclaredField("segments")
                segmentsField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                segmentsField.get(route) as? List<RouteSegmentResult> ?: emptyList()
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot access route segments", ex)
                emptyList()
            }
        }
    }
    
    /**
     * Get one-way direction from RouteDataObject
     * Returns: -1 backward, 0 two-way, 1 forward
     */
    private fun getOneWayDirection(road: RouteDataObject): Int {
        return try {
            // Try to use public API first
            val onewayMethod = road.javaClass.getMethod("getOneway")
            onewayMethod.invoke(road) as? Int ?: 0
        } catch (_: Exception) {
            try {
                // Fallback: use reflection to access oneway field
                val onewayField = road.javaClass.getDeclaredField("oneway")
                onewayField.isAccessible = true
                onewayField.getInt(road)
            } catch (_: Exception) {
                0 // Default to two-way if cannot determine
            }
        }
    }
    
    /**
     * Determine if segment is going in forward direction based on route context
     */
    private fun isSegmentGoingForward(
        segment: RouteSegmentResult,
        allSegments: List<RouteSegmentResult>
    ): Boolean {
        if (allSegments.size < 2) {
            return true // Cannot determine, assume forward
        }
        
        try {
            segment.getObject() ?: return true
            
            // Get start and end point indices of this segment
            val segmentStart = segment.startPointIndex
            val segmentEnd = segment.endPointIndex
            
            // Determine direction based on point indices
            // If segmentEnd > segmentStart, we're going forward along the road
            return segmentEnd > segmentStart
        } catch (e: Exception) {
            Log.e(TAG, "Error determining segment direction", e)
            return true // Default to forward
        }
    }
    
    /**
     * Find problematic one-way segments in the route
     * Returns list of segment indices that violate one-way restrictions
     */
    fun findOneWayViolations(
        app: OsmandApplication?,
        routingHelper: RoutingHelper?
    ): List<Int> {
        val violations = mutableListOf<Int>()
        
        if (app == null || routingHelper == null) {
            return violations
        }
        
        // Only validate for pedestrian mode
        if (routingHelper.appMode != ApplicationMode.PEDESTRIAN) {
            return violations
        }
        
        if (!routingHelper.isRouteCalculated) {
            return violations
        }
        
        try {
            val route = routingHelper.route
            val routeSegments = getRouteSegments(route)
            
            for (i in routeSegments.indices) {
                val segment = routeSegments[i]
                val road = segment.getObject() ?: continue
                
                val oneway = getOneWayDirection(road)
                
                if (oneway != 0) {
                    val isGoingForward = isSegmentGoingForward(segment, routeSegments)
                    
                    if ((oneway == 1 && !isGoingForward) || (oneway == -1 && isGoingForward)) {
                        violations.add(i)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding one-way violations", e)
        }
        
        return violations
    }
    
    /**
     * Get problematic road IDs that should be avoided
     * Returns list of road IDs that violate one-way restrictions
     */
    fun getRoadsToAvoid(
        app: OsmandApplication?,
        routingHelper: RoutingHelper?
    ): List<Long> {
        val roadsToAvoid = mutableListOf<Long>()
        
        if (app == null || routingHelper == null) {
            return roadsToAvoid
        }
        
        if (routingHelper.appMode != ApplicationMode.PEDESTRIAN) {
            return roadsToAvoid
        }
        
        if (!routingHelper.isRouteCalculated) {
            return roadsToAvoid
        }
        
        try {
            val route = routingHelper.route
            val routeSegments = getRouteSegments(route)
            
            for (segment in routeSegments) {
                val road = segment.getObject() ?: continue
                val oneway = getOneWayDirection(road)
                
                if (oneway != 0) {
                    val isGoingForward = isSegmentGoingForward(segment, routeSegments)
                    
                    if ((oneway == 1 && !isGoingForward) || (oneway == -1 && isGoingForward)) {
                        roadsToAvoid.add(road.id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting roads to avoid", e)
        }
        
        return roadsToAvoid
    }
}

