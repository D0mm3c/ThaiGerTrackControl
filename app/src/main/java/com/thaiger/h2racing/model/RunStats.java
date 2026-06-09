package com.thaiger.h2racing.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Sammelt während eines Runs alle Werte, die der Post-Run-Screen braucht.
 *
 * Wird in ConnectingActivity neu erzeugt (frischer Run), vom DashboardActivity
 * bei jedem Telemetrie-Frame mit {@link #update(TelemetryModel, float)}
 * gefüttert und vom PostRunActivity zur Anzeige ausgelesen.
 */
public class RunStats {

    public final long  startedAtMs = System.currentTimeMillis();
    public long  endedAtMs       = 0;

    public int   lastTotalTimeSec = -1;
    public float lastDistanceKm   = Float.NaN;
    public float lastEnergyWh     = Float.NaN;

    public float maxMotorPowerW = 0;
    public float maxFcTempC     = 0;

    private float sumSpeedKmh       = 0;
    private int   countSpeedSamples = 0;

    public int alertCount = 0;
    /** Edge-Detection für Alert-Count: nur false→true zählt. */
    private boolean fcTempOverLast = false;

    /** Power-Verlauf, sample-pro-Update. Wird im PostRun zum Graph runtergesampelt. */
    public final List<Float> powerSamples = new ArrayList<>(1024);

    /**
     * @param fcTempThresholdC Schwelle für Alert-Edge-Detection (aus Prefs).
     */
    public void update(TelemetryModel m, float fcTempThresholdC) {
        if (!Float.isNaN(m.speedKmh)) {
            sumSpeedKmh += m.speedKmh;
            countSpeedSamples++;
        }
        float p = m.motorPowerW();
        if (!Float.isNaN(p)) {
            if (p > maxMotorPowerW) maxMotorPowerW = p;
            powerSamples.add(p);
        }
        if (!Float.isNaN(m.fcTempC)) {
            if (m.fcTempC > maxFcTempC) maxFcTempC = m.fcTempC;
            boolean nowOver = m.fcTempC > fcTempThresholdC;
            if (nowOver && !fcTempOverLast) alertCount++;
            fcTempOverLast = nowOver;
        }
        if (m.totalTimeSec >= 0)         lastTotalTimeSec = m.totalTimeSec;
        if (!Float.isNaN(m.distanceKm))  lastDistanceKm   = m.distanceKm;
        float wh = m.motorEnergyWh();
        if (!Float.isNaN(wh))            lastEnergyWh     = wh;
    }

    public float avgSpeedKmh() {
        return countSpeedSamples == 0 ? 0 : sumSpeedKmh / countSpeedSamples;
    }

    /** ESP-Total-Time wenn vorhanden, sonst Wall-Clock. */
    public int runDurationSec() {
        if (lastTotalTimeSec >= 0) return lastTotalTimeSec;
        long end = endedAtMs > 0 ? endedAtMs : System.currentTimeMillis();
        return (int)((end - startedAtMs) / 1000);
    }

    public void end() {
        if (endedAtMs == 0) endedAtMs = System.currentTimeMillis();
    }
}