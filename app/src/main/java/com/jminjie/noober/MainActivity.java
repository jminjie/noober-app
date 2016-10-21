package com.jminjie.noober;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

public class MainActivity extends AppCompatActivity {
    private Button mRequestDriverButton, mRequestRiderButton, mFinishButton;
    private TextView mTopText;
    private MapView mMapView;

    private RequestQueue mRequestQueue;
    private IMapController mMapController;
    final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    final String TAG = "MainActivity";

    private Animation mSlideLeftAnimation, mSlideRightAnimation = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get the buttons
        mRequestDriverButton = (Button) findViewById(R.id.request_driver_button);
        mRequestRiderButton = (Button) findViewById(R.id.request_rider_button);
        mFinishButton = (Button) findViewById(R.id.finish_button);
        mTopText = (TextView) findViewById(R.id.top_text);

        // init the request queue
        mRequestQueue = Volley.newRequestQueue(this);

        // load animations
        mSlideLeftAnimation = AnimationUtils.loadAnimation(getApplicationContext(),
                R.anim.slide_left_animation);
        mSlideRightAnimation = AnimationUtils.loadAnimation(getApplicationContext(),
                R.anim.slide_right_animation);

        // set up osm
        // TODO check for permission WRITE_EXTERNAL_STORAGE at runtime
        org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants.setUserAgentValue(BuildConfig.APPLICATION_ID);
        mMapView = (MapView) findViewById(R.id.map);
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapView.setBuiltInZoomControls(true);
        mMapView.setMultiTouchControls(true);
        mMapController = mMapView.getController();
        mMapController.setZoom(9);
        GeoPoint startPoint = new GeoPoint(30.0, -100.0);
        mMapController.setCenter(startPoint);

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
     * Define the behavior upon receiving a successfull response from the server as a String
     */
    private Response.Listener<String> responseListener = new Response.Listener<String>() {
        @Override
        public void onResponse(String response) {
            // Display the first 20 characters of the response string.
            mTopText.setText("Response is: " + response.substring(0, 20));
        }
    };

    /**
     * Define the behavior upon receiving an error from the server
     */
    private Response.ErrorListener responseErrorListener = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            mTopText.setText("That didn't work!");
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
        // form the url as "server/<driver_or_rider>?coords=<lat,long>
        String coords = lat.toString() + "," + lon.toString();
        String url = "http://jminjie.com:5000/noober/" + driverOrRider + "?coords=" + coords;
        Log.d(TAG, "Sent GET to " + url);

        // create the request
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                responseListener, responseErrorListener);

        // add the request to the RequestQueue to be sent automatically
        mRequestQueue.add(stringRequest);
    }

    /**
     * Get the best location I can
     * TODO replace this with google_location_services
     * @return
     */
    private Location getBestLocation() {
        LocationManager locationManager =
                (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        // TODO respond to location updates
        // TODO e.g.) https://developer.android.com/guide/topics/location/strategies.html
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // TODO: Enable runtime permission request and callback method on request granted
            // ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
            //         PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return null;
        }
        return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    }


    /**
     * This function should be called if a rider is requesting a driver by tapping the button
     * Sends the user's location to server/driver
     * @param v
     */
    public void onRiderRequestDriverTap(View v) {
        // Show the toast
        Toast myToast = Toast.makeText(getApplicationContext(), "Requested Driver",
                Toast.LENGTH_LONG);
        myToast.show();

        // Hide request buttons and reveal finish button
        disableRequestButtons();
        showFinishButton();

        // Send request for a driver
        Location userLocation = getBestLocation();
        if (userLocation != null) {
            double currentLatitude = userLocation.getLatitude();
            double currentLongitude = userLocation.getLongitude();
            GeoPoint currentGeoPoint = new GeoPoint(currentLatitude, currentLongitude);
            mMapController.setCenter(currentGeoPoint);
            mMapController.setZoom(20);
            sendDriverOrRiderRequest("driver", currentLatitude, currentLongitude);
        }
    }

    /**
     * TODO could probably merge this function with onRiderRequestDriverTap(...)
     * This function should be called if a driver is requesting a rider by tapping the button
     * Sends the user's location to server/rider
     * @param v
     */
    public void onDriverRequestRiderTap(View v) {
        // Show the toast
        Toast myToast = Toast.makeText(getApplicationContext(), "Requested Rider",
                Toast.LENGTH_LONG);
        myToast.show();

        // Hide request buttons and reveal finish button
        disableRequestButtons();
        showFinishButton();

        // Send request for a driver
        Location userLocation = getBestLocation();
        if (userLocation != null) {
            double currentLatitude = userLocation.getLatitude();
            double currentLongitude = userLocation.getLongitude();
            GeoPoint currentGeoPoint = new GeoPoint(currentLatitude, currentLongitude);
            mMapController.setCenter(currentGeoPoint);
            mMapController.setZoom(20);
            sendDriverOrRiderRequest("rider", currentLatitude, currentLongitude);
        }
    }

    /**
     * Slide the finish button into screen.
     * This animation is defined in app/res/anim/slide_left_animation.xml
     */
    private void showFinishButton() {
        mFinishButton.startAnimation(mSlideLeftAnimation);
        mFinishButton.setVisibility(View.VISIBLE);
    }

    /**
     * Slide the finish button out of screen.
     * This animation is defined in app/res/anim/slide_right_animation.xml
     */
    private void hideFinishButton() {
        mFinishButton.startAnimation(mSlideRightAnimation);
        mFinishButton.setVisibility(View.INVISIBLE);
    }

    /**
     * Enables a driver or rider finished with a trip to make more requests. This function should be
     * called when the finish button is tapped
     * @param v
     */
    public void onFinishTap(View v) {
        enableRequestButtons();
        hideFinishButton();
    }
}
