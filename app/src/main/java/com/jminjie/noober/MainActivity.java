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
    private Button mRequestNooberButton, mCancelButton;
    private TextView mTopText;
    private EditText mRiderIdEditText;
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
        mRequestNooberButton = (Button) findViewById(R.id.requestDriverButton);
        mCancelButton = (Button) findViewById(R.id.cancelButton);
        mTopText = (TextView) findViewById(R.id.topText);
        mRiderIdEditText = (EditText) findViewById(R.id.userIdEditText);
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
     * Upon receiving a successful response to a request for a Noober:
     *   If the response says we have a match, show the match on the map together with the user
     *     and remove the progress bar
     *   If the response says we have no match, remove the match from the map and display the
     *     progress bar
     *   In 2 seconds send another request with a location update
     */
    private Response.Listener<JSONObject> driverResponseListener =
            new Response.Listener<JSONObject>() {
        @Override
        public void onResponse(JSONObject response) {
            Log.d(TAG, "driverResponseListener.onResponse");
            mTopText.setText(response.toString());
            // Show the returned driver's location on the map
            try {
                final boolean matched = response.getBoolean("matched");
                if (matched) {
                    mProgressBar.setVisibility(View.GONE);
                    final GeoPoint driverGeoPoint = new GeoPoint(response.getDouble("lat"),
                            response.getDouble("lon"));
                    final OverlayItem driverLocationOverlayItem = new OverlayItem("Your driver", "",
                            driverGeoPoint);
                    driverLocationOverlayItem.setMarker(getDrawable(R.drawable.direction_arrow));
                    ArrayList<OverlayItem> overlayItems = new ArrayList<>();
                    overlayItems.add(driverLocationOverlayItem);
                    final ItemizedIconOverlay<OverlayItem> overlay =
                            new ItemizedIconOverlay<>(overlayItems, null, getApplicationContext());

                    mMapView.getOverlays().add(overlay);
                    // TODO show the driver and the user together on the map
                    mMapController.animateTo(driverGeoPoint);
                } else {
                    mMapView.getOverlays().clear();
                    mProgressBar.setVisibility(View.VISIBLE);
                }
                // wait 2 seconds and then put another request
                mTopText.setText("Sending request #" + mDebugCount.toString());
                mDebugCount += 1;
                mRepeatRequest = new Runnable() {
                    public void run() {
                        final Location currentLocation = getBestLocation();
                        sendDriverRequest("driver", currentLocation.getLatitude(),
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
     * Upon receiving a successful response to cancelling, verify that the server has returned
     * matched = false response
     */
    private Response.Listener<JSONObject> cancelResponseListener =
            new Response.Listener<JSONObject>() {
        @Override
        public void onResponse(JSONObject response) {
            Log.d(TAG, "cancelResponseListener.onResponse");
            mTopText.setText(response.toString());
            // Show the returned rider's location on the map
            try {
                final boolean matched = response.getBoolean("matched");
                if (matched == true) {
                    throw new Exception("Couldn't cancel request.");
                }
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
    private void sendDriverRequest(String driverOrRider, Double lat, Double lon) {
        // form the url
        final String coords = lat.toString() + "," + lon.toString();
        String url = SERVER_URL + driverOrRider + "?coords=" + coords
                + "&userid=" + mRiderIdEditText.getText().toString();
        Log.d(TAG, "Sent GET to " + url);

        // create the request
        JsonObjectRequest stringRequest = null;
        stringRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                driverResponseListener, responseErrorListener);

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
    public void onRequestNooberTap(View v) {
        // Show the toast
        Toast myToast = Toast.makeText(getApplicationContext(), "Requested Driver",
                Toast.LENGTH_LONG);
        myToast.show();

        // Hide request buttons and reveal finish button
        mRequestNooberButton.setEnabled(false);
        showCancelButton();

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
            sendDriverRequest("driver", currentLatitude, currentLongitude);
        }
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

    /**
     * Cancels pending outgoing request and sends a request to the server to remove the user from
     * the queue. This function should be called when the cancel button is tapped
     * @param v the view in which the button is pushed
     */
    public void onCancelTap(View v) {
        // first cancel pending request
        mHandler.removeCallbacks(mRepeatRequest);
        // then send request to server to remove user from queue
        String url = SERVER_URL + "cancel?userid=" + mRiderIdEditText.getText().toString();
        Log.d(TAG, "Sent GET to " + url);

        // create the request
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                    cancelResponseListener, responseErrorListener);
        // add the request to the RequestQueue to be sent automatically
        mRequestQueue.add(stringRequest);

        mProgressBar.setVisibility(View.GONE);
        mRequestNooberButton.setEnabled(true);
        hideCancelButton();
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
