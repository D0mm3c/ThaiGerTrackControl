package com.thaiger.h2racing.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.thaiger.h2racing.model.CarProfile;

/**
 * Zentrale Abstraktion über SharedPreferences. Alle Settings-Lese- und
 * Schreibzugriffe gehen hierdurch — keine Activity fummelt direkt mit
 * SharedPreferences.
 *
 * Defaults stehen als Konstanten oben in der Klasse, damit die Werte an
 * einer einzigen Stelle dokumentiert sind.
 */
public final class Prefs {

    private static final String FILE = "thaiger_prefs";

    // Keys
    private static final String K_WAKE_LOCK       = "wake_lock";
    private static final String K_VIBRATE        = "vibrate";
    private static final String K_ALERT_SOUND    = "alert_sound";
    private static final String K_UPDATE_RATE_MS = "update_rate_ms";
    private static final String K_FC_TEMP_PREFIX  = "fc_temp_max_";    // + carId
    private static final String K_CELLDIFF_PREFIX = "cell_diff_max_"; // + carId

    // MQTT-Relay
    private static final String K_MQTT_ENABLED  = "mqtt_enabled";
    private static final String K_MQTT_HOST     = "mqtt_host";
    private static final String K_MQTT_PORT     = "mqtt_port";
    private static final String K_MQTT_USER     = "mqtt_username";
    private static final String K_MQTT_PASS     = "mqtt_password";
    private static final String K_MQTT_TLS      = "mqtt_use_tls";
    private static final String K_MQTT_RATE_HZ  = "mqtt_rate_hz";

    public static final boolean DEFAULT_MQTT_ENABLED = false;
    public static final int     DEFAULT_MQTT_PORT    = 8883;
    public static final boolean DEFAULT_MQTT_TLS     = true;
    public static final int     DEFAULT_MQTT_RATE_HZ = 5;

    // Defaults
    public static final boolean DEFAULT_WAKE_LOCK       = true;
    public static final boolean DEFAULT_VIBRATE        = true;
    public static final boolean DEFAULT_ALERT_SOUND    = false;
    public static final int     DEFAULT_UPDATE_RATE_MS = 50;

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        this.sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ─── Display ───
    public boolean isWakeLock()             { return sp.getBoolean(K_WAKE_LOCK, DEFAULT_WAKE_LOCK); }
    public void setWakeLock(boolean v)      { sp.edit().putBoolean(K_WAKE_LOCK, v).apply(); }

    public int  getUpdateRateMs()           { return sp.getInt(K_UPDATE_RATE_MS, DEFAULT_UPDATE_RATE_MS); }
    public void setUpdateRateMs(int v)      { sp.edit().putInt(K_UPDATE_RATE_MS, Math.max(20, Math.min(500, v))).apply(); }

    // ─── Alerts ───
    public boolean isVibrate()              { return sp.getBoolean(K_VIBRATE, DEFAULT_VIBRATE); }
    public void setVibrate(boolean v)       { sp.edit().putBoolean(K_VIBRATE, v).apply(); }

    public boolean isAlertSound()           { return sp.getBoolean(K_ALERT_SOUND, DEFAULT_ALERT_SOUND); }
    public void setAlertSound(boolean v)    { sp.edit().putBoolean(K_ALERT_SOUND, v).apply(); }

    // ─── Thresholds (pro Fahrzeug) ───
    /** FC-Temp-Threshold. Fällt auf {@link CarProfile#fcTempMaxC} zurück, wenn nichts gespeichert. */
    public float getFcTempMaxC(CarProfile car) {
        return sp.getFloat(K_FC_TEMP_PREFIX + car.id, car.fcTempMaxC);
    }
    public void setFcTempMaxC(CarProfile car, float celsius) {
        float clamped = Math.max(30f, Math.min(120f, celsius));
        sp.edit().putFloat(K_FC_TEMP_PREFIX + car.id, clamped).apply();
    }

    /** Cell-Voltage-Diff-Threshold in mV. Fällt auf {@link CarProfile#cellDiffMaxMv} zurück. */
    public float getCellDiffMaxMv(CarProfile car) {
        return sp.getFloat(K_CELLDIFF_PREFIX + car.id, car.cellDiffMaxMv);
    }
    public void setCellDiffMaxMv(CarProfile car, float mv) {
        float clamped = Math.max(10f, Math.min(500f, mv));
        sp.edit().putFloat(K_CELLDIFF_PREFIX + car.id, clamped).apply();
    }

    // ─── MQTT-Relay ───
    public boolean isMqttEnabled()              { return sp.getBoolean(K_MQTT_ENABLED, DEFAULT_MQTT_ENABLED); }
    public void    setMqttEnabled(boolean v)    { sp.edit().putBoolean(K_MQTT_ENABLED, v).apply(); }

    public String  getMqttHost()                { return sp.getString(K_MQTT_HOST, ""); }
    public void    setMqttHost(String host)     { sp.edit().putString(K_MQTT_HOST, host == null ? "" : host.trim()).apply(); }

    public int     getMqttPort()                { return sp.getInt(K_MQTT_PORT, DEFAULT_MQTT_PORT); }
    public void    setMqttPort(int port)        { sp.edit().putInt(K_MQTT_PORT, Math.max(1, Math.min(65535, port))).apply(); }

    public String  getMqttUsername()            { return sp.getString(K_MQTT_USER, ""); }
    public void    setMqttUsername(String u)    { sp.edit().putString(K_MQTT_USER, u == null ? "" : u.trim()).apply(); }

    public String  getMqttPassword()            { return sp.getString(K_MQTT_PASS, ""); }
    public void    setMqttPassword(String p)    { sp.edit().putString(K_MQTT_PASS, p == null ? "" : p).apply(); }

    public boolean isMqttUseTls()               { return sp.getBoolean(K_MQTT_TLS, DEFAULT_MQTT_TLS); }
    public void    setMqttUseTls(boolean v)     { sp.edit().putBoolean(K_MQTT_TLS, v).apply(); }

    public int     getMqttRateHz()              { return sp.getInt(K_MQTT_RATE_HZ, DEFAULT_MQTT_RATE_HZ); }
    public void    setMqttRateHz(int hz)        { sp.edit().putInt(K_MQTT_RATE_HZ, Math.max(1, Math.min(10, hz))).apply(); }
}