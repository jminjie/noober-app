package com.jminjie.noober;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

/**
 * Created by jminjie on 2016-10-31.
 *
 * The Requester class is part of the application model. Use getInstance() to get the singleton
 * instance of the class and init(Context) before calling methods.
 *
 * Requester sends requests to the server via Volley.RequestQueue
 */

class Requester {
    private RequestQueue mRequestQueue;
    private final String TAG = "Requester";

    // singleton instance of Requester
    private static Requester s = null;

    static Requester getInstance() {
        if (s == null) {
            s = new Requester();
        }
        return s;
    }

    void init(Context context) {
        mRequestQueue = Volley.newRequestQueue(context);
    }

    /**
     * Define the behavior upon receiving an error from the server
     */
    private Response.ErrorListener onErrorResponse = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.d(TAG, "Error from the server");
        }
    };

    void addRequest(String url, Response.Listener<JSONObject> onResponse) {
        Log.d(TAG, "Sent GET to " + url);

        // create the request object
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                onResponse, onErrorResponse);

        // add the request to the RequestQueue to be sent automatically
        mRequestQueue.add(request);
    }

}
