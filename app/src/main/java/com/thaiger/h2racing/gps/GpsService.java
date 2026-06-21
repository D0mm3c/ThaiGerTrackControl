package com.thaiger.h2racing.gps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.util.Log;

/**
 * Wraps Android LocationManager for 1 Hz location fixes.
 *
 * Strategy (most-reliable first):
 *   1. GPS_PROVIDER   — satellite, highest accuracy, slow cold start (30–60 s)
 *   2. NETWORK_PROVIDER — cell/WiFi, ~50–100 m, instant first fix
 *
 * Both providers are registered simultaneously so GPS fixes fill in as soon as
 * satellites are acquired, while network fixes cover the warm-up period.
 * {@link #start} also emits the last-known cached location immediately (if
 * fresh enough) so the relay doesn't wait a full second for the first fix.
 *
 * Caller must hold ACCESS_FINE_LOCATION before calling {@link #start}.
 * All callbacks arrive on the main thread.
 */
public class GpsService {

    private static final String TAG = "GpsService";
    private static final long   LAST_KNOWN_MAX_AGE_MS = 30_000;   // 30 s

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
     * Start receiving location fixes. Registers with GPS and network providers
     * as available. Returns true if at least one provider was started.
     * Must be called from the main thread.
     */
    @SuppressLint("MissingPermission")
    public boolean start(Listener l) {
        this.listener = l;
        if (locationManager == null) return false;

        boolean started = false;

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,   // 0 = request hardware maximum rate (~1 Hz)
                    0f,
                    androidListener,
                    Looper.getMainLooper());
            started = true;
        }

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    androidListener,
                    Looper.getMainLooper());
            started = true;
        }

        if (!started) {
            Log.w(TAG, "No location provider available");
            return false;
        }

        // Emit last-known fix immediately so there is no blank period at startup.
        Location last = getBestLastKnown();
        if (last != null && System.currentTimeMillis() - last.getTime() < LAST_KNOWN_MAX_AGE_MS) {
            l.onLocation(last);
        }

        return true;
    }

    public void stop() {
        listener = null;
        if (locationManager != null) {
            locationManager.removeUpdates(androidListener);
        }
    }

    @SuppressLint("MissingPermission")
    private Location getBestLastKnown() {
        if (locationManager == null) return null;
        Location gps = null;
        Location net = null;
        try { gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER); }
        catch (Exception ignored) {}
        try { net = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); }
        catch (Exception ignored) {}
        if (gps == null) return net;
        if (net == null) return gps;
        // prefer the more recently updated fix
        return gps.getTime() >= net.getTime() ? gps : net;
    }
}
