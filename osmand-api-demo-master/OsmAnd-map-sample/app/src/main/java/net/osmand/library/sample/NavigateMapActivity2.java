package net.osmand.library.sample;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static net.osmand.plus.utils.InsetsUtils.InsetSide.BOTTOM;
import static net.osmand.plus.utils.InsetsUtils.InsetSide.LEFT;
import static net.osmand.plus.utils.InsetsUtils.InsetSide.RIGHT;
import static net.osmand.plus.utils.InsetsUtils.InsetSide.TOP;

import android.annotation.SuppressLint;

import net.osmand.Location;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.AppInitializeListener;
import net.osmand.plus.AppInitializer;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.OsmandActionBarActivity;
import net.osmand.plus.base.MapViewTrackingUtilities;
import net.osmand.plus.download.DownloadActivityType;
import net.osmand.plus.download.DownloadIndexesThread;
import net.osmand.plus.download.DownloadResources;
import net.osmand.plus.download.DownloadValidationManager;
import net.osmand.plus.download.IndexItem;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.TargetPointsHelper;
import net.osmand.plus.routing.IRoutingDataUpdateListener;
import net.osmand.plus.routing.NextDirectionInfo;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.InsetTarget;
import net.osmand.plus.utils.InsetTarget.InsetTargetBuilder;
import net.osmand.plus.utils.InsetsUtils;
import net.osmand.plus.utils.NativeUtilities;
import net.osmand.plus.utils.OsmAndFormatter;
import net.osmand.plus.views.MapViewWithLayers;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.OsmandMapTileView.OnLongClickListener;

import java.io.IOException;
import java.util.List;

public class NavigateMapActivity2 extends OsmandActionBarActivity implements AppInitializeListener, DownloadIndexesThread.DownloadEvents {

//    private OsmandApplication app;
    private OsmandMapTileView mapTileView;
    private MapViewWithLayers mapViewWithLayers;
    private OnLongClickListener clickListener;
    private View routeInfoContainer;
    private ImageView nextTurnIcon;
    private TextView nextTurnDistanceText;
    private TextView nextTurnText;
    private TextView remainingDistanceText;
    private MaterialButton followLocationButton;
    private Button followNavigationCameraButton;
    private Button overViewNavigationCameraButton;
    private Button startStopNavigationButton;
    private MaterialButton modeCarButton;
    private MaterialButton modeBikeButton;
    private MaterialButton modeFootButton;
    private boolean followLocationEnabled;
    private boolean navigationActive;
    private final NextDirectionInfo nextDirectionInfo = new NextDirectionInfo();
    private final IRoutingDataUpdateListener routingDataUpdateListener = () -> runOnUiThread(this::refreshRouteInfoView);
    private OsmAndLocationProvider locationProvider;
    private final OsmAndLocationListener locationListener = location -> runOnUiThread(() -> onUserLocationUpdated(location));

    private LatLon finish;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.simple_map_activity);
        mapViewWithLayers = findViewById(R.id.map_view_with_layers);
        routeInfoContainer = findViewById(R.id.route_info_container);
        nextTurnIcon = findViewById(R.id.next_turn_icon);
        nextTurnDistanceText = findViewById(R.id.next_turn_distance_text);
//        nextTurnText = findViewById(R.id.next_turn_text);
        remainingDistanceText = findViewById(R.id.remaining_distance_text);
        followNavigationCameraButton = findViewById(R.id.follow_camera);
        overViewNavigationCameraButton = findViewById(R.id.btn_over_view);
        startStopNavigationButton = findViewById(R.id.start_stop_navigation_button);
        modeCarButton = findViewById(R.id.mode_car_button);
        modeBikeButton = findViewById(R.id.mode_bike_button);
        modeFootButton = findViewById(R.id.mode_foot_button);

        app = (OsmandApplication) getApplication();
        app.getAppInitializer().addListener(this);
        setupDownloadListener();
        locationProvider = app.getLocationProvider();
//        if (followLocationButton != null) {
//            followLocationButton.setOnClickListener(v -> toggleFollowLocation());
//            updateFollowLocationButtonState();
//        }

        if (followNavigationCameraButton != null) {
            followNavigationCameraButton.setOnClickListener(v -> followNavigationCamera());
            followNavigationCameraButton.setVisibility(GONE);
        }
        if (overViewNavigationCameraButton != null) {
            overViewNavigationCameraButton.setOnClickListener(v -> overviewCamera());
            overViewNavigationCameraButton.setVisibility(GONE);
        }

        if (startStopNavigationButton != null) {
            startStopNavigationButton.setOnClickListener(v -> {
                if (navigationActive) {
                    stopNavigation();
                } else {
                    startNavigation();
                }
                updateFollowAndOverViewButtonState();
            });
            updateStartStopButtonState();
        }

        setupVehicleModeButtons();

        mapTileView = app.getOsmandMap().getMapView();
        mapTileView.setupRenderingView();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Navigate map");
        toolbar.setNavigationIcon(AndroidUtils.getNavigationIconResId(app));
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

//		CompoundButton openglSwitch = findViewById(R.id.opengl_switch);
//		openglSwitch.setChecked(app.getSettings().USE_OPENGL_RENDER.get());
//		openglSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
//			app.getSettings().USE_OPENGL_RENDER.set(isChecked);
//			RestartActivity.doRestart(this);
//		});

        // Initialize map with user location if available
        if (locationProvider != null) {
            Location lastKnown = locationProvider.getLastKnownLocation();
            if (lastKnown != null) {
                mapTileView.setIntZoom(17);
                mapTileView.setLatLon(lastKnown.getLatitude(), lastKnown.getLongitude());
            } else {
                mapTileView.setIntZoom(17);
                mapTileView.setLatLon(24.717957, 125.344340);
            }
        } else {
            mapTileView.setIntZoom(14);
            mapTileView.setLatLon(24.717957, 125.344340);
        }
    }

    private void getIndexItems(LatLon latLon){

        DownloadIndexesThread downloadThread = app.getDownloadThread();
        DownloadResources indexes = downloadThread.getIndexes();

        // If indexes haven't been loaded from internet, try to reload them
        if (!indexes.isDownloadedFromInternet && !indexes.downloadFromInternetFailed) {
            Log.d("minh", "Indexes not loaded yet. Triggering reload...");
            downloadThread.runReloadIndexFilesSilent();
            // Return empty list for now, indexes will be available after reload completes
            return ;
        }
        try {
            final List<IndexItem> list  =  DownloadResources.findIndexItemsAt(app, latLon, DownloadActivityType.NORMAL_FILE);
            Log.d("minh"," list:" + list.size());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateStatusBarColor() {
        int color = AndroidUtils.getColorFromAttr(this, android.R.attr.colorPrimary);
        if (color != -1) {
            AndroidUiHelper.setStatusBarColor(this, color);
        }
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();

        View root = findViewById(R.id.root);
        InsetTargetBuilder builder = InsetTarget.builder(root)
                .portraitSides(BOTTOM, TOP)
                .landscapeSides(TOP, RIGHT, LEFT);

        InsetsUtils.setWindowInsetsListener(root, (view, windowInsetsCompat)
                -> InsetsUtils.applyPadding(view, windowInsetsCompat, builder.build()), true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    @SuppressLint("SyntheticAccessor")
    protected void onResume() {
        super.onResume();
        mapViewWithLayers.onResume();
        mapTileView.setOnLongClickListener(getClickListener());
        app.getRoutingHelper().addRouteDataListener(routingDataUpdateListener);
        if (locationProvider != null) {
            locationProvider.addLocationListener(locationListener);
            locationProvider.resumeAllUpdates();
            Location lastKnown = locationProvider.getLastKnownLocation();
            if (lastKnown != null) {
                if (followLocationEnabled || !navigationActive) {
                    centerMapOnLocation(lastKnown);
                }
            }
        }
        refreshRouteInfoView();
    }

    private OnLongClickListener getClickListener() {
        if (clickListener == null) {
            clickListener = point -> {
                if (navigationActive) {
                    // Don't allow changing destination during navigation
                    return false;
                }
                RotatedTileBox tileBox = mapTileView.getCurrentRotatedTileBox();
                LatLon latLon = NativeUtilities.getLatLonFromPixel(mapTileView.getMapRenderer(), tileBox, point.x, point.y);

                finish = latLon;
                app.showShortToastMessage("Destination: " + latLon.getLatitude() + ", " + latLon.getLongitude());
                updateStartStopButtonState();
                return true;
            };
        }
        return clickListener;
    }

    private void startNavigation() {
        if (finish == null) {
            app.showShortToastMessage("Please select destination by long clicking on the map");
            return;
        }

        Location currentLocation = locationProvider != null ? locationProvider.getLastKnownLocation() : null;
        if (currentLocation == null) {
            app.showShortToastMessage("Unable to get current location. Please enable location services.");
            return;
        }

        LatLon start = new LatLon(currentLocation.getLatitude(), currentLocation.getLongitude());

        OsmandSettings settings = app.getSettings();
        RoutingHelper routingHelper = app.getRoutingHelper();
        settings.setApplicationMode(ApplicationMode.PEDESTRIAN);

        TargetPointsHelper targetPointsHelper = app.getTargetPointsHelper();

        targetPointsHelper.setStartPoint(start, false, new PointDescription(start.getLatitude(), start.getLongitude()));
        targetPointsHelper.navigateToPoint(finish, true, -1, new PointDescription(finish.getLatitude(), finish.getLongitude()));

        app.getOsmandMap().getMapActions().enterRoutePlanningModeGivenGpx(null, start, null, true, false);

        settings.FOLLOW_THE_ROUTE.set(true);
        routingHelper.setFollowingMode(true);
        routingHelper.setRoutePlanningMode(false);
        routingHelper.notifyIfRouteIsCalculated();
        routingHelper.setCurrentLocation(currentLocation, false);

        OsmAndLocationProvider.requestFineLocationPermissionIfNeeded(this);

        app.showShortToastMessage("Navigation started from current location to " + finish.getLatitude() + ", " + finish.getLongitude());

        navigationActive = true;
//        followLocationEnabled = true;
        updateFollowLocationButtonState();
        updateStartStopButtonState();

        refreshRouteInfoView();
    }

    private void refreshRouteInfoView() {
        if (routeInfoContainer == null) {
            return;
        }

        RoutingHelper routingHelper = app.getRoutingHelper();
        if (routingHelper == null || !routingHelper.isRouteCalculated() || !navigationActive) {
            routeInfoContainer.setVisibility(GONE);
            return;
        }

        routeInfoContainer.setVisibility(VISIBLE);

        NextDirectionInfo info = routingHelper.getNextRouteDirectionInfo(nextDirectionInfo, false);
        if (info != null && info.directionInfo != null) {
            String instruction = info.directionInfo.getDescriptionRoute(app);
            String distanceToTurn = OsmAndFormatter.getFormattedDistance(Math.max(info.distanceTo, 0), app);
            nextTurnDistanceText.setText(getString(R.string.route_info_next_turn_format, distanceToTurn));
//            nextTurnText.setText(instruction != null ? instruction : getString(R.string.route_info_no_turn));
            updateTurnIcon(info.directionInfo, instruction);
        } else {
            nextTurnDistanceText.setText(R.string.route_info_distance_placeholder);
//            nextTurnText.setText(R.string.route_info_no_turn);
            if (nextTurnIcon != null) {
                nextTurnIcon.setImageResource(R.drawable.ic_turn_straight);
            }
        }

        int remaining = routingHelper.getLeftDistance();
        if (remaining > 0) {
            String remainingFormatted = OsmAndFormatter.getFormattedDistance(remaining, app);
            String timeFormatted = OsmAndFormatter.getFormattedDurationShortMinutes(routingHelper.getLeftTime());

            remainingDistanceText.setText(getString(R.string.route_info_remaining_with_time_format,
                    remainingFormatted, timeFormatted));
        } else {
            remainingDistanceText.setText(R.string.route_info_remaining_placeholder);
        }
    }

    private void updateTurnIcon(@Nullable Object directionInfo, @Nullable String instruction) {
        if (nextTurnIcon == null) {
            return;
        }

        int iconResId = R.drawable.ic_turn_straight; // default

        // Try to get TurnType from DirectionInfo using reflection
        try {
            if (directionInfo != null) {
                java.lang.reflect.Field turnTypeField = directionInfo.getClass().getDeclaredField("turnType");
                turnTypeField.setAccessible(true);
                Object turnType = turnTypeField.get(directionInfo);

                if (turnType != null) {
                    String turnTypeStr = turnType.toString();
                    iconResId = getTurnIconResource(turnTypeStr);
                }
            }
        } catch (Exception e) {
            // Fallback to instruction text parsing
            if (instruction != null) {
                iconResId = getTurnIconFromInstruction(instruction);
            }
        }

        nextTurnIcon.setImageResource(iconResId);
    }

    private int getTurnIconResource(String turnType) {
        if (turnType == null) {
            return R.drawable.ic_turn_straight;
        }
        String lower = turnType.toLowerCase();
        if (lower.contains("left") || lower.contains("tl") || lower.contains("tsll")) {
            return R.drawable.ic_turn_left;
        } else if (lower.contains("right") || lower.contains("tr") || lower.contains("tslr")) {
            return R.drawable.ic_turn_right;
        } else if (lower.contains("straight") || lower.contains("ts") || lower.contains("c")) {
            return R.drawable.ic_turn_straight;
        } else if (lower.contains("uturn") || lower.contains("tu")) {
            return R.drawable.ic_turn_uturn;
        }
        return R.drawable.ic_turn_straight;
    }

    private int getTurnIconFromInstruction(String instruction) {
        if (instruction == null) {
            return R.drawable.ic_turn_straight;
        }
        String lower = instruction.toLowerCase(java.util.Locale.getDefault());
        if (lower.contains("left") || lower.contains("rẽ trái") || lower.contains("quẹo trái")) {
            return R.drawable.ic_turn_left;
        } else if (lower.contains("right") || lower.contains("rẽ phải") || lower.contains("quẹo phải")) {
            return R.drawable.ic_turn_right;
        } else if (lower.contains("straight") || lower.contains("thẳng") || lower.contains("tiếp tục")) {
            return R.drawable.ic_turn_straight;
        } else if (lower.contains("u-turn") || lower.contains("quay đầu")) {
            return R.drawable.ic_turn_uturn;
        }
        return R.drawable.ic_turn_straight;
    }

    private void toggleFollowLocation() {
        followLocationEnabled = !followLocationEnabled;
        updateFollowLocationButtonState();
        if (followLocationEnabled && locationProvider != null) {
            Location lastKnown = locationProvider.getLastKnownLocation();
            if (lastKnown != null) {
                centerMapOnLocation(lastKnown);
            }
        }
    }

    private void updateFollowLocationButtonState() {
        if (followLocationButton == null) {
            return;
        }
        followLocationButton.setText(followLocationEnabled
                ? R.string.follow_location_disable
                : R.string.follow_location_enable);
    }

    private void overviewCamera() {
        if (!navigationActive) {
            return;
        }
        RoutingHelper routingHelper = app.getRoutingHelper();
        Location currentLocation = locationProvider != null ? locationProvider.getLastKnownLocation() : null;
        if (currentLocation == null) {
            app.showShortToastMessage("Unable to get current location. Please enable location services.");
            return;
        }
        app.getSettings().FOLLOW_THE_ROUTE.set(false);
        routingHelper.setFollowingMode(false);
        routingHelper.notifyIfRouteIsCalculated();
        routingHelper.setCurrentLocation(currentLocation, false);
        MapViewTrackingUtilities mapTrackingUtilities = app.getMapViewTrackingUtilities();
        mapTrackingUtilities.switchRoutePlanningMode();
        app.getOsmandMap().getMapView().refreshMap();
    }

    private void followNavigationCamera() {
        RoutingHelper routingHelper = app.getRoutingHelper();
        Location currentLocation = locationProvider != null ? locationProvider.getLastKnownLocation() : null;

        if (currentLocation == null) {
            app.showShortToastMessage("Unable to get current location. Please enable location services.");
            return;
        }
        app.getSettings().FOLLOW_THE_ROUTE.set(true);
        routingHelper.setFollowingMode(true);
        routingHelper.setRoutePlanningMode(false);
        routingHelper.notifyIfRouteIsCalculated();
        routingHelper.setCurrentLocation(currentLocation, false);
        MapViewTrackingUtilities mapTrackingUtilities = app.getMapViewTrackingUtilities();
        mapTrackingUtilities.switchRoutePlanningMode();
        app.getOsmandMap().getMapView().refreshMap();
    }

    private void updateStartStopButtonState() {
        startStopNavigationButton.setVisibility(finish != null ? VISIBLE : GONE);
        startStopNavigationButton.setText(navigationActive
                ? R.string.stop_navigation
                : R.string.start_navigation);
    }
    private void updateFollowAndOverViewButtonState() {
        followNavigationCameraButton.setVisibility(navigationActive ? VISIBLE : GONE);
//        overViewNavigationCameraButton.setVisibility(navigationActive ? VISIBLE : GONE); //Todo update logic overview
    }


    private void onUserLocationUpdated(@Nullable Location location) {
        if (location == null) {
            return;
        }
        RoutingHelper routingHelper = app.getRoutingHelper();
        if (routingHelper != null) {
            routingHelper.setCurrentLocation(location, false);
        }
        // Follow location if enabled
        if (followLocationEnabled) {
            centerMapOnLocation(location);
        }
        refreshRouteInfoView();
    }

    private void centerMapOnLocation(@NonNull Location location) {
        if (mapTileView != null) {
            mapTileView.setLatLon(location.getLatitude(), location.getLongitude());
        }
    }

    private void stopNavigation() {
        RoutingHelper routingHelper = app.getRoutingHelper();
        TargetPointsHelper targetPointsHelper = app.getTargetPointsHelper();

        if (routingHelper != null) {
            routingHelper.setFollowingMode(false);
            routingHelper.setRoutePlanningMode(false);
            routingHelper.clearCurrentRoute(null, null);
        }
        if (targetPointsHelper != null) {
            targetPointsHelper.clearAllPoints(true);
        }

        navigationActive = false;
        finish = null;
        routeInfoContainer.setVisibility(GONE);
        updateStartStopButtonState();
    }

    private void setupVehicleModeButtons() {
        if (modeCarButton == null || modeBikeButton == null || modeFootButton == null) {
            return;
        }
        OsmandSettings settings = app.getSettings();
        ApplicationMode currentMode = settings.APPLICATION_MODE.get();

        updateVehicleModeSelection(currentMode);

        modeCarButton.setOnClickListener(v -> onVehicleModeSelected(ApplicationMode.CAR));
        modeBikeButton.setOnClickListener(v -> onVehicleModeSelected(ApplicationMode.BICYCLE));
        modeFootButton.setOnClickListener(v -> onVehicleModeSelected(ApplicationMode.PEDESTRIAN));
    }

    private void onVehicleModeSelected(@NonNull ApplicationMode mode) {
        OsmandSettings settings = app.getSettings();
        RoutingHelper routingHelper = app.getRoutingHelper();

        settings.setApplicationMode(mode);
        if (routingHelper != null) {
            routingHelper.setAppMode(mode);
            if (routingHelper.isRouteCalculated()) {
                routingHelper.recalculateRouteDueToSettingsChange(true);
            }
        }
        updateVehicleModeSelection(mode);
    }

    private void updateVehicleModeSelection(@NonNull ApplicationMode mode) {
        if (modeCarButton == null || modeBikeButton == null || modeFootButton == null) {
            return;
        }
        modeCarButton.setChecked(mode == ApplicationMode.CAR);
        modeBikeButton.setChecked(mode == ApplicationMode.BICYCLE);
        modeFootButton.setChecked(mode == ApplicationMode.PEDESTRIAN);
    }

    private void downloadIndexItem(@NonNull IndexItem item) {
        DownloadValidationManager validationManager = new DownloadValidationManager(app);
        validationManager.startDownload(this, item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapViewWithLayers.onPause();
        mapTileView.setOnLongClickListener(null);
        app.getRoutingHelper().removeRouteDataListener(routingDataUpdateListener);
        if (locationProvider != null) {
            locationProvider.removeLocationListener(locationListener);
            locationProvider.pauseAllUpdates();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapViewWithLayers.onDestroy();
        app.getAppInitializer().removeListener(this);

        DownloadIndexesThread downloadThread = app.getDownloadThread();
        downloadThread.resetUiActivity(this);
    }

    @Override
    public void onStart(@NonNull AppInitializer init) {
        AppInitializeListener.super.onStart(init);
    }

    @Override
    public void onFinish(@NonNull AppInitializer init) {
        AppInitializeListener.super.onFinish(init);
        getIndexItems(new LatLon(24.717957, 125.344340));
    }
    private void setupDownloadListener() {
        DownloadIndexesThread downloadThread = app.getDownloadThread();
        downloadThread.setUiActivity(this); // Set activity làm listener
    }

    @Override
    public void onUpdatedIndexesList() {
        DownloadIndexesThread.DownloadEvents.super.onUpdatedIndexesList();
        Log.d("minh","onUpdatedIndexesList");
        getIndexItems(new LatLon(24.717957, 125.344340));

    }

    @Override
    public void downloadInProgress() {
        DownloadIndexesThread.DownloadEvents.super.downloadInProgress();
        final float task =app.getDownloadThread().getCurrentRunningTask().getDownloadProgress();
        Log.d("minh","downloadInProgress " + task);

    }

    @Override
    public void downloadingError(@NonNull String error) {
        DownloadIndexesThread.DownloadEvents.super.downloadingError(error);
        Log.d("minh","downloadingError");

    }

    @Override
    public void downloadHasFinished() {
        DownloadIndexesThread.DownloadEvents.super.downloadHasFinished();
    }
}
