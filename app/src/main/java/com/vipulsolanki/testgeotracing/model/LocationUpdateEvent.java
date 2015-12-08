package com.vipulsolanki.testgeotracing.model;

import android.location.Location;

/**
 * Created by vipul on 12/4/15.
 */
public class LocationUpdateEvent {
    public final Location location;
    public final long timestamp;

    public LocationUpdateEvent(Location location, long timestamp) {
        this.location = location;
        this.timestamp = timestamp;
    }
}
