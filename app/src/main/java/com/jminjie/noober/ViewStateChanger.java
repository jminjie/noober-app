package com.jminjie.noober;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.api.IMapController;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;

/**
 * Created by jminjie on 2016-10-31.
 *
 * The ViewStateChanger is part of the application presenter. Use getInstance() to get the singleton
 * instance of the class and call init() before calling methods to set the editable UI elements
 *
 * ViewStateChanger provides methods for changing the view based on the model and user input
 */

class ViewStateChanger {
    // s is the singleton instance
    private static ViewStateChanger s = null;

    // UI elements
    private Button mRequestNooberButton, mCancelButton;
    private MapView mMapView;
    private ProgressBar mProgressBar;
    private IMapController mMapController;
    private Context mContext;
    private MyLocationNewOverlay mLocationOverlay;
    private TextView mTopText;
    private Animation mSlideLeftAnimation, mSlideRightAnimation = null;
    private final Drawable DRAWABLE_DIRECTION_ARROW = null;

    /**
     * Get the singleton instance of the ViewStateChanger
     * @return the singleton instance
     */
    static ViewStateChanger getInstance() {
        if (s == null) {
            s = new ViewStateChanger();
        }
        return s;
    }

    /**
     * Initialize the UI elements which can be changed by the ViewStateChanger
     */
     void init(Button requestNooberbutton, Button cancelButton, MapView mapView,
                     ProgressBar progressBar, IMapController mapController, TextView topText,
                     Context context) {
        // set member variables
        mRequestNooberButton = requestNooberbutton;
        mCancelButton = cancelButton;
        mMapView = mapView;
        mProgressBar = progressBar;
        mMapController = mapController;
        mTopText = topText;
        mContext = context;

        // configure mapView
        OpenStreetMapTileProviderConstants.setUserAgentValue(BuildConfig.APPLICATION_ID);
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapView.setBuiltInZoomControls(true);
        mMapView.setMultiTouchControls(true);
        mMapView.setTilesScaledToDpi(true);

        // create user location overlay
        this.mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(context), mapView);
        this.mLocationOverlay.enableMyLocation();
        mMapView.getOverlays().add(this.mLocationOverlay);
        mMapController.setZoom(17);
        mMapController.setCenter(mLocationOverlay.getMyLocation());

        // load animations
        mSlideLeftAnimation = AnimationUtils.loadAnimation(mContext,
                R.anim.slide_left_animation);
        mSlideRightAnimation = AnimationUtils.loadAnimation(mContext,
                R.anim.slide_right_animation);
    }

    /**
     * @return the MyLocationNewOverlay
     */
    MyLocationNewOverlay getMyLocationNewOverlay() {
        return mLocationOverlay;
    }

    /**
     * Update the view to waiting-for-match state
     */
    void setWaitingForMatch() {
        showCancelButton();
        mRequestNooberButton.setEnabled(false);
        mMapView.getOverlays().clear();
        mProgressBar.setVisibility(View.VISIBLE);
    }

    /**
     * Update the view to idle state
     */
    void setIdle() {
        mProgressBar.setVisibility(View.GONE);
        mRequestNooberButton.setEnabled(true);
        hideCancelButton();
    }

    /**
     * Update the view to waiting-for-pickup state
     * @param lat the matched driver's latitude
     * @param lon the matched driver's longitude
     */
    // TODO show the driver and the user together on the map
    void setWaitingForPickup(double lat, double lon) {
        // Hide the progress bar from WaitingForMatch view state
        mProgressBar.setVisibility(View.GONE);

        // Show the matched driver on the map
        final GeoPoint driverGeoPoint = new GeoPoint(lat, lon);
        final OverlayItem driverLocationOverlayItem = new OverlayItem("Your driver", "",
                driverGeoPoint);
        driverLocationOverlayItem.setMarker(DRAWABLE_DIRECTION_ARROW);
        ArrayList<OverlayItem> overlayItems = new ArrayList<>();
        overlayItems.add(driverLocationOverlayItem);
        final ItemizedIconOverlay<OverlayItem> overlay =
                new ItemizedIconOverlay<>(overlayItems, null, mContext);
        mMapView.getOverlays().add(overlay);
        mMapController.animateTo(driverGeoPoint);
    }

    void setTopText(String text) {
        mTopText.setText(text);
    }

    void showToast(String text) {
        Toast myToast = Toast.makeText(mContext, text, Toast.LENGTH_LONG);
        myToast.show();
    }

    /**
     * Slide the finish button into screen.
     * This animation is defined in app/res/anim/slide_left_animation.xml
     */
    private void showCancelButton() {
        if (mCancelButton.getVisibility() == View.VISIBLE)
            return;
        mCancelButton.startAnimation(mSlideLeftAnimation);
        mCancelButton.setVisibility(View.VISIBLE);
    }

    /**
     * Slide the finish button out of screen.
     * This animation is defined in app/res/anim/slide_right_animation.xml
     */
    private void hideCancelButton() {
        if (mCancelButton.getVisibility() != View.VISIBLE)
            return;
        mCancelButton.startAnimation(mSlideRightAnimation);
        mCancelButton.setVisibility(View.INVISIBLE);
    }
}