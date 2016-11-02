package com.jminjie.noober;

import android.os.Handler;
import android.util.Log;
import android.widget.EditText;

import com.android.volley.Response;

import org.json.JSONObject;

/**
 * Created by jminjie on 2016-10-31.
 * <p>
 * The Poller is part of the application model. Use the constructor to set the userId source
 * <p>
 * Poller continuously polls the server based on the current state and calls the ViewStateController
 * to update the view based on the state.
 */

class Poller {
    //(TODO: eventually userId will be stored on login so that this does not interact with the view)
    private EditText mUserIdEditText;

    private Handler mHandler;
    private Runnable mPollingRequest;
    private ViewStateChanger mViewStateChanger;

    private final String TAG = "Poller";
    private Integer mDebugCount = 0;

    // polling interval in milliseconds
    private final int POLLING_INTERVAL = 2000;

    Poller(EditText userIdEditText) {
        mUserIdEditText = userIdEditText;
        mHandler = new Handler();
        mViewStateChanger = ViewStateChanger.getInstance();
    }

    enum RiderState {
        IDLE,
        WAITING_FOR_MATCH,
        WAITING_FOR_PICKUP,
        WAITING_FOR_DROPOFF
    }

    // the current state of the rider
    private RiderState riderState = RiderState.IDLE;

    void setRiderState(RiderState state) {
        riderState = state;
    }

    Response.Listener<JSONObject> getRiderRequestingDriverResponseListener() {
        return kRiderRequestingDriverResponseListener;
    }

    /**
     * Upon receiving a successful response to a request for a Noober:
     * If the response says we have a match, show the match on the map together with the user
     * and remove the progress bar
     * If the response says we have no match, remove the match from the map and display the
     * progress bar
     * In 2 seconds send another request with a location update
     */
    private Response.Listener<JSONObject> kRiderRequestingDriverResponseListener =
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, "kRiderRequestingDriverResponseListener.onResponse");
                    mViewStateChanger.setTopText(response.toString());
                    // Show the returned driver's location on the map
                    try {
                        final boolean matched = response.getBoolean("matched");
                        if (matched) {
                            mViewStateChanger.setWaitingForPickup(response.getDouble("lat"),
                                    response.getDouble("lon"));
                            setRiderState(Poller.RiderState.WAITING_FOR_PICKUP);
                            doPoll();
                        } else {
                            mViewStateChanger.setWaitingForMatch();
                            setRiderState(Poller.RiderState.WAITING_FOR_MATCH);
                            doPoll();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                        mViewStateChanger.setTopText(e.getMessage());
                    }
                }
            };

    Response.Listener<JSONObject> getRiderCancelResponseListener() {
        return kRiderCancelResponseListener;
    }

    /**
     * Upon receiving a successful response to cancelling, notify the user
     */
    private Response.Listener<JSONObject> kRiderCancelResponseListener =
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    // update the view
                    mViewStateChanger.setIdle();

                    // Show the toast
                    mViewStateChanger.showToast("Request cancelled");
                }
            };

    /**
     * Upon receiving a successful response to a request for a Noober:
     * If the response says we have a match, show the match on the map together with the user
     * and remove the progress bar
     * If the response says we have no match then do nothing
     */
    private Response.Listener<JSONObject> kRiderWaitingForMatchResponseListener =
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, "kRiderWaitingForMatchResponseListener.onResponse");
                    try {
                        final boolean matched = response.getBoolean("matched");
                        if (matched) {
                            // change to waiting-for-pickup and continue polling
                            mViewStateChanger.setWaitingForPickup(
                                    response.getDouble("lat"), response.getDouble("lon"));
                            mViewStateChanger.setTopText(response.toString());
                            setRiderState(Poller.RiderState.WAITING_FOR_PICKUP);
                            doPoll();
                        } else {
                            // don't change state, just continue polling
                            doPoll();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                        mViewStateChanger.setTopText(e.getMessage());
                    }
                }
            };

    // TODO: implement stubs
    private Response.Listener<JSONObject> kRiderWaitingForPickupResponseListener = null;
    private Response.Listener<JSONObject> kRiderWaitingForDropoffResponseListener = null;

    /**
     * Start polling the server based on current state
     */
    private void doPoll() {
        Log.d(TAG, "doPoll");
        // wait 2 seconds and then send a request
        mViewStateChanger.setTopText("Sending request #" + mDebugCount.toString());
        mDebugCount += 1;
        if (riderState == RiderState.WAITING_FOR_MATCH) {
            mPollingRequest = new Runnable() {
                public void run() {
                    String url = MainActivity.SERVER_URL + "rider_app?user_id="
                            + mUserIdEditText.getText() + "&type=101";
                    Requester.getInstance().addRequest(url, kRiderWaitingForMatchResponseListener);
                }
            };
        } else if (riderState == RiderState.WAITING_FOR_PICKUP) {
            mPollingRequest = new Runnable() {
                public void run() {
                    String url = MainActivity.SERVER_URL + "rider_app?user_id="
                            + mUserIdEditText.getText() + "&type=102";
                    Requester.getInstance().addRequest(url, kRiderWaitingForPickupResponseListener);
                }
            };
        } else if (riderState == RiderState.WAITING_FOR_DROPOFF) {
            String url = MainActivity.SERVER_URL + "rider_app?user_id="
                    + mUserIdEditText.getText() + "&type=103";
            Requester.getInstance().addRequest(url, kRiderWaitingForDropoffResponseListener);
        } else {
            stopPolling();
        }
        mHandler.postDelayed(mPollingRequest, POLLING_INTERVAL);
    }

    /**
     * Stop polling the server
     */
    private void stopPolling() {
        mHandler.removeCallbacks(mPollingRequest);
    }

}
