package com.thaiger.h2racing.relay;

import com.thaiger.h2racing.model.TelemetryModel;

import java.util.Locale;

/**
 * Codiert ein {@link TelemetryModel} als kompaktes JSON-Objekt für den MQTT-Relay.
 *
 * Format orientiert sich am ursprünglichen ESP-Protokoll — ein Buchstabe als Key,
 * Zahlen ohne unnötige Nachkommastellen. Felder die NaN / -1 sind werden
 * weggelassen, damit der Payload klein bleibt (typisch 100–200 Bytes).
 *
 * Beispiel:
 * {"ts":1736517201234,"A":28.3,"B":25.0,"C":3,"D":"15:35","E":"2:15",
 *  "G":28.3,"J":5.4,"L":8.1,"N":53.4,"Q":30,"T":70,"U":70,"dist":0.42}
 */
public final class TelemetryJsonEncoder {

    private TelemetryJsonEncoder() {}

    public static String encode(TelemetryModel m) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"ts\":").append(m.timestampMs);

        appendF(sb, "A", m.speedKmh);
        appendF(sb, "B", m.avgSpeedKmh);
        appendI(sb, "C", m.laps);
        appendT(sb, "D", m.totalTimeSec);
        appendT(sb, "E", m.targetLapTimeSec);
        appendF(sb, "F", m.optimalSpeedKmh);
        appendF(sb, "G", m.fcVoltageV);
        appendF(sb, "H", m.supercapVoltageV);
        appendF(sb, "I", m.motorVoltageV);
        appendF(sb, "J", m.fcCurrentA);
        appendF(sb, "K", m.supercapCurrentA);
        appendF(sb, "L", m.motorCurrentA);
        appendF(sb, "M", m.ownConsumptionA);
        appendF(sb, "N", m.fcTempC);
        appendF(sb, "O", m.airPumpDutyPct);
        appendF(sb, "P", m.drivingHint);
        appendF(sb, "Q", m.cellVoltDiffMv);
        appendF(sb, "R", m.fcEnergyWs);
        appendF(sb, "S", m.motorEnergyWs);
        appendF(sb, "T", m.fcEfficiencyPct);
        appendF(sb, "U", m.sysEfficiencyPct);
        appendF(sb, "dist", m.distanceKm);   // phone-derived, extra key

        sb.append('}');
        return sb.toString();
    }

    private static void appendF(StringBuilder sb, String k, float v) {
        if (Float.isNaN(v)) return;
        sb.append(",\"").append(k).append("\":");
        // 1 decimal place, Locale.US for decimal point
        sb.append(String.format(Locale.US, "%.1f", v));
    }

    private static void appendI(StringBuilder sb, String k, int v) {
        if (v < 0) return;
        sb.append(",\"").append(k).append("\":").append(v);
    }

    /** Seconds → "mm:ss" string, or omit if negative. */
    private static void appendT(StringBuilder sb, String k, int totalSec) {
        if (totalSec < 0) return;
        sb.append(",\"").append(k).append("\":\"")
                .append(totalSec / 60).append(':')
                .append(String.format(Locale.US, "%02d", totalSec % 60))
                .append('"');
    }
}