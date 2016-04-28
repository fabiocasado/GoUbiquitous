package com.example.android.sunshine.wearable;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

/**
 * Created by fcasado on 4/28/16.
 */
public class DataLayerListenerService extends WearableListenerService {

	private static final String TAG = "DataLayerSample";
	private static final String WEATHER_PATH = "/weather";

	@Override
	public void onDataChanged(DataEventBuffer dataEvents) {
		GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
				.addApi(Wearable.API)
				.build();

		ConnectionResult connectionResult =
				googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

		if (!connectionResult.isSuccess()) {
			return;
		}

		// Loop through the events and send a message
		// to the node that created the data item.
		for (DataEvent event : dataEvents) {
			DataItem item = event.getDataItem();
			if (item.getUri().getPath().compareTo(WEATHER_PATH) == 0) {
				DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

				SharedPreferences sp = getBaseContext().getSharedPreferences("weather", Context.MODE_PRIVATE);
				SharedPreferences.Editor editor = sp.edit();
				editor.putInt("weatherID", dataMap.getInt("weatherID"));
				editor.putFloat("maxTemp", dataMap.getFloat("maxTemp"));
				editor.putFloat("minTemp", dataMap.getFloat("minTemp"));
				editor.commit();
			}
		}
	}
}
