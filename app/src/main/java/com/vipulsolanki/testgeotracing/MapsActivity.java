package com.vipulsolanki.testgeotracing;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.gms.maps.model.Polyline;
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

import java.util.ArrayList;
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
    private Polyline partialPolyline; //YELLO polyline
    private Polyline routePolyline; //CYAN, travelled(actual)
    private MarkerOptions currentMarkerOptions;
    private Marker currentMarker;
    private Deviation[] deviations;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        currentMarkerOptions = new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_location))
                .flat(true)
                .anchor(0.5f, 0.5f)
                .visible(true);

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

        //Get any remaining snapped points
        //This will also update deviations if any.
        getSnappedPoints();

    }

    private void onButtonDraw() {

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
                "place_id:" + sourcePlace.getId(),
                "place_id:" + destinationPlace.getId())
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

    class Deviation {
        Deviation(float meters, boolean bDeviated) {
            this.meters = meters;
            this.bDeviated = bDeviated;
        }

        @Override
        public String toString() {
            return "meters : " + meters + " ; bDeviated:" + bDeviated;
        }

        float meters;
        boolean bDeviated;
    }

    private void calcDeviations(List<com.google.maps.model.LatLng> route, List<com.google.maps.model.LatLng> actualRoute) {
        float[] resultParam = new float[2];
        deviations = new Deviation[actualRoute.size()];
        //Initializing
        for (int i = 0; i < deviations.length; i++) {
            deviations[i] = new Deviation(0, false);
        }

        int counter = 0;
        for (com.google.maps.model.LatLng latLng : actualRoute) {
            float result = Float.MAX_VALUE;
            for (com.google.maps.model.LatLng latlngRoute : route) {
                Location.distanceBetween(latLng.lat, latLng.lng, latlngRoute.lat, latlngRoute.lng, resultParam);
                result = Math.min(result, resultParam[0]); //resultParam[0] is distanceBetween.
            }

            Deviation deviation = deviations[counter++];
            deviation.meters = result;
            deviation.bDeviated = result > 99 ? true : false;
        }
    }

    private void analyzeDeviations() {
        final int MAX_DEVIATION_TOLERANCE = 3;
        int deviationStartIndex = -1;
        int counter = 0;

        for (int i = 0; i < deviations.length; i++) {
            Deviation deviation = deviations[i];
            if (deviation.bDeviated) { //TRUE
                if (deviationStartIndex == -1) {
                    deviationStartIndex = i;
                    counter = 1;
                } else {
                    counter++;
                }
            } else { //FALSE
                if (counter > MAX_DEVIATION_TOLERANCE) {
                    for (int j = deviationStartIndex; j < i; j++) {
                        deviation.bDeviated = true;
                    }
                } else {
                    for (int j = deviationStartIndex; j < i; j++) {
                        deviation.bDeviated = false;
                    }
                }
                counter = 0;
            }
        }
    }

    class DeviationPaths { //All Deviations polylines(RED).
        private PolylineOptions options = null;
        public ArrayList<PolylineOptions> polylineOptions= new ArrayList<>(); //List of RED arcs.

        DeviationPaths() {
        }

        public void stop() {
            if (options != null) {
                polylineOptions.add(options);
                options = null;
            }
        }

        public PolylineOptions add(LatLng latLng) {
            if (options == null) options = new PolylineOptions();
            return options.add(latLng);
        }
    }

    private void drawDeviation() {
        if (deviations == null || deviations.length == 0) {
            return;
        }

        DeviationPaths deviationPaths = new DeviationPaths();

        for (int i = 0; i < deviations.length; i++) {
            Deviation deviation = deviations[i];
            if (deviation.bDeviated == true) {
                deviationPaths.add(latLngFromModel(actualRoute.get(i)));

            } else {
                deviationPaths.stop();
            }

        }

        deviationPaths.stop();

        //Draws all RED lines.
        for (int i = 0; i < deviationPaths.polylineOptions.size(); i++) {
            PolylineOptions options = deviationPaths.polylineOptions.get(i);
            options.width(25)
                    .zIndex(2)
                    .color(Color.RED);
            mMap.addPolyline(options);
        }


    }


    private Polyline drawPath(List<com.google.maps.model.LatLng> latLngList, int lineColor) {
        if (latLngList != null && latLngList.size() > 0) {
            PolylineOptions polylineOptions = new PolylineOptions();
            //mMap.clear();
            for (com.google.maps.model.LatLng latLng : latLngList) {
                polylineOptions.add(new LatLng(latLng.lat, latLng.lng));
            }
            polylineOptions.width(Math.round(mMap.getCameraPosition().zoom * 1.25));
            polylineOptions.color(lineColor);
            return mMap.addPolyline(polylineOptions);
        }

        return null;
    }

    private LatLng latLngFromModel(com.google.maps.model.LatLng modelLatLng) {
        return new LatLng(modelLatLng.lat, modelLatLng.lng);
    }

    private void drawRoute(DirectionsRoute route) {
        List<com.google.maps.model.LatLng> latLngList = route.overviewPolyline.decodePath();

        MarkerOptions startMarker = new MarkerOptions()
                .position(latLngFromModel(latLngList.get(0)))
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_location))
                .visible(true);

        MarkerOptions endMarker = new MarkerOptions()
                .position(latLngFromModel(latLngList.get(latLngList.size()-1)))
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_finish_flag))
                .anchor(0, 1)
                .visible(true);

        mMap.clear();
        drawPath(latLngList, Color.GREEN);
        mMap.addMarker(startMarker);
        mMap.addMarker(endMarker);

    }

    private void onSourceSelected(Place place, String attributions) {
        sourcePlace = place;
        trackingState = TrackState.START_SELECTED;
    }


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

        com.google.maps.model.LatLng currentPoint =
                new com.google.maps.model.LatLng(event.location.getLatitude(),
                        event.location.getLongitude());

        partialTracedRoute.add(currentPoint);

        if (partialTracedRoute.size() >= 5) {
            try {
                getSnappedPoints();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if (partialPolyline != null) {
                partialPolyline.remove();
            }

            partialPolyline = drawPath(partialTracedRoute, Color.YELLOW);
            if (currentMarker != null) currentMarker.remove();
            currentMarker = mMap.addMarker(currentMarkerOptions.position(latLngFromModel(currentPoint)));
        }

    }

    private void getSnappedPoints() {
        GeoApiContext context = new GeoApiContext().setApiKey(getString(R.string.google_maps_server_key));
        RoadsApi.snapToRoads(context, true, partialTracedRoute.toArray(new com.google.maps.model.LatLng[partialTracedRoute.size()]))
                .setCallback(new PendingResult.Callback<SnappedPoint[]>() {
                    @Override
                    public void onResult(SnappedPoint[] result) {
                        addSnappedPoints(result);
                        if (actualRoute.size() > 0) {
                            latestPoint = actualRoute.get(actualRoute.size() - 1);
                        }

                        Handler h = new Handler(Looper.getMainLooper());
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                //clear and add the last one so that next snapToRoads call has the last point in it.
                                partialTracedRoute.clear();
                                partialTracedRoute.add(latestPoint);

                                //Clear partial polyline - YELLOW
                                if (partialPolyline != null) {
                                    partialPolyline.remove();
                                    partialPolyline = null;
                                }
                                //Clear route polyline - CYAN(travelled)
                                if (routePolyline != null) {
                                    routePolyline.remove();
                                    routePolyline = null;
                                }

                                routePolyline = drawPath(actualRoute, Color.CYAN);

                                if (currentMarker != null) currentMarker.remove();
                                currentMarker = mMap.addMarker(currentMarkerOptions.position(latLngFromModel(latestPoint)));

                                //Deviations
                                calcDeviations(selectedRoute.overviewPolyline.decodePath(), actualRoute); // expected, actual
                                analyzeDeviations();
                                drawDeviation();
                            }
                        });
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        Log.e(TAG, e.getMessage());
                        e.printStackTrace();
                    }
                });
    }

    private void addSnappedPoints(SnappedPoint[] result) {
        //If the last point in the actualRoute is same as first one in
        // result then remove it since it makes it duplicate in the whole route.
        if (actualRoute.size() > 0) {
            if (actualRoute.get(actualRoute.size()-1) == result[0].location) {
                actualRoute.remove(actualRoute.size()-1);
            }
        }
        for (SnappedPoint snappedPoint : result) {
            actualRoute.add(snappedPoint.location);
        }
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void cameraChanged(CameraChangeEvent event) {
    }

}
