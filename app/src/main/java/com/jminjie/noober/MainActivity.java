package com.jminjie.noober;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

public class MainActivity extends AppCompatActivity {
    Button requestDriverButton, requestRiderButton, finishButton;
    TextView topText;
    RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get the buttons
        requestDriverButton = (Button) findViewById(R.id.request_driver_button);
        requestRiderButton = (Button) findViewById(R.id.request_rider_button);
        finishButton = (Button) findViewById(R.id.finish_button);
        topText = (TextView) findViewById(R.id.top_text);

        // init the request queue
        requestQueue = Volley.newRequestQueue(this);
    }

    private void disableRequestButtons() {
        requestDriverButton.setEnabled(false);
        requestRiderButton.setEnabled(false);
    }

    private void enableRequestButtons() {
        requestDriverButton.setEnabled(true);
        requestRiderButton.setEnabled(true);
    }

    private Response.Listener<String> responseListener = new Response.Listener<String> () {
        @Override
        public void onResponse(String response) {
            // Display the first 20 characters of the response string.
            topText.setText("Response is: "+ response.substring(0,20));
        }
    };

    private Response.ErrorListener responseErrorListener = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            topText.setText("That didn't work!");
        }
    };

    private boolean sendDriverOrRiderRequest(String driverOrRider, Float lat, Float lon) {
        String coords = lat.toString() + "," + lon.toString();
        String url ="http://jminjie.com:5000/noober/" + driverOrRider + "?coords=" + coords;

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                responseListener, responseErrorListener);
        // Add the request to the RequestQueue.
        requestQueue.add(stringRequest);
        return true;
    }

    public void onRiderRequestDriverTap(View v) {
        // Show the toast
        Toast myToast = Toast.makeText(getApplicationContext(), "Requested Driver", Toast.LENGTH_LONG);
        myToast.show();

        // Hide request buttons and reveal finish button
        disableRequestButtons();
        finishButton.setVisibility(View.VISIBLE);

        // Send request for a driver
        sendDriverOrRiderRequest("driver", 5f, 10f);
    }

    public void onDriverRequestRiderTap(View v) {
        // Show the toast
        Toast myToast = Toast.makeText(getApplicationContext(), "Requested Rider", Toast.LENGTH_LONG);
        myToast.show();

        // Hide request buttons and reveal finish button
        disableRequestButtons();
        finishButton.setVisibility(View.VISIBLE);

        // Send request for a rider
        sendDriverOrRiderRequest("rider", 5f, 10f);
    }

    public void onFinishTap(View v) {
        enableRequestButtons();
        finishButton.setVisibility(View.INVISIBLE);
    }
}
