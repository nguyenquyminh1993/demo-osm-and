package net.osmand.library.sample

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
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
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
import net.osmand.plus.views.layers.MapTileLayer
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.max

class MainActivity : OsmandActionBarActivity(), AppInitializeListener, DownloadEvents,
    IRouteInformationListener {
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

                    finish = latLon

                    // Add destination point immediately to show marker on map
                    val targetPointsHelper = app?.targetPointsHelper
                    if (targetPointsHelper != null) {
                        // Clear previous destination if exists
                        targetPointsHelper.clearPointToNavigate(false)
                        // Add new destination point
                        targetPointsHelper.navigateToPoint(
                            latLon, true, -1, PointDescription(latLon.latitude, latLon.longitude)
                        )
                        // Refresh map to show the marker
                        mapTileView?.refreshMap()
                    }

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
    private val followLocationButton: MaterialButton? = null
    private var followNavigationCameraButton: Button? = null
    private var overViewNavigationCameraButton: Button? = null
    private var startStopNavigationButton: Button? = null
    private var modeCarButton: MaterialButton? = null
    private var modeBikeButton: MaterialButton? = null
    private var modeFootButton: MaterialButton? = null
    private var progressBar: ProgressBar? = null
    private var lnProgressBar: LinearLayout? = null
    private var loadingView: ProgressBar? = null
    private var lnLoadingView: LinearLayout? = null
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
        }
    }

    private var routeListener: IRouteInformationListener? = null

    private var finish: LatLon? = null

    private var applicationMode: ApplicationMode? = ApplicationMode.PEDESTRIAN

    private var overlayLayer: MapTileLayer? = null


    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.navigation_map_activity)
        mapViewWithLayers = findViewById(R.id.map_view_with_layers)
        routeInfoContainer = findViewById(R.id.route_info_container)
        nextTurnIcon = findViewById(R.id.next_turn_icon)
        nextTurnDistanceText = findViewById(R.id.next_turn_distance_text)
        //        nextTurnText = findViewById(R.id.next_turn_text);
        remainingDistanceText = findViewById(R.id.remaining_distance_text)
        followNavigationCameraButton = findViewById(R.id.follow_camera)
        overViewNavigationCameraButton = findViewById(R.id.btn_over_view)
        startStopNavigationButton = findViewById(R.id.start_stop_navigation_button)
        modeCarButton = findViewById(R.id.mode_car_button)
        modeBikeButton = findViewById(R.id.mode_bike_button)
        modeFootButton = findViewById(R.id.mode_foot_button)
        progressBar = findViewById(R.id.progress_bar)
        lnProgressBar = findViewById(R.id.ln_progress)
        loadingView = findViewById(R.id.loading_view)
        lnLoadingView = findViewById(R.id.ln_loading_view)
        myLocationImv = findViewById(R.id.img_my_location)

        showLoading()
        app = application as OsmandApplication
        app?.appInitializer?.addListener(this)
        setupDownloadListener()
        setupRouteListener()
        OsmAndLocationProvider.requestFineLocationPermissionIfNeeded(this)
        ensureIndexesLoaded()
        locationProvider = app?.locationProvider

        //        if (followLocationButton != null) {
//            followLocationButton.setOnClickListener(v -> toggleFollowLocation());
//            updateFollowLocationButtonState();
//        }
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

        setupVehicleModeButtons()

        mapTileView = app?.osmandMap?.mapView
        mapTileView?.setupRenderingView()

//        val toolbar = findViewById<Toolbar>(R.id.toolbar)
//        toolbar.title = "Navigate map"
//        toolbar.setNavigationIcon(AndroidUtils.getNavigationIconResId(this))
//        toolbar.setNavigationOnClickListener { onBackPressed() }

        //		CompoundButton openglSwitch = findViewById(R.id.opengl_switch);
//		openglSwitch.setChecked(app?.getSettings().USE_OPENGL_RENDER.get());
//		openglSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
//			app?.getSettings().USE_OPENGL_RENDER.set(isChecked);
//			RestartActivity.doRestart(this);
//		});

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
            Log.d("minh", "Indexes not loaded yet. Triggering reload...")
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
            Log.d("minh", " list:" + list.size)

            return list

        } catch (e: IOException) {
            Log.d("minh", " IOException:" + e.printStackTrace())
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

        val start = LatLon(currentLocation.latitude, currentLocation.longitude)

        val settings = app?.settings ?: return
        val routingHelper = app?.routingHelper ?: return
        settings.setApplicationMode(applicationMode)
        val targetPointsHelper = app?.targetPointsHelper ?: return

        targetPointsHelper.setStartPoint(
            start, false, PointDescription(start.latitude, start.longitude)
        )
        targetPointsHelper.navigateToPoint(
            finish!!, true, -1, PointDescription(finish!!.latitude, finish!!.longitude)
        )

        app?.osmandMap?.mapActions?.enterRoutePlanningModeGivenGpx(null, start, null, true, false)

        settings.FOLLOW_THE_ROUTE.set(true)
        routingHelper.isFollowingMode = true
        routingHelper.isRoutePlanningMode = false
        routingHelper.notifyIfRouteIsCalculated()
        routingHelper.setCurrentLocation(currentLocation, false)

        OsmAndLocationProvider.requestFineLocationPermissionIfNeeded(this)

        app?.showShortToastMessage("Navigation started from current location to " + finish!!.latitude + ", " + finish!!.longitude)

        navigationActive = true
        //        followLocationEnabled = true;
        updateFollowLocationButtonState()
        updateStartStopButtonState()

        refreshRouteInfoView()
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
            //            nextTurnText.setText(instruction != null ? instruction : getString(R.string.route_info_no_turn));
            updateTurnIcon(info.directionInfo, instruction)
        } else {
            nextTurnDistanceText?.setText(R.string.route_info_distance_placeholder)
            //            nextTurnText.setText(R.string.route_info_no_turn);
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
        } catch (e: Exception) {
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
        if (lower.contains("left") || lower.contains("rẽ trái") || lower.contains("quẹo trái")) {
            return R.drawable.ic_turn_left
        } else if (lower.contains("right") || lower.contains("rẽ phải") || lower.contains("quẹo phải")) {
            return R.drawable.ic_turn_right
        } else if (lower.contains("straight") || lower.contains("thẳng") || lower.contains("tiếp tục")) {
            return R.drawable.ic_turn_straight
        } else if (lower.contains("u-turn") || lower.contains("quay đầu")) {
            return R.drawable.ic_turn_uturn
        }
        return R.drawable.ic_turn_straight
    }

    private fun toggleFollowLocation() {
        followLocationEnabled = !followLocationEnabled
        updateFollowLocationButtonState()
        if (followLocationEnabled && locationProvider != null) {
            val lastKnown = locationProvider!!.lastKnownLocation
            if (lastKnown != null) {
                centerMapOnLocation(lastKnown)
            }
        }
    }

    private fun updateFollowLocationButtonState() {
        if (followLocationButton == null) {
            return
        }
        followLocationButton.setText(
            if (followLocationEnabled) R.string.follow_location_disable
            else R.string.follow_location_enable
        )
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
        //        overViewNavigationCameraButton.setVisibility(navigationActive ? VISIBLE : GONE); //Todo update logic overview
    }


    private fun downloadMapIfNeed(location: Location?) {
        if (location == null || isIndexReady.not() || hasDownloadIndex) {
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

        if (routingHelper != null) {
            routingHelper.isFollowingMode = false
            routingHelper.isRoutePlanningMode = false
            routingHelper.clearCurrentRoute(null, null)
        }
        targetPointsHelper?.clearAllPoints(true)

        navigationActive = false
        finish = null
        routeInfoContainer?.visibility = View.GONE
        updateStartStopButtonState()
    }

    private fun setupVehicleModeButtons() {
        if (modeCarButton == null || modeBikeButton == null || modeFootButton == null) {
            return
        }

        updateVehicleModeSelection(applicationMode)

        modeCarButton?.setOnClickListener { v: View? ->
            onVehicleModeSelected(
                ApplicationMode.CAR
            )
        }
        modeBikeButton?.setOnClickListener { v: View? ->
            onVehicleModeSelected(
                ApplicationMode.BICYCLE
            )
        }
        modeFootButton?.setOnClickListener { v: View? ->
            onVehicleModeSelected(
                ApplicationMode.PEDESTRIAN
            )
        }
    }

    private fun onVehicleModeSelected(mode: ApplicationMode) {
        val settings = app?.settings
        val routingHelper = app?.routingHelper

        settings?.setApplicationMode(mode)
        if (routingHelper != null) {
            routingHelper.appMode = mode
            if (routingHelper.isRouteCalculated) {
                routingHelper.recalculateRouteDueToSettingsChange(true)
            }
        }
        updateVehicleModeSelection(mode)
    }

    private fun updateVehicleModeSelection(mode: ApplicationMode?) {
        if (modeCarButton == null || modeBikeButton == null || modeFootButton == null || mode == null) {
            return
        }
        modeCarButton?.isChecked = mode === ApplicationMode.CAR
        modeBikeButton?.isChecked = mode === ApplicationMode.BICYCLE
        modeFootButton?.isChecked = mode === ApplicationMode.PEDESTRIAN
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
                    Log.d("minh", "Map downloaded: " + item.fileName)
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
        hasDownloadIndex = true
        val latLon = LatLon(location.latitude, location.longitude)
        if (areIndexesReady().not()) {
            ensureIndexesLoaded()
        } else {
            // Get maps at location
            val maps: List<IndexItem> =
                getMapsAtLocationSafely(latLon, DownloadActivityType.NORMAL_FILE)
            Log.d("minh", "Found " + maps.size + " maps at location")

            // Download the first map if available
            if (maps.isNotEmpty()) {
                val firstMap = maps[0]
                // Check if already downloaded
                if (firstMap.isDownloaded) {

                    Log.d("minh", "Map already downloaded: " + firstMap.fileName)
                } else if (isDownloading(firstMap)) {
                    Log.d(
                        "minh",
                        "Map is currently downloading: " + firstMap.fileName
                    )
                } else {
                    downloadIndexItem(firstMap)
                }
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
            Log.w("minh", "Regions not initialized yet. Cannot query maps.")
            return emptyList()
        }

        // Check if indexes are loaded (groupByRegion is populated)
        val downloadThread = app.downloadThread
        val indexes = downloadThread.indexes

        // If indexes haven't been loaded from internet, try to reload them
        if (!indexes.isDownloadedFromInternet && !indexes.downloadFromInternetFailed) {
            Log.d("minh", "Indexes not loaded yet. Triggering reload...")
            downloadThread.runReloadIndexFilesSilent()
            // Return empty list for now, indexes will be available after reload completes
            return emptyList()
        }

        try {
            val items = DownloadResources.findIndexItemsAt(app, latLon, type)
            if (items.isEmpty()) {
                Log.d(
                    "minh",
                    "No maps found at location: " + latLon + ". Indexes loaded: " + indexes.isDownloadedFromInternet
                )
            }
            return items
        } catch (e: IOException) {
            Log.e("minh", "Error finding maps at location: $latLon", e)
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
        val overlayName = "Shigira Resort Map"
        val urlTemplate = "https://shigira-resort-map.resort-cloud.com/hot/{z}/{x}/{y}.png"
        addMapOverlay(overlayName, urlTemplate, 1, 18)

    }

    private fun setMapLanguage(localeCode: String): Boolean {
        try {
            val settings = app.settings
            // Set map preferred locale
            settings.MAP_PREFERRED_LOCALE.set(localeCode)

            app.osmandMap.mapView.refreshMapComplete()

            Log.d(
                "NavigationActivity",
                "✅ Map language set to: " + (if (localeCode.isEmpty()) "native/local names" else localeCode)
            )
            return true
        } catch (e: java.lang.Exception) {
            Log.e("NavigationActivity", "Error setting map language: $localeCode", e)
            return false
        }
    }

    private fun isOverlayActive(overlayNameValue: String): Boolean {
        return overlayNameValue == app.settings.MAP_OVERLAY.get()
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

            // Apply overlay immediately (không cần MapActivity)
            // Plugin có thể access map view trực tiếp từ app.getOsmandMap().getMapView()
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


            // Get overlay tile source từ settings
            val overlayName: String = settings.MAP_OVERLAY.get()
            if (overlayName.isEmpty()) {
                // Remove overlay nếu setting là null
                removeOverlayLayer()
                return
            }

            val tileSource: ITileSource? = settings.getTileSourceByName(overlayName, false)
            if (tileSource == null) {
                Log.w("NavigationActivity", "⚠️ Tile source not found: $overlayName")
                return
            }


            // Tạo overlay layer nếu chưa có
            if (overlayLayer == null) {
                overlayLayer = MapTileLayer(app, false)
            }


            // Check if layer already has this map
//            if (tileSource == overlayLayer?.map) {
//                Log.d("NavigationActivity", "Overlay already applied")
//                return
//            }


            // Add layer vào map view nếu chưa có
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
    }

    override fun onDestroy() {
        super.onDestroy()
        resetAllData()

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
//        setVoiceEnabled(false)
        addShigiraResortMapOverlay() //TODO only use for nansei
    }

    fun enableVoice() {
        setVoiceEnabled(true)
    }

    fun setVoiceEnabled(enabled: Boolean) {
        val settings = app.settings
        val appMode = app.routingHelper.appMode

        settings.VOICE_MUTE.setModeValue(appMode, !enabled)
        app.routingHelper.voiceRouter.setMuteForMode(appMode, !enabled)

        Log.d("NavigationActivity", if (enabled) "✅ Voice enabled" else "🔇 Voice disabled")
    }

    private fun setupDownloadListener() {
        val downloadThread = app?.downloadThread
        downloadThread?.setUiActivity(this) // Set activity làm listener
    }

    private fun setupRouteListener() {
        routeListener = this
        routeListener?.let {
            app?.routingHelper?.addListener(it)
        }
    }

    override fun onUpdatedIndexesList() {
        super.onUpdatedIndexesList()
        Log.d("minh", "onUpdatedIndexesList")
        isIndexReady = true
    }

    override fun downloadInProgress() {
        super.downloadInProgress()
        val task = app?.downloadThread?.currentRunningTask?.downloadProgress
        showDownloadIndexProgress(task ?: 0f)
    }

    override fun downloadingError(error: String) {
        super.downloadingError(error)
        Log.d("minh", "downloadingError")
        hasDownloadIndex = false
        hideDownloadIndexProgress()
    }

    override fun downloadHasFinished() {
        super.downloadHasFinished()
        Log.d("minh", "downloadHasFinished")
        hideDownloadIndexProgress()
        setMapLanguage("ja")
        app.downloadThread.updateLoadedFiles()
        addShigiraResortMapOverlay()
        refreshUIAfterDownload()
    }

    override fun newRouteIsCalculated(p0: Boolean, p1: ValueHolder<Boolean>?) {
    }

    override fun routeWasCancelled() {
    }

    override fun routeWasFinished() {
        stopNavigation()
        updateFollowAndOverViewButtonState()
        updateFollowLocationButtonState()
    }


}