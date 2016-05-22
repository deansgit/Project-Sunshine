package com.runningoutofbreadth.wearable;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class WeatherDataListenerService extends WearableListenerService{

//    private final static String LOG_TAG = "WEARABLE SERVICE";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
//        Log.d(LOG_TAG, "onDataChanged called");
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataMap dataMap = DataMapItem.fromDataItem(
                        dataEvent.getDataItem()).getDataMap();
                String path = dataEvent.getDataItem().getUri().getPath();
                if (path.equals(getString(R.string.weather_data_path))) {
                    //Broadcast data to receivers
                    Intent dataItemIntent = new Intent();
                    dataItemIntent.setAction(Intent.ACTION_SEND);
                    dataItemIntent.putExtras(dataMap.toBundle());
                    LocalBroadcastManager.getInstance(this).sendBroadcast(dataItemIntent);
                }
            }
        }
    }
}
