package com.vipulsolanki.testgeotracing;

import android.app.Application;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.vipulsolanki.testgeotracing.model.LocationUpdateModel;

import io.realm.Realm;
import io.realm.RealmConfiguration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;


public class App extends Application
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    private static App sApplication;

    @Override
    public void onCreate() {
        super.onCreate();
        sApplication = this;
        buildGoogleApiClient();
        initRealmDatabase();
    }

    private void initRealmDatabase() {
        //Set realm configuration
        RealmConfiguration.Builder configBuilder = new RealmConfiguration.Builder(getApplicationContext());
        RealmConfiguration realmConfiguration = configBuilder.name("default").schemaVersion(1).build();
        Realm.setDefaultConfiguration(realmConfiguration);
    }

    public static App get() {
        return sApplication;
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    public boolean requestLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            mLocationRequest = new LocationRequest();
            mLocationRequest.setInterval(MILLISECONDS.convert(30, SECONDS));
            mLocationRequest.setFastestInterval(MILLISECONDS.convert(10, SECONDS));
            mLocationRequest.setSmallestDisplacement(50);
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            return true;
        }

        return false;
    }

    public boolean stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            return true;
        }
        return false;
    }

    //GoogleApiClient connection callbacks
    @Override
    public void onConnected(Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    //GoogleApiClient connection failed callback
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    //Location Listener implementation
    @Override
    public void onLocationChanged(Location location) {
        saveLocation(location, System.currentTimeMillis());
    }

    private void saveLocation(Location location, long timestamp) {
        Realm realm = Realm.getDefaultInstance();
        try {
            LocationUpdateModel model = new LocationUpdateModel(location, timestamp);
            realm.beginTransaction();
            realm.copyToRealm(model);
            realm.commitTransaction();
        } finally {
            realm.close();
        }
    }

}
