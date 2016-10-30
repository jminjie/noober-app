package com.jminjie.noober;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private Button mRequestDriverButton, mRequestRiderButton, mFinishButton, mCancelButton;
    private TextView mTopText;
    private EditText mUserIdEditText;
    private MapView mMapView;
    private MyLocationNewOverlay mLocationOverlay;
    private ProgressBar mProgressBar;

    private Animation mSlideLeftAnimation, mSlideRightAnimation = null;

    private Handler mHandler;
    private Runnable mRepeatRequest;
    private RequestQueue mRequestQueue;
    private IMapController mMapController;

    final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    final int PERMISSIONS_REQUEST_MULTIPLE = 2;

    final String SERVER_URL = "http://jminjie.com:5000/noober/";
    final String TAG = "MainActivity";

    private Integer mDebugCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get the buttons
        mRequestDriverButton = (Button) findViewById(R.id.requestDriverButton);
        mRequestRiderButton = (Button) findViewById(R.id.requestRiderButton);
        mFinishButton = (Button) findViewById(R.id.finishButton);
        mCancelButton = (Button) findViewById(R.id.cancelButton);
        mTopText = (TextView) findViewById(R.id.topText);
        mUserIdEditText = (EditText) findViewById(R.id.userIdEditText);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);

        // init the request queue
        mRequestQueue = Volley.newRequestQueue(this);

        // handler for delayed repeated requests
        mHandler = new Handler();

        // load animations
        mSlideLeftAnimation = AnimationUtils.loadAnimation(getApplicationContext(),
                R.anim.slide_left_animation);
        mSlideRightAnimation = AnimationUtils.loadAnimation(getApplicationContext(),
                R.anim.slide_right_animation);

        // set up osm
        OpenStreetMapTileProviderConstants.setUserAgentValue(
                BuildConfig.APPLICATION_ID);
        mMapView = (MapView) findViewById(R.id.map);
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapView.setBuiltInZoomControls(true);
        mMapView.setMultiTouchControls(true);
        mMapController = mMapView.getController();
        mMapController.setZoom(17);
        GeoPoint startPoint = new GeoPoint(30.0, -100.0);
        mMapController.setCenter(startPoint);
        mMapView.setTilesScaledToDpi(true);

        this.mLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(getApplicationContext()), mMapView);
        this.mLocationOverlay.enableMyLocation();
        mMapView.getOverlays().add(this.mLocationOverlay);

        // Get permissions
        promptUserForPermissions();
    }

    /**
     * Disable the two request buttons. This function should be called when either request is made
     * so that duplicate requests are not sent by the user
     */
    private void disableRequestButtons() {
        mRequestDriverButton.setEnabled(false);
        mRequestRiderButton.setEnabled(false);
    }

    /**
     * Enable the two request buttons. This function should be called when a trip is finished to
     * allow the user to make the next request
     */
    private void enableRequestButtons() {
        mRequestDriverButton.setEnabled(true);
        mRequestRiderButton.setEnabled(true);
    }

    /**
     * Define the behavior upon receiving a successful response to a rider requesting driver
     * from the server as a JSONObject
     */
    private Response.Listener<JSONObject> driverResponseListener =
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    // Display the response string
                    mTopText.setText(response.toString());
                    mProgressBar.setVisibility(View.GONE);
                }
            };

    /**
     * Define the behavior upon receiving a successful response to driver requesting rider
     * from the server as a JSONObject
     */
    private Response.Listener<JSONObject> riderResponseListener =
            new Response.Listener<JSONObject>() {
        @Override
        public void onResponse(JSONObject response) {
            Log.d(TAG, "onResponse");
            mTopText.setText(response.toString());
            // Show the returned rider's location on the map
            try {
                final boolean matched = response.getBoolean("matched");
                if (matched) {
                    mProgressBar.setVisibility(View.GONE);
                    final GeoPoint riderGeoPoint = new GeoPoint(response.getDouble("lat"),
                            response.getDouble("lon"));
                    final OverlayItem riderLocationOverlayItem = new OverlayItem("", "",
                            riderGeoPoint);
                    ArrayList<OverlayItem> overlayItems = new ArrayList<>();
                    overlayItems.add(riderLocationOverlayItem);
                    final ItemizedIconOverlay<OverlayItem> overlay =
                            new ItemizedIconOverlay<>(overlayItems, null, getApplicationContext());

                    mMapView.getOverlays().add(overlay);
                    mMapController.animateTo(riderGeoPoint);
                }
                // wait 2 seconds and then put another request
                mTopText.setText("Sending request #" + mDebugCount.toString());
                mDebugCount += 1;
                mRepeatRequest = new Runnable() {
                    public void run() {
                        final Location currentLocation = getBestLocation();
                        sendDriverOrRiderRequest("rider", currentLocation.getLatitude(),
                                currentLocation.getLongitude());
                    }
                };
                mHandler.postDelayed(mRepeatRequest, 2000);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                mTopText.setText(e.getMessage());
            }
        }
    };

    /**
     * Define the behavior upon receiving an error from the server
     */
    private Response.ErrorListener responseErrorListener = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            mTopText.setText("responseErrorListener " + error);
        }
    };

    /**
     * Send a request to the server using Volley.RequestQueue. The request contains information
     * on whether a rider or driver is being requested, and the last known location of the user
     * @param driverOrRider = "driver" if requesting a driver, or "rider" if requesting a rider
     * @param lat = the user's latitude
     * @param lon = the user's longitude
     */
    private void sendDriverOrRiderRequest(String driverOrRider, Double lat, Double lon) {
        // form the url
        final String coords = lat.toString() + "," + lon.toString();
        String url = SERVER_URL + driverOrRider + "?coords=" + coords
                + "&userid=" + mUserIdEditText.getText().toString();
        Log.d(TAG, "Sent GET to " + url);

        // create the request
        JsonObjectRequest stringRequest = null;
        if (driverOrRider.equals("driver")) {
            stringRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                    driverResponseListener, responseErrorListener);
        } else if (driverOrRider.equals("rider")) {
            stringRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                    riderResponseListener, responseErrorListener);
        }

        // add the request to the RequestQueue to be sent automatically
        mRequestQueue.add(stringRequest);
    }

    /**
     * Get the best location available
     * @return a Location object with the best available location data
     */
    private Location getBestLocation() {
        Location bestLocation = mLocationOverlay.getLastFix();
        if (bestLocation == null) {
            LocationManager locationManager =
                    (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        return bestLocation;
    }


    /**
     * This function should be called if a rider is requesting a driver by tapping the button
     * Sends the user's location to server/driver
     * @param v the view in which the button is pushed
     */
    public void onRiderRequestDriverTap(View v) {
        // Show the toast
        Toast myToast = Toast.makeText(getApplicationContext(), "Requested Driver",
                Toast.LENGTH_LONG);
        myToast.show();

        // Hide request buttons and reveal finish button
        disableRequestButtons();
        showFinishButton();

        // Turn on progress bar
        mProgressBar.setVisibility(View.VISIBLE);

        // Send request for a driver
        Location userLocation = getBestLocation();
        if (userLocation != null) {
            // show the user overlay
            double currentLatitude = userLocation.getLatitude();
            double currentLongitude = userLocation.getLongitude();
            GeoPoint currentGeoPoint = new GeoPoint(currentLatitude, currentLongitude);
            mMapController.setZoom(23);
            mMapController.animateTo(currentGeoPoint);
            sendDriverOrRiderRequest("driver", currentLatitude, currentLongitude);
        }
    }

    /**
     * This function should be called if a driver is requesting a rider by tapping the button
     * Sends the user's location to server/rider
     * @param v the view in which the button is pushed
     */
    public void onDriverRequestRiderTap(View v) {
        // Show the toast
        Toast myToast = Toast.makeText(getApplicationContext(), "Requested Rider",
                Toast.LENGTH_LONG);
        myToast.show();

        // Hide request buttons and reveal finish button
        disableRequestButtons();
        showFinishButton();

        // Turn on progress bar
        mProgressBar.setVisibility(View.VISIBLE);

        // Send request for a driver
        Location userLocation = getBestLocation();
        if (userLocation != null) {
            double currentLatitude = userLocation.getLatitude();
            double currentLongitude = userLocation.getLongitude();
            sendDriverOrRiderRequest("rider", currentLatitude, currentLongitude);
        }
    }

    /**
     * Slide the finish button into screen.
     * This animation is defined in app/res/anim/slide_left_animation.xml
     */
    private void showFinishButton() {
        if (mFinishButton.getVisibility() == View.VISIBLE)
            return;
        mFinishButton.startAnimation(mSlideLeftAnimation);
        mFinishButton.setVisibility(View.VISIBLE);
    }

    /**
     * Slide the finish button out of screen.
     * This animation is defined in app/res/anim/slide_right_animation.xml
     */
    private void hideFinishButton() {
        if (mFinishButton.getVisibility() != View.VISIBLE)
            return;
        mFinishButton.startAnimation(mSlideRightAnimation);
        mFinishButton.setVisibility(View.INVISIBLE);
    }

    /**
     * Cancels pending outgoing request and sends a request to the server to remove the user from
     * the queue
     * This function should be called when the cancel button is
     * tapped
     * @param v the view in which the button is pushed
     */
    public void onCancelTap(View v) {
        // first cancel pending request
        mHandler.removeCallbacks(mRepeatRequest);
        // then send request to server to remove user from queue
        String url = SERVER_URL + "cancel?userid=" + mUserIdEditText.getText().toString();
        Log.d(TAG, "Sent GET to " + url);

        // create the request
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                    null, responseErrorListener);
        // add the request to the RequestQueue to be sent automatically
        mRequestQueue.add(stringRequest);

        mProgressBar.setVisibility(View.GONE);
        onFinishTap(v);
    }


    /**
     * Enables a driver or rider finished with a trip to make more requests. This function should be
     * called when the finish button is tapped
     * @param v the view in which the button is pushed
     */
    public void onFinishTap(View v) {
        enableRequestButtons();
        hideFinishButton();
    }

    private boolean accessFineLocationGranted() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean writeExternalStorageGranted() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Prompt user to allow the permissions that we need to receive at runtime
     */
    private void promptUserForPermissions() {
        if (!accessFineLocationGranted() && !writeExternalStorageGranted()) {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSIONS_REQUEST_MULTIPLE);
        }
        if (!accessFineLocationGranted()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
        if (!writeExternalStorageGranted()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
    }

    /**
     * Handle the permissions request response. If the permission is granted, continue running the
     * app. Otherwise, close the app immediately.
     * @param requestCode the code passed in to requestPermission
     * @param permissions which permissions were requested
     * @param grantResults corresponding array of PERMISSION_GRANTED | PERMISSION_DENIED
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                // intentional fall through
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length == 0)
                    finish();
                return;
            }
            case PERMISSIONS_REQUEST_MULTIPLE: {
                if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    return;
                } else {
                    finish();
                }
            }
        }
    }
}
