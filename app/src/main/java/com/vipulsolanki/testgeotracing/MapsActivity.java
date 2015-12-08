package com.vipulsolanki.testgeotracing;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.RoadsApi;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.SnappedPoint;
import com.google.maps.model.TravelMode;
import com.squareup.otto.Subscribe;
import com.vipulsolanki.testgeotracing.model.CameraChangeEvent;
import com.vipulsolanki.testgeotracing.model.LocationUpdateEvent;
import com.vipulsolanki.testgeotracing.model.LocationUpdateModel;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmResults;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final int REQUEST_PLACE_PICKER = 101;
    private GoogleMap mMap;

    Button btnStart;
    Button btnStop;
    Button btnDraw;
    Button btnList;

    private GoogleMap.OnMarkerClickListener markerClickListener = new GoogleMap.OnMarkerClickListener() {
        @Override
        public boolean onMarkerClick(Marker marker) {
            marker.showInfoWindow();
            return true;
        }
    };

    private GoogleMap.OnMapLongClickListener mapLongClickListener = new GoogleMap.OnMapLongClickListener() {
        @Override
        public void onMapLongClick(LatLng latLng) {
            Log.d(TAG, "Long click on map");
            doMapLongClick(latLng);
        }
    };
    private DirectionsRoute selectedRoute;
    private List<com.google.maps.model.LatLng> actualRoute = new ArrayList<>(64);
    private List<com.google.maps.model.LatLng> partialTracedRoute = new ArrayList<>(8);
    private com.google.maps.model.LatLng originPoint;
    private com.google.maps.model.LatLng latestPoint;

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

    @Override
    protected void onResume() {
        super.onResume();
        App.getBus().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.getBus().unregister(this);
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
        actualRoute.clear();
        originPoint = selectedRoute.overviewPolyline.decodePath().get(0);
        latestPoint = originPoint;
        trackingState = TrackState.NAVIGATION_STARTED;
    }

    private void onButtonStop() {
        if (!App.get().stopLocationUpdates()) {
            Toast.makeText(this, "Some error, but who cares?", Toast.LENGTH_SHORT).show();
        }
        trackingState = TrackState.NAVIGATION_ENDED;
    }

    private void onButtonDraw() {
        //oldRefreshRoute();
        if (selectedRoute != null) {
            //drawRoute(selectedRoute);
        }
    }

    private void oldRefreshRoute() {
        Realm realm = Realm.getDefaultInstance();
        Iterator<LocationUpdateModel> iter = realm.where(LocationUpdateModel.class).findAll().iterator();
        PolylineOptions polylineOptions = new PolylineOptions();
        mMap.clear();

        long polyLineWidth = Math.round(mMap.getCameraPosition().zoom * 1.5);
        while (iter.hasNext()) {
            LocationUpdateModel model = iter.next();
            Log.d(TAG, "DRAW : " + model.getLatitude() + " : " + model.getLongitude());
            polylineOptions.add(new LatLng(model.getLatitude(), model.getLongitude()))
                    .width(polyLineWidth)
                    .color(Color.GREEN);

            if (mMap.getCameraPosition().zoom >= 16.0) {
                DecimalFormat decimalFormat = new DecimalFormat("#.0");
                mMap.addMarker(new MarkerOptions()
                        .title(decimalFormat.format(model.getAccuracy()))
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_location))
                        .anchor(0.5f, 0.5f)
                        .flat(false)
                        .position(new LatLng(model.getLatitude(), model.getLongitude())));
            }
        }
        mMap.addPolyline(polylineOptions);
        realm.close();
    }

    private void onButtonList() {
        startActivity(UpdatesListActivity.getLaunchIntent(getApplicationContext()));
    }

    private void doMapLongClick(LatLng latLng) {
        //Do only if the start or destination not yet selected.
        if (trackingState == TrackState.NONE || trackingState == TrackState.START_SELECTED) {
            try {
                PlacePicker.IntentBuilder intentBuilder =
                        new PlacePicker.IntentBuilder();
                intentBuilder.setLatLngBounds(new LatLngBounds.Builder().include(latLng).build());
                Intent intent = intentBuilder.build(this);
                startActivityForResult(intent, REQUEST_PLACE_PICKER);

            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
        }
        
    }

    enum TrackState {
        NONE,
        START_SELECTED,
        END_SELECTED,
        ROUTE_DRAWN,
        NAVIGATION_STARTED,
        NAVIGATION_ENDED
    }

    private TrackState trackingState = TrackState.NONE;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PLACE_PICKER
                && resultCode == Activity.RESULT_OK) {

            // The user has selected a place. Extract the name and address.
            final Place place = PlacePicker.getPlace(data, this);
            String attributions = PlacePicker.getAttributions(data);
            if (attributions == null) {
                attributions = "";
            }

            if (trackingState == TrackState.NONE) {
                onSourceSelected(place, attributions);
            } else {
                onDestinationSelected(place, attributions);
            }

        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    Place sourcePlace;
    Place destinationPlace;

    private void onDestinationSelected(Place place, String attributions) {
        destinationPlace = place;
        trackingState = TrackState.END_SELECTED;
        getRoute(sourcePlace, destinationPlace);
    }

    private void getRoute(Place sourcePlace, Place destinationPlace) {
        GeoApiContext context = new GeoApiContext().setApiKey(getString(R.string.google_maps_server_key));

        DirectionsApiRequest request = DirectionsApi.getDirections(context,
                sourcePlace.getAddress().toString(),
                destinationPlace.getAddress().toString())
                .mode(TravelMode.DRIVING);

        try {
            DirectionsRoute[] routes = request.await();
            if (routes != null && routes.length > 0) {
                selectedRoute = routes[0];
                drawRoute(selectedRoute);
                trackingState = TrackState.ROUTE_DRAWN;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private void drawPath(List<com.google.maps.model.LatLng> latLngList,
                          int lineColor,
                          MarkerOptions startMarker,
                          MarkerOptions endMarker) {

        if (latLngList != null && latLngList.size() > 0) {
            PolylineOptions polylineOptions = new PolylineOptions();
            //mMap.clear();
            for (com.google.maps.model.LatLng latLng : latLngList) {
                polylineOptions.add(new LatLng(latLng.lat, latLng.lng));
            }
            polylineOptions.width(Math.round(mMap.getCameraPosition().zoom * 1.25));
            polylineOptions.color(lineColor);
            mMap.addPolyline(polylineOptions);

            if (startMarker != null) {
                LatLng firstPoint = new LatLng(latLngList.get(0).lat, latLngList.get(0).lng);
                mMap.addMarker(startMarker.position(firstPoint));
            }
            if (endMarker != null) {
                LatLng endPoint = new LatLng(latLngList.get(latLngList.size()-1).lat, latLngList.get(latLngList.size()-1).lng);
                mMap.addMarker(endMarker.position(endPoint));
            }
        }

    }

    private void drawRoute(DirectionsRoute route) {
        List<com.google.maps.model.LatLng> latLngList = route.overviewPolyline.decodePath();

        MarkerOptions startMarker = new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_location))
                .visible(true);

        MarkerOptions endMarker = new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_finish_flag))
                .anchor(0, 1)
                .visible(true);

        mMap.clear();
        drawPath(latLngList, Color.GREEN, startMarker, endMarker);
    }

    private void onSourceSelected(Place place, String attributions) {
        sourcePlace = place;
        trackingState = TrackState.START_SELECTED;
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

        UiSettings uiSettings = mMap.getUiSettings();
        uiSettings.setMyLocationButtonEnabled(true);
        uiSettings.setMapToolbarEnabled(true);

        mMap.setOnCameraChangeListener(cameraChangeListener);
        mMap.setOnMarkerClickListener(markerClickListener);
        mMap.setOnMapLongClickListener(mapLongClickListener);

        LatLng ksn = new LatLng(18.5444665 , 73.7917223);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ksn, 15f));

    }

    private GoogleMap.OnCameraChangeListener cameraChangeListener = new GoogleMap.OnCameraChangeListener() {
        @Override
        public void onCameraChange(CameraPosition cameraPosition) {
            App.getBus().post(new CameraChangeEvent());
        }
    };

    @SuppressWarnings("unused")
    @Subscribe
    public void locationAvailable(LocationUpdateEvent event) {
        //oldRefreshRoute();
//        if (selectedRoute != null) {
//            drawRoute(selectedRoute);
//        }

        com.google.maps.model.LatLng currentPoint =
                new com.google.maps.model.LatLng(event.location.getLatitude(),
                        event.location.getLongitude());

        partialTracedRoute.add(currentPoint);

        if (partialTracedRoute.size() >= 4) {
            GeoApiContext context = new GeoApiContext().setApiKey(getString(R.string.google_maps_server_key));
//            RoadsApi.snapToRoads(context, true, partialTracedRoute.toArray(new com.google.maps.model.LatLng[partialTracedRoute.size()]))
//                    .setCallback(new PendingResult.Callback<SnappedPoint[]>() {
//                @Override
//                public void onResult(SnappedPoint[] result) {
//                    for (SnappedPoint snappedPoint : result) {
//                        actualRoute.add(snappedPoint.location);
//                    }
//                    if (actualRoute != null && actualRoute.size() > 0) {
//                        latestPoint = actualRoute.get(actualRoute.size() - 1);
//                    }
//                    partialTracedRoute.clear();
//                    drawPath(actualRoute,
//                            Color.BLUE,
//                            null,
//                            new MarkerOptions()
//                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_location))
//                                    .flat(true)
//                                    .anchor(0.5f, 0.5f)
//                                    .visible(true)
//                    );
//                }
//
//                @Override
//                public void onFailure(Throwable e) {
//
//                }
//            });
            try {
                SnappedPoint[] result = RoadsApi.snapToRoads(context, true, partialTracedRoute.toArray(new com.google.maps.model.LatLng[partialTracedRoute.size()]))
                        .await();

                for (SnappedPoint snappedPoint : result) {
                    actualRoute.add(snappedPoint.location);
                }
                if (actualRoute != null && actualRoute.size() > 0) {
                    latestPoint = actualRoute.get(actualRoute.size() - 1);
                }
                partialTracedRoute.clear();
                drawPath(actualRoute,
                        Color.CYAN,
                        null,
                        new MarkerOptions()
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_location))
                                .flat(true)
                                .anchor(0.5f, 0.5f)
                                .visible(true)
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    @SuppressWarnings("unused")
    @Subscribe
    public void cameraChanged(CameraChangeEvent event) {
        //oldRefreshRoute();
//        if (selectedRoute != null) {
//            drawRoute(selectedRoute);
//        }
    }
}
