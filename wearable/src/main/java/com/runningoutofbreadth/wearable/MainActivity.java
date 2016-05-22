package com.runningoutofbreadth.wearable;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    private static final String LOG_TAG = "WEARABLE ACTIVITY";
    private TextView mDateTextView;
    private ImageView mWeatherImageView;
    private TextView mHighTextView;
    private TextView mLowTextView;
    private GoogleApiClient mGoogleApiClient;

    private static final String WEAR_DATE_STRING_KEY = "date";
    private static final String WEAR_HIGH_TEMP_KEY = "high";
    private static final String WEAR_LOW_TEMP_KEY = "low";
    private static final String WEAR_WEATHER_IMAGE_KEY = "weather_image";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mDateTextView = (TextView) findViewById(R.id.date_text_view);
                mWeatherImageView = (ImageView) findViewById(R.id.weather_image_view);
                mHighTextView = (TextView) findViewById(R.id.high_text_view);
                mLowTextView = (TextView) findViewById(R.id.low_text_view);
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        IntentFilter dataItemFilter = new IntentFilter(Intent.ACTION_SEND);
        DataItemBroadcastReceiver dataItemBroadcastReceiver = new DataItemBroadcastReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(dataItemBroadcastReceiver, dataItemFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        if ((mGoogleApiClient != null) && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    public class DataItemBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            String high = bundle.getString(WEAR_HIGH_TEMP_KEY);
            String low = bundle.getString(WEAR_LOW_TEMP_KEY);
            int weatherId = bundle.getInt(WEAR_WEATHER_IMAGE_KEY);
            int weatherResourceId = WearUtility.getIconResourceForWeatherCondition(weatherId);
            Resources resources = getResources();

            String mDate = bundle.getString(WEAR_DATE_STRING_KEY);

            Log.d(LOG_TAG, "mDate (long) is: " + String.valueOf(mDate)
                    + " and dateString is: " + mDate);
            mDateTextView.setText(mDate);
            //TODO fix this once we load in the images
            mWeatherImageView.setImageDrawable(resources.getDrawable(weatherResourceId));
            mHighTextView.setText(String.valueOf(high));
            mLowTextView.setText(String.valueOf(low));
        }
    }

}
