package com.jminjie.noober;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class MainActivity extends AppCompatActivity {
    private EditText mUserIdEditText;

    private Requester mRequester;
    private Poller mPoller;
    private ViewStateChanger mViewStateChanger;

    final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    final int PERMISSIONS_REQUEST_MULTIPLE = 2;

    public static final String SERVER_URL = "http://jminjie.com:5000/noober/rider_app";
    final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get the view components
        mUserIdEditText = (EditText) findViewById(R.id.userIdEditText);

        // initialize the requester
        Log.d(TAG, "onCreate initialize mRequester");
        mRequester = Requester.getInstance();
        mRequester.init(getApplicationContext());

        // poller to periodically poll server for updates
        Log.d(TAG, "onCreate initialize mPoller");
        mPoller = new Poller(mUserIdEditText);

        // init viewStateChanger
        Log.d(TAG, "onCreate initialize mViewStateChanger");
        final TextView mTopText = (TextView) findViewById(R.id.topText);
        final Button requestNooberButton = (Button) findViewById(R.id.requestDriverButton);
        final Button cancelButton = (Button) findViewById(R.id.cancelButton);
        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
        final MapView mapView = (MapView) findViewById(R.id.map);
        final IMapController mapController = mapView.getController();
        final ImageView pickupChooser = (ImageView) findViewById(R.id.pickupMarker);
        mViewStateChanger = ViewStateChanger.getInstance();
        mViewStateChanger.init(requestNooberButton, cancelButton, mapView, pickupChooser,
                progressBar, mapController, mTopText, getApplicationContext());

        // verify permissions
        Log.d(TAG, "onCreate getPermissions");
        promptUserForPermissions();

        Log.d(TAG, "onCreate finished");
    }

    /**
     * Get the best location available
     *
     * @return a Location object with the best available location data
     */
    public static Location getBestLocation(Context context, MyLocationNewOverlay locationOverlay) {
        Location bestLocation = locationOverlay.getLastFix();
        if (bestLocation == null) {
            LocationManager locationManager =
                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e("MainActivity", "(static) getBestLocation");
                bestLocation.setLatitude(40.0);
                bestLocation.setLongitude(120.);
            }
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        return bestLocation;
    }

    /**
     * This function should be called if a rider is requesting a driver by tapping the button
     * Sends the pickup location to server
     *
     * @param v the view in which the button is pushed
     */
    public void onRequestNooberTap(View v) {
        Log.d(TAG, "onRequestNooberTap");
        // Show the toast
        mViewStateChanger.showToast("Request sent");

        mViewStateChanger.getMapView().getOverlays().clear();
        // Send kRiderRequestingDriver (type 100)
        IGeoPoint mapCenter = mViewStateChanger.getMapView().getMapCenter();
        Double lat = mapCenter.getLatitude();
        Double lon = mapCenter.getLongitude();
        String url = SERVER_URL + "?type=100&lat=" + lat.toString() + "&lon=" + lon.toString()
                + "&user_id=" + mUserIdEditText.getText().toString();
        mRequester.addRequest(url, mPoller.getRiderRequestingDriverResponseListener());
    }

    /**
     * Cancels pending outgoing request and sends a request to the server to remove the user from
     * the queue. This function should be called when the cancel button is tapped
     *
     * @param v the view in which the button is pushed
     */
    public void onCancelTap(View v) {
        // stop polling
        mPoller.setRiderState(Poller.RiderState.IDLE);
        mPoller.stopPolling();

        // send request to server to remove user from queue
        String url = SERVER_URL + "?type=104&user_id=" + mUserIdEditText.getText().toString();
        mRequester.addRequest(url, mPoller.getRiderCancelResponseListener());
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
            ActivityCompat.requestPermissions(this, new String[]{
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
     *
     * @param requestCode  the code passed in to requestPermission
     * @param permissions  which permissions were requested
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
                if (grantResults.length != 2 || grantResults[0] != PackageManager.PERMISSION_GRANTED
                        || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    finish();
                }
            }
        }
    }
}
