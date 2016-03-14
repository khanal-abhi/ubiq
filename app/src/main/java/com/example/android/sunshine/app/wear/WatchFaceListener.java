package com.example.android.sunshine.app.wear;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.R;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * Created by abhi on 3/14/16.
 */
public class WatchFaceListener extends WearableListenerService
        implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks{

    public static final String WEAR_MSG_PATH = "/wear/data/sunshine/020508";
    public static final String READY_MESSAGE = "ready";

    private static final String[] PROJECTION_COLUMN = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP

    };

    private static final int WEATHER_ID = 0;
    private static final int MAX_TEMP = 1;
    private static final int MIN_TEMP = 2;

    private GoogleApiClient googleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(googleApiClient != null){
            googleApiClient.disconnect();
            googleApiClient = null;
        }
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private byte[] serialize(Object object) throws IOException{
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
        objectOutputStream.writeObject(object);
        return outputStream.toByteArray();

    }

    private byte[] getBitmapArray(Bitmap bitmap){
        ByteArrayOutputStream byteArrayOutputStream = null;

        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } finally {
            try {
                byteArrayOutputStream.close();
                byteArrayOutputStream = null;
            } catch (IOException e){
                Log.d(getClass().getSimpleName(), e.getMessage());
            }
        }


    }

    private void dispatchForecastToWearable(final byte[] message){
        if(googleApiClient.isConnected()){
            NodeApi.GetConnectedNodesResult nodesResult = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();

            for(Node node: nodesResult.getNodes()){
                Wearable.MessageApi.sendMessage(
                        googleApiClient,
                        node.getId(),
                        WEAR_MSG_PATH,
                        message
                ).await();
            }
        }
    }

    private byte[] [] getForecastData(){
        String location = Utility.getPreferredLocation(this);
        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location,
                System.currentTimeMillis()
        );

        Cursor cursor = getContentResolver().query(
                weatherUri,
                PROJECTION_COLUMN,
                null,
                null,
                WeatherContract.WeatherEntry.COLUMN_DATE + " ASC"
        );

        if(cursor == null)
            return null;
        if(!cursor.moveToFirst()){
            cursor.close();
            return null;
        }

        int weatherId = cursor.getInt(WEATHER_ID);
        int rId = Utility.getArtResourceForWeatherCondition(weatherId);
        double max = cursor.getDouble(MAX_TEMP);
        double min = cursor.getDouble(MIN_TEMP);
        String displayMax = Utility.formatTemperature(this, max);
        String displayMin = Utility.formatTemperature(this, min);
        cursor.close();

        Bitmap weatherArt = BitmapFactory.decodeResource(getResources(), rId);

        weatherArt = Bitmap.createScaledBitmap(
                weatherArt,
                (int) getResources().getDimension(R.dimen.wearable_dimen),
                (int) getResources().getDimension(R.dimen.wearable_dimen),
                false
        );

        byte[] maxBytes = displayMax.getBytes();
        byte[] minBytes = displayMin.getBytes();
        byte[] weatherArtBytes = getBitmapArray(weatherArt);

        return new byte[][]{weatherArtBytes, maxBytes, minBytes};
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if(messageEvent.getPath().equals(WEAR_MSG_PATH)){
            if(messageEvent.getData().toString().equals(READY_MESSAGE)){
                new Thread(){
                    @Override
                    public void run() {
                        try {
                            byte[][] forecast = getForecastData();
                            byte[] serializedForecast = serialize(forecast);
                            dispatchForecastToWearable(serializedForecast);
                        } catch (IOException e){
                            Log.d(getClass().getSimpleName(), e.getMessage());
                        }
                    }
                }.start();
            }
        }
    }
}
