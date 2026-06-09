package com.thaiger.h2racing.gps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;

/**
 * Wraps Android LocationManager for GPS-only fixes at 1 Hz.
 *
 * Caller must hold ACCESS_FINE_LOCATION before calling {@link #start}.
 * Callbacks arrive on the main thread.
 */
public class GpsService {

    public interface Listener {
        void onLocation(Location location);
    }

    private final LocationManager locationManager;
    private Listener listener;

    private final LocationListener androidListener = location -> {
        Listener l = listener;
        if (l != null) l.onLocation(location);
    };

    public GpsService(Context ctx) {
        locationManager = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Start receiving GPS fixes. Returns false if GPS provider is unavailable.
     * Must be called from the main thread (callbacks are delivered there).
     */
    @SuppressLint("MissingPermission")
    public boolean start(Listener l) {
        this.listener = l;
        if (locationManager == null) return false;
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false;
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,   // minTimeMs — 1 Hz
                0f,      // minDistanceMeters
                androidListener,
                Looper.getMainLooper());
        return true;
    }

    public void stop() {
        listener = null;
        if (locationManager != null) {
            locationManager.removeUpdates(androidListener);
        }
    }
}
