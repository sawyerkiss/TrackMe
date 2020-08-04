package com.tamtran.trackme;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;

class TrackInfo implements Parcelable {
    private LatLng startLocation;
    private LatLng endLocation;
    private String duration;

    private String avgSpeed;
    private String distance;

    public LatLng getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(LatLng startLocation) {
        this.startLocation = startLocation;
    }

    public LatLng getEndLocation() {
        return endLocation;
    }

    public void setEndLocation(LatLng endLocation) {
        this.endLocation = endLocation;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getAvgSpeed() {
        return avgSpeed;
    }

    public void setAvgSpeed(String avgSpeed) {
        this.avgSpeed = avgSpeed;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public TrackInfo(LatLng startLocation, LatLng endLocation, String duration, String avgSpeed, String distance) {
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.duration = duration;
        this.avgSpeed = avgSpeed;
        this.distance = distance;
    }

    protected TrackInfo(Parcel in) {
        this.startLocation = in.readParcelable(LatLng.class.getClassLoader());
        this.endLocation = in.readParcelable(LatLng.class.getClassLoader());
        this.duration = in.readString();
        this.avgSpeed = in.readString();
        this.distance = in.readString();
    }

    public static final Creator<TrackInfo> CREATOR = new Creator<TrackInfo>() {
        @Override
        public TrackInfo createFromParcel(Parcel in) {
            return new TrackInfo(in);
        }

        @Override
        public TrackInfo[] newArray(int size) {
            return new TrackInfo[size];
        }
    };


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(this.startLocation,0);
        parcel.writeParcelable(this.endLocation, 0);
        parcel.writeString(duration);
        parcel.writeString(avgSpeed);
        parcel.writeString(distance);
    }
}