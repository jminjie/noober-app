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
 * <p>
 * The Requester class is part of the application model. Use getInstance() to get the singleton
 * instance of the class and init(Context) before calling methods.
 * <p>
 * Requester sends requests to the server via Volley.RequestQueue
 */

class Requester {
    // singleton instance of Requester
    private static Requester s = null;
    private final String TAG = "Requester";
    private RequestQueue mRequestQueue;
    /**
     * Define the behavior upon receiving an error from the server
     */
    private Response.ErrorListener onErrorResponse = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            Log.e(TAG, "Error from the server");
            Log.e(TAG, error.toString());
        }
    };

    static Requester getInstance() {
        if (s == null) {
            s = new Requester();
        }
        return s;
    }

    // use the context to get a requestQueue
    void init(Context context) {
        mRequestQueue = Volley.newRequestQueue(context);
    }

    void addRequest(String url, Response.Listener<JSONObject> onResponse) {
        Log.d(TAG, "Sent GET to " + url);

        // create the request object
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                onResponse, onErrorResponse);

        // add the request to the RequestQueue to be sent automatically
        mRequestQueue.add(request);
    }

}
