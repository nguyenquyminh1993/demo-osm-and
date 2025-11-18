package net.osmand.library.sample;

import static net.osmand.plus.utils.InsetsUtils.InsetSide.BOTTOM;
import static net.osmand.plus.utils.InsetsUtils.InsetSide.LEFT;
import static net.osmand.plus.utils.InsetsUtils.InsetSide.RIGHT;
import static net.osmand.plus.utils.InsetsUtils.InsetSide.TOP;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import net.osmand.Location;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmAndLocationProvider.OsmAndLocationListener;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.OsmandActionBarActivity;
import net.osmand.plus.activities.RestartActivity;
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

public class NavigateMapActivity extends OsmandActionBarActivity {

	private OsmandApplication app;
	private OsmandMapTileView mapTileView;
	private MapViewWithLayers mapViewWithLayers;
	private OnLongClickListener clickListener;
	private View routeInfoContainer;
	private TextView nextTurnIconText;
	private TextView nextTurnDistanceText;
	private TextView nextTurnText;
	private TextView remainingDistanceText;
	private MaterialButton followLocationButton;
	private MaterialButton startStopNavigationButton;
	private MaterialButton modeCarButton;
	private MaterialButton modeBikeButton;
	private MaterialButton modeFootButton;
	private boolean followLocationEnabled;
	private boolean navigationActive;
	private final NextDirectionInfo nextDirectionInfo = new NextDirectionInfo();
	private final IRoutingDataUpdateListener routingDataUpdateListener = () -> runOnUiThread(this::refreshRouteInfoView);
	private OsmAndLocationProvider locationProvider;
	private final OsmAndLocationListener locationListener = location -> runOnUiThread(() -> onUserLocationUpdated(location));

	private LatLon start;
	private LatLon finish;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_map_activity);
		mapViewWithLayers = findViewById(R.id.map_view_with_layers);
		routeInfoContainer = findViewById(R.id.route_info_container);
		nextTurnIconText = findViewById(R.id.next_turn_icon_text);
		nextTurnDistanceText = findViewById(R.id.next_turn_distance_text);
		nextTurnText = findViewById(R.id.next_turn_text);
		remainingDistanceText = findViewById(R.id.remaining_distance_text);
		followLocationButton = findViewById(R.id.follow_location_button);
		startStopNavigationButton = findViewById(R.id.start_stop_navigation_button);
		modeCarButton = findViewById(R.id.mode_car_button);
		modeBikeButton = findViewById(R.id.mode_bike_button);
		modeFootButton = findViewById(R.id.mode_foot_button);

		app = (OsmandApplication) getApplication();

		locationProvider = app.getLocationProvider();
		if (followLocationButton != null) {
			followLocationButton.setOnClickListener(v -> toggleFollowLocation());
			updateFollowLocationButtonState();
		}

		if (startStopNavigationButton != null) {
			startStopNavigationButton.setOnClickListener(v -> {
				if (navigationActive) {
					stopNavigation();
				} else {
					startNavigation();
				}
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

		CompoundButton openglSwitch = findViewById(R.id.opengl_switch);
		openglSwitch.setChecked(app.getSettings().USE_OPENGL_RENDER.get());
		openglSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			app.getSettings().USE_OPENGL_RENDER.set(isChecked);
			RestartActivity.doRestart(this);
		});
		//set start location and zoom for map
		mapTileView.setIntZoom(14);
		mapTileView.setLatLon(24.717957, 125.344340);
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
			if (followLocationEnabled && lastKnown != null) {
				centerMapOnLocation(lastKnown);
			}
		}
		refreshRouteInfoView();
	}

	private OnLongClickListener getClickListener() {
		if (clickListener == null) {
			clickListener = point -> {
				RotatedTileBox tileBox = mapTileView.getCurrentRotatedTileBox();
				LatLon latLon = NativeUtilities.getLatLonFromPixel(mapTileView.getMapRenderer(), tileBox, point.x, point.y);

				if (start == null) {
					start = latLon;
					app.showShortToastMessage("Start point " + latLon.getLatitude() + " " + latLon.getLongitude());
				} else if (finish == null) {
					finish = latLon;
					app.showShortToastMessage("Finish point " + latLon.getLatitude() + " " + latLon.getLongitude());
					updateStartStopButtonState();
				}
				return true;
			};
		}
		return clickListener;
	}

	private void startNavigation() {
		if (start == null || finish == null) {
			app.showShortToastMessage("Chọn điểm bắt đầu và điểm kết thúc trước");
			return;
		}
		OsmandSettings settings = app.getSettings();
		RoutingHelper routingHelper = app.getRoutingHelper();
		settings.setApplicationMode(ApplicationMode.CAR);

		TargetPointsHelper targetPointsHelper = app.getTargetPointsHelper();

		targetPointsHelper.setStartPoint(start, false, new PointDescription(start.getLatitude(), start.getLongitude()));
		targetPointsHelper.navigateToPoint(finish, true, -1, new PointDescription(finish.getLatitude(), finish.getLongitude()));

		app.getOsmandMap().getMapActions().enterRoutePlanningModeGivenGpx(null, start, null, true, false);

		settings.FOLLOW_THE_ROUTE.set(true);
		routingHelper.setFollowingMode(true);
		routingHelper.setRoutePlanningMode(false);
		routingHelper.notifyIfRouteIsCalculated();
		routingHelper.setCurrentLocation(app.getLocationProvider().getLastKnownLocation(), false);

		OsmAndLocationProvider.requestFineLocationPermissionIfNeeded(this);

		app.showShortToastMessage("StartNavigation from " + start.getLatitude() + " " + start.getLongitude()
				+ " to " + finish.getLatitude() + " " + finish.getLongitude());

		navigationActive = true;
		followLocationEnabled = true;
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
			routeInfoContainer.setVisibility(View.GONE);
			return;
		}

		routeInfoContainer.setVisibility(View.VISIBLE);

		NextDirectionInfo info = routingHelper.getNextRouteDirectionInfo(nextDirectionInfo, false);
		if (info != null && info.directionInfo != null) {
			String instruction = info.directionInfo.getDescriptionRoute(app);
			String distanceToTurn = OsmAndFormatter.getFormattedDistance(Math.max(info.distanceTo, 0), app);
			nextTurnDistanceText.setText(getString(R.string.route_info_next_turn_format, distanceToTurn));
			nextTurnText.setText(instruction != null ? instruction : getString(R.string.route_info_no_turn));
			updateTurnIcon(instruction);
		} else {
			nextTurnDistanceText.setText(R.string.route_info_distance_placeholder);
			nextTurnText.setText(R.string.route_info_no_turn);
			if (nextTurnIconText != null) {
				nextTurnIconText.setText("⬆");
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

	private void updateTurnIcon(@Nullable String instruction) {
		if (nextTurnIconText == null) {
			return;
		}
		if (instruction == null) {
			nextTurnIconText.setText("⬆");
			return;
		}
		String lower = instruction.toLowerCase(java.util.Locale.getDefault());
		if (lower.contains("left")) {
			nextTurnIconText.setText("↰");
		} else if (lower.contains("right")) {
			nextTurnIconText.setText("↱");
		} else {
			nextTurnIconText.setText("⬆");
		}
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

	private void updateStartStopButtonState() {
		if (startStopNavigationButton == null) {
			return;
		}
		boolean canStart = start != null && finish != null;
		startStopNavigationButton.setEnabled(canStart || navigationActive);
		startStopNavigationButton.setText(navigationActive
				? R.string.stop_navigation
				: R.string.start_navigation);
	}

	private void onUserLocationUpdated(@Nullable Location location) {
		if (location == null) {
			return;
		}
		RoutingHelper routingHelper = app.getRoutingHelper();
		if (routingHelper != null) {
			routingHelper.setCurrentLocation(location, false);
		}
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
		routeInfoContainer.setVisibility(View.GONE);
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
	}
}