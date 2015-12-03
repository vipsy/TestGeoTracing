package com.vipulsolanki.testgeotracing.model;

import android.location.Location;

import io.realm.RealmObject;

public class LocationUpdateModel extends RealmObject {

    private long timestamp;
    private double latitude;
    private double longitude;
    private double accuracy;
    private double speed = -1;  //in kmph

    public LocationUpdateModel() {

    }

    public LocationUpdateModel(Location location, long timestamp) {
        this.timestamp = timestamp;
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        accuracy = location.getAccuracy();
        if (location.hasSpeed()) { speed = location.getSpeed() * 3.6; }
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public double getSpeed() {
        return speed;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
}
