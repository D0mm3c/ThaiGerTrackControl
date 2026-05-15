package com.thaiger.h2racing.model;

/**
 * Telemetriedaten vom ESP32 — alle 21 Felder aus ThaiGerTechnik_Bluetooth.xlsx.
 *
 * Protokoll-Format: jedes Feld eingerahmt in Asterisken, Buchstabe als Key.
 *   Beispiel-Frame: *A28.3**B25.0**C3**D15:35**E2:15**F25.0**G28.3**H27.4*
 *                   *I27.4**J5.4**K2.7**L8.1**M0.7**N53.4**O63**P65**Q150*
 *                   *R123456**S234567**T70**U70*
 *
 * Defaults: NaN bzw. -1 bedeutet "in diesem Frame nicht gesendet". So bleibt
 * die App stabil, auch wenn ein partielles Paket reinkommt (z. B. beim Boot).
 */
public class TelemetryModel {

    /** Empfangszeitpunkt auf dem Phone. */
    public long timestampMs = System.currentTimeMillis();

    // ─── A..F: Geschwindigkeit / Zeit / Runden ───
    /** A — aktuelle Geschwindigkeit [km/h]. Hero-Metrik. */
    public float speedKmh         = Float.NaN;
    /** B — Durchschnittsgeschwindigkeit [km/h]. */
    public float avgSpeedKmh      = Float.NaN;
    /** C — aktuelle Rundenzahl. */
    public int   laps             = -1;
    /** D — Gesamtzeit seit Run-Start [s]. ESP sendet "mm:ss". */
    public int   totalTimeSec     = -1;
    /** E — Zielzeit für aktuelle Runde [s]. */
    public int   targetLapTimeSec = -1;
    /** F — optimale Geschwindigkeit [km/h]. */
    public float optimalSpeedKmh  = Float.NaN;

    // ─── G..M: Spannungen / Ströme ───
    public float fcVoltageV       = Float.NaN;   // G
    public float supercapVoltageV = Float.NaN;   // H
    public float motorVoltageV    = Float.NaN;   // I
    public float fcCurrentA       = Float.NaN;   // J
    public float supercapCurrentA = Float.NaN;   // K
    public float motorCurrentA    = Float.NaN;   // L
    public float ownConsumptionA  = Float.NaN;   // M

    // ─── N..U: Temp / Effizienz / Energie ───
    /** N — Brennstoffzellentemperatur [°C]. Primärer Health-Indikator. */
    public float fcTempC          = Float.NaN;
    /** O — Duty Cycle Luftpumpe [%]. */
    public float airPumpDutyPct   = Float.NaN;
    /** P — Fahranweisung: 0..50 langsamer, 50..100 schneller. */
    public float drivingHint      = Float.NaN;
    /** Q — Zellspannungsdifferenz [mV]. >50mV → rot. */
    public float cellVoltDiffMv   = Float.NaN;
    /** R — Brennstoffzellen-Energie kumuliert [Ws]. */
    public float fcEnergyWs       = Float.NaN;
    /** S — Motor-Energie kumuliert [Ws]. */
    public float motorEnergyWs    = Float.NaN;
    /** T — Brennstoffzellen-Wirkungsgrad [%]. */
    public float fcEfficiencyPct  = Float.NaN;
    /** U — Systemwirkungsgrad [%]. */
    public float sysEfficiencyPct = Float.NaN;

    // ─── Derived (auf dem Phone berechnet) ───
    /** Auf Phone integrierte Distanz seit Run-Start [km]. ESP sendet keine. */
    public float distanceKm       = Float.NaN;

    // ─── Berechnete Leistungen ───
    public float motorPowerW() {
        if (Float.isNaN(motorVoltageV) || Float.isNaN(motorCurrentA)) return Float.NaN;
        return motorVoltageV * motorCurrentA;
    }

    public float fcPowerW() {
        if (Float.isNaN(fcVoltageV) || Float.isNaN(fcCurrentA)) return Float.NaN;
        return fcVoltageV * fcCurrentA;
    }

    /** Motorenergie in Wh statt Ws — für die Bottom-Bar. */
    public float motorEnergyWh() {
        if (Float.isNaN(motorEnergyWs)) return Float.NaN;
        return motorEnergyWs / 3600f;
    }

    /**
     * Übernimmt alle Felder aus {@code other}, die dort *gesetzt* sind
     * (nicht NaN / nicht -1). Wird vom Parser benutzt, um ein "rollendes"
     * Frame mit den jeweils letzten bekannten Werten zu führen.
     */
    public void mergeFrom(TelemetryModel other) {
        if (!Float.isNaN(other.speedKmh))         speedKmh         = other.speedKmh;
        if (!Float.isNaN(other.avgSpeedKmh))      avgSpeedKmh      = other.avgSpeedKmh;
        if (other.laps >= 0)                      laps             = other.laps;
        if (other.totalTimeSec >= 0)              totalTimeSec     = other.totalTimeSec;
        if (other.targetLapTimeSec >= 0)          targetLapTimeSec = other.targetLapTimeSec;
        if (!Float.isNaN(other.optimalSpeedKmh))  optimalSpeedKmh  = other.optimalSpeedKmh;
        if (!Float.isNaN(other.fcVoltageV))       fcVoltageV       = other.fcVoltageV;
        if (!Float.isNaN(other.supercapVoltageV)) supercapVoltageV = other.supercapVoltageV;
        if (!Float.isNaN(other.motorVoltageV))    motorVoltageV    = other.motorVoltageV;
        if (!Float.isNaN(other.fcCurrentA))       fcCurrentA       = other.fcCurrentA;
        if (!Float.isNaN(other.supercapCurrentA)) supercapCurrentA = other.supercapCurrentA;
        if (!Float.isNaN(other.motorCurrentA))    motorCurrentA    = other.motorCurrentA;
        if (!Float.isNaN(other.ownConsumptionA))  ownConsumptionA  = other.ownConsumptionA;
        if (!Float.isNaN(other.fcTempC))          fcTempC          = other.fcTempC;
        if (!Float.isNaN(other.airPumpDutyPct))   airPumpDutyPct   = other.airPumpDutyPct;
        if (!Float.isNaN(other.drivingHint))      drivingHint      = other.drivingHint;
        if (!Float.isNaN(other.cellVoltDiffMv))   cellVoltDiffMv   = other.cellVoltDiffMv;
        if (!Float.isNaN(other.fcEnergyWs))       fcEnergyWs       = other.fcEnergyWs;
        if (!Float.isNaN(other.motorEnergyWs))    motorEnergyWs    = other.motorEnergyWs;
        if (!Float.isNaN(other.fcEfficiencyPct))  fcEfficiencyPct  = other.fcEfficiencyPct;
        if (!Float.isNaN(other.sysEfficiencyPct)) sysEfficiencyPct = other.sysEfficiencyPct;
        if (!Float.isNaN(other.distanceKm))       distanceKm       = other.distanceKm;
        this.timestampMs = other.timestampMs;
    }
}
