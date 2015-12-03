package com.vipulsolanki.testgeotracing;

import android.graphics.Color;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.vipulsolanki.testgeotracing.model.LocationUpdateModel;

import java.util.Iterator;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = MapsActivity.class.getSimpleName();
    private GoogleMap mMap;

    Button btnStart;
    Button btnStop;
    Button btnDraw;
    Button btnList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        btnStart = (Button) findViewById(R.id.btn_start);
        btnStop = (Button) findViewById(R.id.btn_stop);
        btnDraw = (Button) findViewById(R.id.btn_draw);
        btnList = (Button) findViewById(R.id.btn_list);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonStart();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonStop();
            }
        });
        btnDraw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonDraw();
            }
        });
        btnList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonList();
            }
        });
    }

    private void onButtonStart() {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        RealmResults<LocationUpdateModel> result = realm.where(LocationUpdateModel.class).findAll();
        result.clear();
        realm.commitTransaction();
        realm.close();

        if (!App.get().requestLocationUpdates()) {
            Toast.makeText(this, "Request failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void onButtonStop() {
        if (!App.get().requestLocationUpdates()) {
            Toast.makeText(this, "Some error, but who cares?", Toast.LENGTH_SHORT).show();
        }
    }

    private void onButtonDraw() {
        Realm realm = Realm.getDefaultInstance();
        Iterator<LocationUpdateModel> iter = realm.where(LocationUpdateModel.class).findAll().iterator();
        PolylineOptions polylineOptions = new PolylineOptions();
        while (iter.hasNext()) {
            LocationUpdateModel model = iter.next();
            Log.d(TAG, "DRAW : " + model.getLatitude() + " : " + model.getLongitude());
            polylineOptions.add(new LatLng(model.getLatitude(), model.getLongitude()))
                            .width(25)
                            .color(Color.GREEN);
            mMap.addMarker(new MarkerOptions().title(String.valueOf(model.getAccuracy())).position(new LatLng(model.getLatitude(), model.getLongitude())));
        }
        mMap.addPolyline(polylineOptions);
//        LocationUpdateModel firstModel = realm.where(LocationUpdateModel.class).findFirst();
//        if (firstModel != null) {
//            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
//                    new LatLng(firstModel.getLatitude(), firstModel.getLongitude()),
//                    15f));
//        }
        realm.close();
    }

    private void onButtonList() {
        startActivity(UpdatesListActivity.getLaunchIntent(getApplicationContext()));
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        LatLng ksn = new LatLng(18.5444665 , 73.7917223);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ksn, 15f));
    }
}
