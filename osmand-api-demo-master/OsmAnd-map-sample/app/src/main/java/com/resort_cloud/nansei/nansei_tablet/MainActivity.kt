package com.resort_cloud.nansei.nansei_tablet

import android.annotation.SuppressLint
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.resort_cloud.nansei.nansei_tablet.dialog.SearchDestinationDialog
import com.resort_cloud.nansei.nansei_tablet.layers.FacilityMarkerLayer
import com.resort_cloud.nansei.nansei_tablet.layers.InternalRoutesLayer
import com.resort_cloud.nansei.nansei_tablet.utils.AlertManager
import com.resort_cloud.nansei.nansei_tablet.utils.ErrorHandler
import com.resort_cloud.nansei.nansei_tablet.utils.MainViewModel
import com.resort_cloud.nansei.nansei_tablet.utils.MapBoundsConstants
import com.resort_cloud.nansei.nansei_tablet.utils.NetworkUtils
import com.resort_cloud.nansei.nansei_tablet.utils.RouteOneWayValidator
import kotlinx.coroutines.launch
import net.osmand.IndexConstants
import net.osmand.Location
import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.data.ValueHolder
import net.osmand.map.ITileSource
import net.osmand.map.TileSourceManager
import net.osmand.plus.AppInitializeListener
import net.osmand.plus.AppInitializer
import net.osmand.plus.OsmAndLocationProvider
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.OsmandActionBarActivity
import net.osmand.plus.base.MapViewTrackingUtilities
import net.osmand.plus.download.DownloadActivityType
import net.osmand.plus.download.DownloadIndexesThread
import net.osmand.plus.download.DownloadIndexesThread.DownloadEvents
import net.osmand.plus.download.DownloadResources
import net.osmand.plus.download.DownloadValidationManager
import net.osmand.plus.download.IndexItem
import net.osmand.plus.helpers.AndroidUiHelper
import net.osmand.plus.helpers.ToastHelper
import net.osmand.plus.routing.IRouteInformationListener
import net.osmand.plus.routing.IRoutingDataUpdateListener
import net.osmand.plus.routing.NextDirectionInfo
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.backend.OsmandSettings
import net.osmand.plus.utils.AndroidUtils
import net.osmand.plus.utils.InsetTarget
import net.osmand.plus.utils.InsetsUtils
import net.osmand.plus.utils.InsetsUtils.InsetSide
import net.osmand.plus.utils.NativeUtilities
import net.osmand.plus.utils.OsmAndFormatter
import net.osmand.plus.views.MapViewWithLayers
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.views.layers.FavouritesLayer
import net.osmand.plus.views.layers.MapMarkersLayer
import net.osmand.plus.views.layers.MapTileLayer
import net.osmand.plus.views.layers.POIMapLayer
import net.osmand.plus.views.layers.TransportStopsLayer
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.max

class MainActivity : OsmandActionBarActivity(), AppInitializeListener, DownloadEvents,
    IRouteInformationListener {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var osmandApp: OsmandApplication
    private var mapTileView: OsmandMapTileView? = null
    private var mapViewWithLayers: MapViewWithLayers? = null
    private var clickListener: OsmandMapTileView.OnLongClickListener? = null
        get() {
            if (field == null) {
                field = OsmandMapTileView.OnLongClickListener { point: PointF ->
                    if (navigationActive) {
                        // Don't allow changing destination during navigation
                        return@OnLongClickListener false
                    }
                    val tileBox = mapTileView?.currentRotatedTileBox
                    val latLon = NativeUtilities.getLatLonFromPixel(
                        mapTileView?.mapRenderer, tileBox!!, point.x, point.y
                    )

                    if (showMapOutCaution) {
                        AlertManager.showMapOutOfBoundsDialog(this)
                        return@OnLongClickListener false
                    }

                    finish = latLon

                    // Reset flag - always start with PEDESTRIAN mode when destination changes
                    switchedToBicycleMode = false
                    calculateRouteToDestination()
                    app?.showShortToastMessage("Destination: " + latLon.latitude + ", " + latLon.longitude)
                    updateStartStopButtonState()
                    true
                }
            }
            return field
        }
    private var routeInfoContainer: View? = null
    private var nextTurnIcon: ImageView? = null
    private var nextTurnDistanceText: TextView? = null
    private val nextTurnText: TextView? = null
    private var remainingDistanceText: TextView? = null
    private var followNavigationCameraButton: Button? = null
    private var overViewNavigationCameraButton: Button? = null
    private var startStopNavigationButton: Button? = null
    private var progressBar: ProgressBar? = null
    private var lnProgressBar: LinearLayout? = null
    private var loadingView: ProgressBar? = null
    private var lnLoadingView: LinearLayout? = null
    private var lnOutOfArea: LinearLayout? = null
    private var myLocationImv: ImageButton? = null
    private var followLocationEnabled = false
    private var hasUpdateFirstOpenMap = false
    private var navigationActive = false
    private var isIndexReady = false
    private var hasDownloadIndex = false

    private val nextDirectionInfo = NextDirectionInfo()
    private val routingDataUpdateListener =
        IRoutingDataUpdateListener { runOnUiThread { this.refreshRouteInfoView() } }
    private var locationProvider: OsmAndLocationProvider? = null
    private val locationListener = OsmAndLocationListener { location: Location? ->
        runOnUiThread {
            updateLocationWhenInit(location)
            downloadMapIfNeed(location)
            // Check map bounds and show alerts
            checkMapBoundsAndShowAlert(location)
        }
    }

    private var routeListener: IRouteInformationListener? = null

    private var finish: LatLon? = null

    // Track if we've switched to bicycle mode due to one-way violation
    private var switchedToBicycleMode = false

    private var overlayLayer: MapTileLayer? = null
    private var facilityMarkerLayer: FacilityMarkerLayer? = null
    private var internalRoutesLayer: InternalRoutesLayer? = null

    // Search destination
    private var destinationText: String = ""
    private var destinationTextView: TextView? = null
    private var btnClearDestination: ImageView? = null
    private var searchBarDestination: MaterialCardView? = null

    // Map bounds alert
    private var showMapOutCaution: Boolean = false
    private var lastCheckedLocation: Location? = null
    private lateinit var mapTrackingUtilities: MapViewTrackingUtilities

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        mapViewWithLayers = findViewById(R.id.map_view_with_layers)
        routeInfoContainer = findViewById(R.id.route_info_container)
        nextTurnIcon = findViewById(R.id.next_turn_icon)
        nextTurnDistanceText = findViewById(R.id.next_turn_distance_text)
        //        nextTurnText = findViewById(R.id.next_turn_text);
        remainingDistanceText = findViewById(R.id.remaining_distance_text)
        followNavigationCameraButton = findViewById(R.id.follow_camera)
        startStopNavigationButton = findViewById(R.id.start_stop_navigation_button)
        progressBar = findViewById(R.id.progress_bar)
        lnProgressBar = findViewById(R.id.ln_progress)
        loadingView = findViewById(R.id.loading_view)
        lnLoadingView = findViewById(R.id.ln_loading_view)
        lnOutOfArea = findViewById(R.id.ln_out_of_area)
        myLocationImv = findViewById(R.id.img_my_location)

        // Search destination views
        searchBarDestination = findViewById(R.id.search_bar_destination)
        destinationTextView = findViewById(R.id.tv_destination_text)
        btnClearDestination = findViewById(R.id.btn_clear_destination)

        showLoading()
        app = application as OsmandApplication
        app?.appInitializer?.addListener(this)
        mapTrackingUtilities = app.getMapViewTrackingUtilities()
        setupDownloadListener()
        setupRouteListener()
        OsmAndLocationProvider.requestFineLocationPermissionIfNeeded(this)
        ensureIndexesLoaded()
        locationProvider = app?.locationProvider

        if (followNavigationCameraButton != null) {
            followNavigationCameraButton?.setOnClickListener { followNavigationCamera() }
            followNavigationCameraButton?.visibility = View.GONE
        }
        if (overViewNavigationCameraButton != null) {
            overViewNavigationCameraButton?.setOnClickListener { overviewCamera() }
            overViewNavigationCameraButton?.visibility = View.GONE
        }

        if (startStopNavigationButton != null) {
            startStopNavigationButton?.setOnClickListener {
                if (navigationActive) {
                    stopNavigation()
                } else {
                    startNavigation()
                }
                updateFollowAndOverViewButtonState()
            }
            updateStartStopButtonState()
        }

        myLocationImv?.setOnClickListener {
            val lastKnown = locationProvider?.lastKnownLocation
            lastKnown?.let {
                centerMapOnLocation(it)
            }
        }
        setupSearchDestination()

        mapTileView = app?.osmandMap?.mapView

//        // Disable default markers before render and adding custom markers
//        mapTileView?.let {
//            removeAllDefaultMarkerLayers(it, app)
//        }

        mapTileView?.setupRenderingView()

        // Initialize map with user location if available
        if (locationProvider != null) {
            val lastKnown = locationProvider?.lastKnownLocation
            if (lastKnown != null) {
                mapTileView?.setIntZoom(17)
                mapTileView?.setLatLon(lastKnown.latitude, lastKnown.longitude)
            }
        }
    }

    private fun getIndexItems(latLon: LatLon): List<IndexItem> {
        val downloadThread = app?.downloadThread ?: return emptyList()
        val indexes = downloadThread.indexes ?: return emptyList()

        // If indexes haven't been loaded from internet, try to reload them
        if (!indexes.isDownloadedFromInternet && !indexes.downloadFromInternetFailed) {
            downloadThread.runReloadIndexFilesSilent()
            // Return empty list for now, indexes will be available after reload completes
            return emptyList()
        }

        try {
            val list =
                DownloadResources.findIndexItemsAt(app, latLon, DownloadActivityType.NORMAL_FILE)

            if (list.isNullOrEmpty()) return emptyList()

            val indexItem = list[0]

            if (doesItemFileExist(indexItem)) return emptyList()

            return list

        } catch (e: IOException) {
            return emptyList()
        }

    }

    override fun updateStatusBarColor() {
        val color = AndroidUtils.getColorFromAttr(this, android.R.attr.colorPrimary)
        if (color != -1) {
            AndroidUiHelper.setStatusBarColor(this, color)
        }
    }

    override fun onContentChanged() {
        super.onContentChanged()

        val root = findViewById<View>(R.id.root)
        val builder = InsetTarget.builder(root).portraitSides(InsetSide.BOTTOM, InsetSide.TOP)
            .landscapeSides(InsetSide.TOP, InsetSide.RIGHT, InsetSide.LEFT)

        InsetsUtils.setWindowInsetsListener(
            root, { view: View?, windowInsetsCompat: WindowInsetsCompat? ->
                InsetsUtils.applyPadding(
                    view!!, windowInsetsCompat!!, builder.build()
                )
            }, true
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Check if IndexItem file actually exists on disk
     * This is more reliable than isDownloaded() flag
     *
     * @param item IndexItem to check
     * @return true if file exists (either target file or backup file)
     */
    private fun doesItemFileExist(item: IndexItem): Boolean {
        val existingFile: File? = item.getExistedFile(app)
        return existingFile != null && existingFile.exists()
    }

    @SuppressLint("SyntheticAccessor")
    override fun onResume() {
        super.onResume()
        mapViewWithLayers?.onResume()
        mapTileView?.setOnLongClickListener(clickListener)
        app?.routingHelper?.addRouteDataListener(routingDataUpdateListener)
        if (locationProvider != null) {
            locationProvider?.addLocationListener(locationListener)
            locationProvider?.resumeAllUpdates()
            val lastKnown = locationProvider?.lastKnownLocation
            if (lastKnown != null) {
                if (followLocationEnabled || !navigationActive) {
                    centerMapOnLocation(lastKnown)
                }
            }
        }
        refreshRouteInfoView()
    }

    private fun startNavigation() {
        if (finish == null) {
            app?.showShortToastMessage("Please select destination by long clicking on the map")
            return
        }

        val currentLocation =
            if (locationProvider != null) locationProvider?.lastKnownLocation else null
        if (currentLocation == null) {
            app?.showShortToastMessage("Unable to get current location. Please enable location services.")
            return
        }

        val routingHelper = app?.routingHelper ?: return
        val settings = app?.settings ?: return

        // If route is not calculated yet, calculate it first
        if (!routingHelper.isRouteCalculated) {
            calculateRouteToDestination()
            // Wait a bit for route calculation, then check again
            // Note: Route calculation is async, so we might need to handle this differently
        }
        mapTrackingUtilities.backToLocationImpl(17, true)
        mapTrackingUtilities.switchRoutePlanningMode()
        settings.FOLLOW_THE_ROUTE.set(true)
        routingHelper.isFollowingMode = true
        routingHelper.isRoutePlanningMode = false
        routingHelper.setCurrentLocation(currentLocation, false)
        OsmAndLocationProvider.requestFineLocationPermissionIfNeeded(this)
        app?.showShortToastMessage("Navigation started")

        navigationActive = true
        updateStartStopButtonState()
        refreshRouteInfoView()
    }

    private fun calculateRouteToDestination() {
        if (finish == null) {
            return
        }

        val currentLocation =
            if (locationProvider != null) locationProvider?.lastKnownLocation else null
        if (currentLocation == null) {
            return
        }

        val start = LatLon(currentLocation.latitude, currentLocation.longitude)
        val settings = app?.settings ?: return
        val routingHelper = app?.routingHelper ?: return
        val targetPointsHelper = app?.targetPointsHelper ?: return

        // Always start with PEDESTRIAN mode when calculating route
        // If route violates one-way, we'll switch to BICYCLE mode in newRouteIsCalculated()
        val modeToUse =
            if (switchedToBicycleMode) ApplicationMode.BICYCLE else ApplicationMode.PEDESTRIAN

        // Set application mode
        settings.applicationMode = modeToUse
        routingHelper.appMode = modeToUse

        // Set start and destination points
        targetPointsHelper.setStartPoint(
            start, false, PointDescription(start.latitude, start.longitude)
        )
        targetPointsHelper.navigateToPoint(
            finish!!, true, -1, PointDescription(finish!!.latitude, finish!!.longitude)
        )

        // Enter route planning mode to calculate route (but don't start navigation)
        app?.osmandMap?.mapActions?.enterRoutePlanningModeGivenGpx(null, start, null, true, false)

        // Only set route planning mode, don't activate following mode
        routingHelper.isRoutePlanningMode = true
        routingHelper.isFollowingMode = false
        routingHelper.notifyIfRouteIsCalculated()
        routingHelper.setCurrentLocation(currentLocation, false)

        // Refresh map to show route
        mapTileView?.refreshMap()
    }

    private fun refreshRouteInfoView() {
        if (routeInfoContainer == null) {
            return
        }

        val routingHelper = app?.routingHelper
        if (routingHelper == null || !routingHelper.isRouteCalculated || !navigationActive) {
            routeInfoContainer?.visibility = View.GONE
            return
        }

        routeInfoContainer?.visibility = View.VISIBLE

        val info = routingHelper.getNextRouteDirectionInfo(nextDirectionInfo, false)
        if (info?.directionInfo != null) {
            val instruction = info.directionInfo.getDescriptionRoute(app!!)
            val distanceToTurn = OsmAndFormatter.getFormattedDistance(
                max(info.distanceTo.toDouble(), 0.0).toFloat(), app!!
            )
            nextTurnDistanceText?.text =
                getString(R.string.route_info_next_turn_format, distanceToTurn)
            updateTurnIcon(info.directionInfo, instruction)
        } else {
            nextTurnDistanceText?.setText(R.string.route_info_distance_placeholder)
            if (nextTurnIcon != null) {
                nextTurnIcon?.setImageResource(R.drawable.ic_turn_straight)
            }
        }

        val remaining = routingHelper.leftDistance
        if (remaining > 0) {
            val remainingFormatted =
                OsmAndFormatter.getFormattedDistance(remaining.toFloat(), app!!)
            val timeFormatted =
                OsmAndFormatter.getFormattedDurationShortMinutes(routingHelper.leftTime)

            remainingDistanceText?.text = getString(
                R.string.route_info_remaining_with_time_format, remainingFormatted, timeFormatted
            )
        } else {
            remainingDistanceText?.setText(R.string.route_info_remaining_placeholder)
        }
    }

    private fun updateTurnIcon(directionInfo: Any?, instruction: String?) {
        if (nextTurnIcon == null) {
            return
        }

        var iconResId = R.drawable.ic_turn_straight // default

        // Try to get TurnType from DirectionInfo using reflection
        try {
            if (directionInfo != null) {
                val turnTypeField = directionInfo.javaClass.getDeclaredField("turnType")
                turnTypeField.isAccessible = true
                val turnType = turnTypeField[directionInfo]

                if (turnType != null) {
                    val turnTypeStr = turnType.toString()
                    iconResId = getTurnIconResource(turnTypeStr)
                }
            }
        } catch (_: Exception) {
            // Fallback to instruction text parsing
            if (instruction != null) {
                iconResId = getTurnIconFromInstruction(instruction)
            }
        }

        nextTurnIcon?.setImageResource(iconResId)
    }

    private fun getTurnIconResource(turnType: String?): Int {
        if (turnType == null) {
            return R.drawable.ic_turn_straight
        }
        val lower = turnType.lowercase(Locale.getDefault())
        if (lower.contains("left") || lower.contains("tl") || lower.contains("tsll")) {
            return R.drawable.ic_turn_left
        } else if (lower.contains("right") || lower.contains("tr") || lower.contains("tslr")) {
            return R.drawable.ic_turn_right
        } else if (lower.contains("straight") || lower.contains("ts") || lower.contains("c")) {
            return R.drawable.ic_turn_straight
        } else if (lower.contains("uturn") || lower.contains("tu")) {
            return R.drawable.ic_turn_uturn
        }
        return R.drawable.ic_turn_straight
    }

    private fun getTurnIconFromInstruction(instruction: String?): Int {
        if (instruction == null) {
            return R.drawable.ic_turn_straight
        }
        val lower = instruction.lowercase(Locale.getDefault())
        if (lower.contains("left")) {
            return R.drawable.ic_turn_left
        } else if (lower.contains("right")) {
            return R.drawable.ic_turn_right
        } else if (lower.contains("straight")) {
            return R.drawable.ic_turn_straight
        } else if (lower.contains("u-turn")) {
            return R.drawable.ic_turn_uturn
        }
        return R.drawable.ic_turn_straight
    }


    private fun overviewCamera() {
        if (!navigationActive) {
            return
        }
        val routingHelper = app?.routingHelper
        val currentLocation =
            if (locationProvider != null) locationProvider!!.lastKnownLocation else null
        if (currentLocation == null) {
            app?.showShortToastMessage("Unable to get current location. Please enable location services.")
            return
        }
        app?.settings?.FOLLOW_THE_ROUTE?.set(false)
        routingHelper?.isFollowingMode = false
        routingHelper?.notifyIfRouteIsCalculated()
        routingHelper?.setCurrentLocation(currentLocation, false)
        val mapTrackingUtilities = app?.mapViewTrackingUtilities
        mapTrackingUtilities?.switchRoutePlanningMode()
        app?.osmandMap?.mapView?.refreshMap()
    }

    private fun followNavigationCamera() {
        val routingHelper = app?.routingHelper ?: return
        val currentLocation =
            if (locationProvider != null) locationProvider!!.lastKnownLocation else null

        if (currentLocation == null) {
            app?.showShortToastMessage("Unable to get current location. Please enable location services.")
            return
        }
        val settings = app?.settings ?: return
        settings.FOLLOW_THE_ROUTE.set(true)
        routingHelper.isFollowingMode = true
        routingHelper.isRoutePlanningMode = false
        routingHelper.notifyIfRouteIsCalculated()
        routingHelper.setCurrentLocation(currentLocation, false)
        val mapTrackingUtilities = app?.mapViewTrackingUtilities ?: return
        mapTrackingUtilities.switchRoutePlanningMode()
        app?.osmandMap?.mapView?.refreshMap()
    }

    private fun updateStartStopButtonState() {
        startStopNavigationButton?.visibility = if (finish != null) View.VISIBLE else View.GONE
        startStopNavigationButton?.setText(
            if (navigationActive) R.string.stop_navigation
            else R.string.start_navigation
        )
    }

    private fun updateFollowAndOverViewButtonState() {
        followNavigationCameraButton?.visibility = if (navigationActive) View.VISIBLE else View.GONE
    }


    private fun downloadMapIfNeed(location: Location?) {
        // Only download when:
        // 1. Location is available
        // 2. App is initialized (indexes ready)
        // 3. Network is available
        // 4. Not currently downloading
        if (location == null || isIndexReady.not() || hasDownloadIndex) {
            return
        }
        
        // Check network before downloading
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Log.d("MainActivity", "No network available, skip map download/update check")
            return
        }
        
        downloadMap(location)
    }

    private fun updateLocationWhenInit(location: Location?) {
        if (location == null || hasUpdateFirstOpenMap) {
            return
        }

        val routingHelper = app?.routingHelper
        routingHelper?.setCurrentLocation(location, false)
        hasUpdateFirstOpenMap = true
        centerMapOnLocation(location)

        refreshRouteInfoView()
    }

    private fun centerMapOnLocation(location: Location) {
        if (mapTileView != null) {
            mapTileView?.setLatLon(location.latitude, location.longitude)
            mapTileView?.setIntZoom(17)
            mapTileView?.setLatLon(location.latitude, location.longitude)
        }
    }

    private fun stopNavigation() {
        val routingHelper = app?.routingHelper
        val targetPointsHelper = app?.targetPointsHelper

        settings.applicationMode = ApplicationMode.DEFAULT
        routingHelper?.appMode = ApplicationMode.DEFAULT

        if (routingHelper != null) {
            routingHelper.isFollowingMode = false
            routingHelper.isRoutePlanningMode = false
            routingHelper.clearCurrentRoute(null, null)
        }
        targetPointsHelper?.clearAllPoints(true)

        navigationActive = false
        finish = null
        routeInfoContainer?.visibility = View.GONE

        // Reset flag when stopping navigation
        switchedToBicycleMode = false

        updateStartStopButtonState()
    }

    private fun downloadIndexItem(item: IndexItem) {
        val validationManager = DownloadValidationManager(app!!)
        validationManager.startDownload(this, item)
    }

    private fun refreshUIAfterDownload() {
        // Re-query maps at current location to update status
        val location = app.locationProvider.lastKnownLocation
        if (location != null) {
            val latLon = LatLon(location.latitude, location.longitude)
            val maps: List<IndexItem> = getIndexItems(latLon)
            // Update UI to show which maps are now downloaded
            for (item in maps) {
                if (isItemFullyDownloaded(item)) {
                    Log.d("MainActivity", "Map downloaded: " + item.fileName)
                    // Update your UI here
                }
            }
        }
    }

    private fun isItemFullyDownloaded(item: IndexItem): Boolean {
        return item.isDownloaded && doesItemFileExist(item) && !item.isOutdated
    }


    private fun areIndexesReady(): Boolean {
        val downloadThread: DownloadIndexesThread = app.downloadThread
        val indexes: DownloadResources = downloadThread.indexes
        // Check if indexes have been loaded (groupByRegion should be populated)
        // We can't directly check groupByRegion, but we can check if indexes were downloaded from internet
        return indexes.isDownloadedFromInternet || indexes.downloadFromInternetFailed
    }

    private fun downloadMap(location: Location) {
        // Ensure app is initialized
        if (!isIndexReady || !areIndexesReady()) {
            Log.d("MainActivity", "App not initialized yet, ensuring indexes loaded...")
            ensureIndexesLoaded()
            return
        }

        // Check network again to ensure connectivity
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Log.d("MainActivity", "No network available, cannot download map")
            return
        }

        hasDownloadIndex = true
        val latLon = LatLon(location.latitude, location.longitude)
        
        // Get maps at location
        val maps: List<IndexItem> =
            getMapsAtLocationSafely(latLon, DownloadActivityType.NORMAL_FILE)

        // Download the first map if available
        if (maps.isNotEmpty()) {
            val firstMap = maps[0]

            // Check if currently downloading
            if (isDownloading(firstMap)) {
                Log.d("MainActivity", "Map is currently downloading: ${firstMap.fileName}")
                return
            }

            // Check if map is outdated (needs to be re-downloaded)
            val isOutdated = firstMap.isOutdated
            val isDownloaded = firstMap.isDownloaded && doesItemFileExist(firstMap)

            if (isOutdated) {
                Log.d("MainActivity", "Map is outdated: ${firstMap.fileName}, downloading update...")
                downloadIndexItem(firstMap)
            } else if (!isDownloaded) {
                // Map not downloaded yet
                Log.d("MainActivity", "Map not downloaded: ${firstMap.fileName}, downloading...")
                downloadIndexItem(firstMap)
            } else {
                // Map already downloaded and up-to-date
                Log.d("MainActivity", "Map already downloaded and up-to-date: ${firstMap.fileName}")
            }
        }
    }

    private fun ensureIndexesLoaded() {
        if (!areIndexesReady()) {
            Log.d("minh", "Indexes not loaded. Triggering reload...")
            app.downloadThread.runReloadIndexFilesSilent()
        }
    }

    private fun isDownloading(item: IndexItem): Boolean {
        val downloadThread = app.downloadThread
        return downloadThread.isDownloading(item)
    }

    private fun getMapsAtLocationSafely(
        latLon: LatLon, type: DownloadActivityType
    ): List<IndexItem> {
        // Check if regions are initialized
        if (!app.regions.isInitialized) {
            return emptyList()
        }

        // Check if indexes are loaded (groupByRegion is populated)
        val downloadThread = app.downloadThread
        val indexes = downloadThread.indexes

        // If indexes haven't been loaded from internet, try to reload them
        if (!indexes.isDownloadedFromInternet && !indexes.downloadFromInternetFailed) {
            downloadThread.runReloadIndexFilesSilent()
            // Return empty list for now, indexes will be available after reload completes
            return emptyList()
        }

        try {
            val items = DownloadResources.findIndexItemsAt(app, latLon, type)
            return items
        } catch (_: IOException) {
            return emptyList()
        }
    }

    private fun showDownloadIndexProgress(percentDownload: Float) {
        lnProgressBar?.visibility = View.VISIBLE
        progressBar?.visibility = View.VISIBLE
        progressBar?.progress = percentDownload.toInt()
    }

    private fun hideDownloadIndexProgress() {
        lnProgressBar?.visibility = View.GONE
        progressBar?.visibility = View.GONE
    }

    private fun showLoading() {
        lnLoadingView?.visibility = View.VISIBLE
        loadingView?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        lnLoadingView?.visibility = View.GONE
        loadingView?.visibility = View.GONE
    }

    private fun addShigiraResortMapOverlay() {
//        val overlayName = "Shigira Resort Map"
//        val urlTemplate = "https://shigira-resort-map.resort-cloud.com/hot/{z}/{x}/{y}.png"
//        addMapOverlay(overlayName, urlTemplate, 1, 18)

    }

    private fun setMapLanguage(localeCode: String): Boolean {
        try {
            val settings = app.settings
            // Set map preferred locale
            settings.MAP_PREFERRED_LOCALE.set(localeCode)
            app.osmandMap.mapView.refreshMapComplete()
            return true
        } catch (e: java.lang.Exception) {
            Log.e("NavigationActivity", "Error setting map language: $localeCode", e)
            return false
        }
    }

    private fun addMapOverlay(
        overlayName: String, urlTemplate: String?,
        minZoom: Int, maxZoom: Int
    ): Boolean {
        try {
            val normalizedUrl: String =
                TileSourceManager.TileSourceTemplate.normalizeUrl(urlTemplate)

            val tileSource: TileSourceManager.TileSourceTemplate =
                TileSourceManager.TileSourceTemplate(
                    overlayName,
                    normalizedUrl,
                    ".png",
                    maxZoom,
                    minZoom,
                    256,
                    16,
                    18000
                )

            // Install tile source (creates directory and meta info file)
            val installed = app.settings.installTileSource(tileSource)
            if (!installed) {
                Log.e("NavigationActivity", "Failed to install tile source: $overlayName")
                return false
            }

            // Set as overlay
            app.settings.MAP_OVERLAY.set(overlayName)
            app.settings.MAP_OVERLAY_PREVIOUS.set(overlayName)

            // Apply overlay immediately (no need for MapActivity)
            applyOverlayToMap()

            Log.d("NavigationActivity", "✅ Map overlay added successfully: $overlayName")
            return true
        } catch (e: java.lang.Exception) {
            Log.e("NavigationActivity", "Error adding map overlay: $overlayName", e)
            return false
        }
    }

    private fun applyOverlayToMap() {
        try {
            val settings: OsmandSettings = app.settings
            val mapView = app.osmandMap.mapView


            // Get overlay tile source from settings
            val overlayName: String = settings.MAP_OVERLAY.get()
            if (overlayName.isEmpty()) {
                // Remove overlay if setting is null
                removeOverlayLayer()
                return
            }

            val tileSource: ITileSource? = settings.getTileSourceByName(overlayName, false)
            if (tileSource == null) {
                Log.w("NavigationActivity", "⚠️ Tile source not found: $overlayName")
                return
            }


            // Create overlay layer if not exists
            if (overlayLayer == null) {
                overlayLayer = MapTileLayer(app, false)
            }


            // Check if layer already has this map
//            if (tileSource == overlayLayer?.map) {
//                Log.d("NavigationActivity", "Overlay already applied")
//                return
//            }


            // Add layer to map view if not exists
            overlayLayer?.let {
                if (!mapView.isLayerExists(it)) {
                    mapView.addLayer(it, 0f)
                }
            }

            // Set map cho layer
            overlayLayer?.map = tileSource


            // Set transparency
            val transparency: Int = settings.MAP_OVERLAY_TRANSPARENCY.get()
            overlayLayer?.alpha = transparency


            // Refresh map
            mapView.refreshMap()

            Log.d("NavigationActivity", "✅ Map overlay applied immediately to map view")
        } catch (e: java.lang.Exception) {
            // Safely handle - overlay setting is already saved
            Log.w("NavigationActivity", "⚠️ Could not apply overlay immediately: " + e.message)
            Log.d(
                "NavigationActivity",
                "Overlay setting is saved and will be applied when map opens"
            )
        }
    }

    private fun removeOverlayLayer() {
        try {
            if (overlayLayer != null) {
                val mapView = app.osmandMap.mapView
                if (mapView.isLayerExists(overlayLayer!!)) {
                    mapView.removeLayer(overlayLayer!!)
                }
                overlayLayer?.map = null
                mapView.refreshMap()
                Log.d("NavigationActivity", "✅ Overlay layer removed from map view")
            }
        } catch (e: java.lang.Exception) {
            Log.w("NavigationActivity", "⚠️ Could not remove overlay layer: " + e.message)
        }
    }

    fun setVoiceLanguage(languageCode: String, useTTS: Boolean): Boolean {
        try {
            val settings = app.settings
            val appMode = app.routingHelper.appMode


            // Build voice provider name
            val voiceProvider = if (useTTS) {
                // TTS format: "en-tts", "vi-tts", etc.
                languageCode + IndexConstants.VOICE_PROVIDER_SUFFIX
            } else {
                // Recorded voice format: "en", "vi", etc.
                languageCode
            }


            // Set voice provider
            settings.VOICE_PROVIDER.setModeValue(appMode, voiceProvider)

            // Unmute voice if it was muted
            if (settings.VOICE_MUTE.getModeValue(appMode)) {
                settings.VOICE_MUTE.setModeValue(appMode, false)
                app.routingHelper.voiceRouter.setMuteForMode(appMode, false)
            }


            // Initialize voice command player
            app.initVoiceCommandPlayer(this, appMode, null, false, false, false, false)

            Log.d(
                "NavigationActivity",
                "✅ Voice language set to: " + languageCode + (if (useTTS) " (TTS)" else " (Recorded)")
            )
            return true
        } catch (e: java.lang.Exception) {
            Log.e("NavigationActivity", "Error setting voice language: $languageCode", e)
            return false
        }
    }

    private fun disableToasts() {
        val toastHelper: ToastHelper = app.toastHelper
        toastHelper.setDisplayHandler(object : ToastHelper.ToastDisplayHandler {

            override fun showSimpleToast(p0: String, p1: Boolean) {

            }

            override fun showSimpleToast(textId: Int, isLong: Boolean, vararg args: Any?) {
                // Do nothing - toast disabled
            }

            override fun showCarToast(p0: String, p1: Boolean) {

            }

            override fun showCarToast(textId: Int, isLong: Boolean, vararg args: Any?) {
                // Do nothing - toast disabled
            }
        })
        Log.d("NavigationActivity", "🔇 Toasts disabled")
    }

    private fun removeRoutingListener() {
        routeListener?.let {
            app?.routingHelper?.removeListener(it)
        }
    }

    private fun resetDownload() {
        val downloadThread = app?.downloadThread
        downloadThread?.resetUiActivity(this)
    }

    private fun removeLocationListener() {
        if (locationProvider != null) {
            locationProvider?.removeLocationListener(locationListener)
            locationProvider?.pauseAllUpdates()
        }
    }

    private fun resetAllData() {
        stopNavigation()
        removeOverlayLayer()
        removeLocationListener()
        mapViewWithLayers?.onDestroy()
        app?.appInitializer?.removeListener(this)
        removeRoutingListener()
        resetDownload()
        isIndexReady = false
        hasDownloadIndex = false
    }

    override fun onPause() {
        super.onPause()
        mapViewWithLayers?.onPause()
        mapTileView?.setOnLongClickListener(null)
        app?.routingHelper?.removeRouteDataListener(routingDataUpdateListener)
        removeLocationListener()
        // Stop beep when activity pauses
        showMapOutCaution = false
        AlertManager.stopBeepSound()
    }

    override fun onDestroy() {
        // Remove custom facility marker layer
        facilityMarkerLayer?.let {
            if (mapTileView?.isLayerExists(it) == true) {
                mapTileView?.removeLayer(it)
            }
        }
        facilityMarkerLayer = null

        super.onDestroy()
        resetAllData()
        // Stop beep when activity destroys
        AlertManager.stopBeepSound()
    }

    override fun onStart(init: AppInitializer) {
        super<AppInitializeListener>.onStart(init)
    }

    override fun onFinish(init: AppInitializer) {
        super.onFinish(init)
        setupMap()
        hideLoading()
    }

    private fun setupMap() {
        disableToasts()
        setMapLanguage("ja")
        addShigiraResortMapOverlay() //TODO only use for nansei

        // Setup custom facility markers
//        setupFacilityMarkers()

        // Setup internal routes from GPX
        setupInternalRoutes()

        // Set default speed for all modes at initialization
        initializeDefaultSpeed()
    }

    /**
     * Disable all default markers (POI, Favorites, etc.) to use custom markers instead
     */
    fun removeAllDefaultMarkerLayers(mapView: OsmandMapTileView, app: OsmandApplication) {

        val poiLayer = mapView.getLayerByClass(POIMapLayer::class.java)
        if (poiLayer != null) {
            mapView.removeLayer(poiLayer)
        }

        val favoritesLayer = mapView.getLayerByClass(FavouritesLayer::class.java)
        if (favoritesLayer != null) {
            mapView.removeLayer(favoritesLayer)
        }

        val mapMarkersLayer = mapView.getLayerByClass(MapMarkersLayer::class.java)
        if (mapMarkersLayer != null) {
            mapView.removeLayer(mapMarkersLayer)
        }

        val transportStopsLayer = mapView.getLayerByClass(TransportStopsLayer::class.java)
        if (transportStopsLayer != null) {
            mapView.removeLayer(transportStopsLayer)
        }

        val mapLayers = app.osmandMap.mapLayers
        val mapVectorLayer = mapLayers.mapVectorLayer
        mapVectorLayer?.symbolsAlpha = 0

        app.poiFilters.clearAllSelectedPoiFilters()

        val settings = app.settings
        settings.SHOW_FAVORITES.set(false)
        settings.SHOW_MAP_MARKERS.set(false)

        mapView.refreshMap()
    }

    /**
     * Setup custom facility markers layer
     * Uses constant data from MarkerDataConstants (extracted from miyako_map.html)
     */
    private fun setupFacilityMarkers() {
        try {
            // Remove old layer if exists
            facilityMarkerLayer?.let { oldLayer ->
                if (mapTileView?.isLayerExists(oldLayer) == true) {
                    mapTileView?.removeLayer(oldLayer)
                }
            }

            // Create new layer with constant data (no need for ViewModel data)
            facilityMarkerLayer = FacilityMarkerLayer(this)
            facilityMarkerLayer?.let { layer ->
                mapTileView?.addLayer(layer, 5f)
                val markerCount =
                    com.resort_cloud.nansei.nansei_tablet.data.MarkerDataConstants.getAllMarkers().size
                Log.d(
                    "MainActivity",
                    "✅ Facility markers added: $markerCount markers from constants"
                )
            }
            mapTileView?.refreshMap()
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error setting up facility markers: ${e.message}", e)
        }


    }

    /**
     * Setup internal routes layer to draw polylines from GPX file
     */
    private fun setupInternalRoutes() {
        try {
            // Remove old layer if exists
            internalRoutesLayer?.let { oldLayer ->
                if (mapTileView?.isLayerExists(oldLayer) == true) {
                    mapTileView?.removeLayer(oldLayer)
                }
            }

            // Create new layer
            internalRoutesLayer = InternalRoutesLayer(this)
            internalRoutesLayer?.let { layer ->
                // Add layer with lower priority (drawn below markers)
                mapTileView?.addLayer(layer, 3f)
                Log.d("MainActivity", "✅ Internal routes layer added from data_internal.gpx")
            }
            mapTileView?.refreshMap()
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error setting up internal routes: ${e.message}", e)
        }
    }


    /**
     * Initialize default speed settings for all application modes
     * This is called once when map is initialized
     */
    private fun initializeDefaultSpeed() {
        try {
            val settings = app?.settings ?: return

            // Set default speed for PEDESTRIAN mode (Golf Cart)
            setDefaultSpeedForMode(settings, ApplicationMode.PEDESTRIAN, 3.33f)

            // You can also set for other modes if needed:
            // setDefaultSpeedForMode(settings, ApplicationMode.CAR, 13.89f)
            // setDefaultSpeedForMode(settings, ApplicationMode.BICYCLE, 4.17f)

            Log.d("MainActivity", "✅ Default speeds initialized for all modes")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error initializing default speeds: ${e.message}", e)
        }
    }

    /**
     * Set default speed for a specific ApplicationMode
     * This will affect routing time calculations in OsmAnd library
     */
    private fun setDefaultSpeedForMode(
        settings: OsmandSettings,
        mode: ApplicationMode,
        speedMps: Float
    ) {
        try {
            // METHOD 1: Try to set DEFAULT_SPEED field
            try {
                val defaultSpeedField = settings.javaClass.getDeclaredField("DEFAULT_SPEED")
                defaultSpeedField.isAccessible = true
                val defaultSpeedSetting = defaultSpeedField.get(settings)

                if (defaultSpeedSetting != null) {
                    // Try to set speed for specific mode using setModeValue
                    try {
                        val setModeValueMethod = defaultSpeedSetting.javaClass.getMethod(
                            "setModeValue",
                            ApplicationMode::class.java,
                            Any::class.java
                        )
                        setModeValueMethod.invoke(defaultSpeedSetting, mode, speedMps)
                        Log.d("MainActivity", "✅ Set DEFAULT_SPEED for $mode successfully")
                        return
                    } catch (_: Exception) {
                        Log.d("MainActivity", "⚠️ setModeValue(Any) failed, trying Float method")

                        // Try with Float parameter
                        try {
                            val setModeValueMethodFloat = defaultSpeedSetting.javaClass.getMethod(
                                "setModeValue",
                                ApplicationMode::class.java,
                                Float::class.java
                            )
                            setModeValueMethodFloat.invoke(defaultSpeedSetting, mode, speedMps)
                            return
                        } catch (e2: Exception) {
                            Log.d("MainActivity", "⚠️ setModeValue(Float) failed: ${e2.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("MainActivity", "⚠️ DEFAULT_SPEED field not found: ${e.message}")
            }

            // METHOD 2: Try to find and set speed-related fields
            val allFields = settings.javaClass.declaredFields
            for (field in allFields) {
                if (field.name.uppercase().contains("SPEED")) {
                    try {
                        field.isAccessible = true
                        val setting = field.get(settings)
                        if (setting != null) {
                            // Try setModeValue
                            try {
                                val setModeValueMethod = setting.javaClass.getMethod(
                                    "setModeValue",
                                    ApplicationMode::class.java,
                                    Any::class.java
                                )
                                setModeValueMethod.invoke(setting, mode, speedMps)
                                return
                            } catch (_: Exception) {
                                // Try Float parameter
                                try {
                                    val setModeValueMethodFloat = setting.javaClass.getMethod(
                                        "setModeValue",
                                        ApplicationMode::class.java,
                                        Float::class.java
                                    )
                                    setModeValueMethodFloat.invoke(setting, mode, speedMps)
                                    return
                                } catch (_: Exception) {
                                    // Continue to next field
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Skip this field
                    }
                }
            }

            Log.w("MainActivity", "⚠️ Could not find suitable speed setting field for $mode")
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error setting default speed for $mode: ${e.message}", e)
        }
    }

    private fun setupDownloadListener() {
        val downloadThread = app?.downloadThread
        downloadThread?.setUiActivity(this) // Set activity as listener
    }

    private fun setupRouteListener() {
        routeListener = this
        routeListener?.let {
            app?.routingHelper?.addListener(it)
        }
    }

    override fun onUpdatedIndexesList() {
        super.onUpdatedIndexesList()
        isIndexReady = true

        // When indexes are updated, reset flag to check for outdated maps again
        // Only reset if network is available to avoid unnecessary checks when offline
        if (NetworkUtils.isNetworkAvailable(this)) {
            hasDownloadIndex = false
            Log.d("MainActivity", "Indexes updated, will check for outdated maps on next location update")
        }
    }

    override fun downloadInProgress() {
        super.downloadInProgress()
        val task = app?.downloadThread?.currentRunningTask?.downloadProgress
        showDownloadIndexProgress(task ?: 0f)
    }

    override fun downloadingError(error: String) {
        super.downloadingError(error)
        hasDownloadIndex = false
        hideDownloadIndexProgress()
    }

    override fun downloadHasFinished() {
        super.downloadHasFinished()
        hideDownloadIndexProgress()
        setMapLanguage("ja")
        app.downloadThread.updateLoadedFiles()
        addShigiraResortMapOverlay()
        refreshUIAfterDownload()
        
        // Reset flag after download completes to allow checking again if needed
        hasDownloadIndex = false
        Log.d("MainActivity", "Map download finished, reset download flag")
    }

    override fun newRouteIsCalculated(newRoute: Boolean, p1: ValueHolder<Boolean>?) {
        val routingHelper = app?.routingHelper ?: return
        mapTileView?.refreshMap();

        // Validate route for one-way restrictions in pedestrian mode
        if (newRoute && routingHelper.appMode == ApplicationMode.PEDESTRIAN) {
            val isValid = RouteOneWayValidator.validateRouteForOneWay(app, routingHelper)
            if (!isValid) {
                // Route violates one-way restrictions, switch to BICYCLE mode and recalculate
                Log.w(
                    "MainActivity",
                    "Route violates one-way restrictions, switching to BICYCLE mode"
                )

                // Clear current route
                routingHelper.clearCurrentRoute(null, null)

                // Switch to bicycle mode
                switchedToBicycleMode = true

                // Wait a bit then recalculate with bicycle mode
                app?.runInUIThread({
                    calculateRouteToDestination()
                }, 500)
                return
            } else {
                // Route is valid, reset flag
                switchedToBicycleMode = false
            }
        }

        if (newRoute && routingHelper.isRoutePlanningMode && !mapTileView!!.isCarView) {
            app.runInUIThread(this::fitCurrentRouteToMap, 300);
        }
    }

    private fun fitCurrentRouteToMap() {
        app.osmandMap.fitCurrentRouteToMap(true, 0)
    }

    override fun routeWasCancelled() {
    }

    override fun routeWasFinished() {
        stopNavigation()
        updateFollowAndOverViewButtonState()
    }

    /**
     * Setup search destination functionality
     */
    private fun setupSearchDestination() {
        // Setup observers for ViewModel
        setupFacilityObservers()

        // Load facilities from API
        viewModel.loadFacilities()

        // Setup click listener cho search bar
        searchBarDestination?.setOnClickListener {
            openSearchDestinationDialog()
        }

        // Setup clear button
        btnClearDestination?.setOnClickListener {
            clearDestination()
        }

        // Update UI
        updateDestinationTextUI()
    }

    /**
     * Setup observers for facility data and errors from ViewModel
     */
    private fun setupFacilityObservers() {
        // Observe facility kinds
        viewModel.facilityKinds.observe(this) { facilityKinds ->
            Log.d("MainActivity", "Facility kinds updated: ${facilityKinds.size}")
        }

        // Observe errors
        viewModel.facilityError.observe(this) { exception ->
            exception?.let {
                ErrorHandler.handleError(this@MainActivity, it) {
                    // Clear error after dialog dismissed
                    viewModel.clearError()
                }
            }
        }
    }

    /**
     * Mở dialog search destination
     */
    private fun openSearchDestinationDialog() {
        val currentFacilityKinds = viewModel.facilityKinds.value ?: emptyList()

        if (currentFacilityKinds.isEmpty())
            return
        showSearchDialog(currentFacilityKinds)

    }

    /**
     * Hiển thị search dialog
     */
    private fun showSearchDialog(facilityKinds: List<com.resort_cloud.nansei.nansei_tablet.data.model.FacilityKind>) {
        val dialog = SearchDestinationDialog.newInstance(
            facilityKinds = facilityKinds,
            currentDestinationText = destinationText,
            onFacilitySelected = { facility ->
                onDestinationSelected(facility)
            }
        )
        dialog.show(supportFragmentManager, "SearchDestinationDialog")
    }

    /**
     * Xử lý khi chọn destination facility
     */
    private fun onDestinationSelected(facility: com.resort_cloud.nansei.nansei_tablet.utils.FacilityItem.FacilityData) {

        stopNavigation()
        // Reset flag when destination changes - always start with PEDESTRIAN mode
        switchedToBicycleMode = false

        // Set destination text
        destinationText = facility.name
        updateDestinationTextUI()

        // Set destination location
        val destinationLatLon = LatLon(facility.latitude, facility.longitude)
        if (showMapOutCaution) {
            AlertManager.showMapOutOfBoundsDialog(this)
            return
        }

        finish = destinationLatLon

        calculateRouteToDestination()

        updateStartStopButtonState()
        updateFollowAndOverViewButtonState()
    }

    /**
     * Clear destination
     */
    private fun clearDestination() {
        destinationText = ""
        updateDestinationTextUI()
        stopNavigation()
        updateFollowAndOverViewButtonState()
    }

    /**
     * Update destination text UI
     */
    private fun updateDestinationTextUI() {
        if (destinationText.isEmpty()) {
            destinationTextView?.text = getString(R.string.destination_place)
            destinationTextView?.setTextColor(
                ContextCompat.getColor(
                    this,
                    R.color.darker_gray
                )
            )
            btnClearDestination?.visibility = View.GONE
        } else {
            destinationTextView?.text = destinationText
            destinationTextView?.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            btnClearDestination?.visibility = View.VISIBLE
        }
    }

    /**
     * Check map bounds and show alert if needed
     * Similar to Flutter _setBlShowMapOutCaution() and _showDialogIfNgLocationStatus()
     */
    private fun checkMapBoundsAndShowAlert(location: Location?) {
        if (location == null) {
            // No location: show dialog if not already shown
            if (lastCheckedLocation == null) {
                AlertManager.showLocationDisabledDialog(this)
            }
            AlertManager.manageBeepSound(false, this)
            showMapOutCaution = false
            return
        }

        val lat = location.latitude
        val lng = location.longitude

        // Check if out of main map bounds (show dialog)
        val isOutOfMapBounds = MapBoundsConstants.isLocationOutOfMapBounds(lat, lng)
        if (isOutOfMapBounds) {
            showOutOfAreaView()
        } else {
            hideOutOfAreaView()
        }

        // Check if out of caution bounds (show text warning + beep)
        val shouldShowCaution = MapBoundsConstants.isLocationOutOfCautionBounds(lat, lng)

        // Update caution state and manage beep
        if (shouldShowCaution != showMapOutCaution) {
            showMapOutCaution = shouldShowCaution
            AlertManager.manageBeepSound(shouldShowCaution, this)
        }

        lastCheckedLocation = location
    }

    private fun showOutOfAreaView() {
        lnOutOfArea?.visibility = View.VISIBLE
    }

    private fun hideOutOfAreaView() {
        lnOutOfArea?.visibility = View.GONE
    }

}