package com.thaiger.h2racing.parser;

import com.thaiger.h2racing.model.TelemetryModel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser für das ESP32-Telemetrieformat aus ThaiGerTechnik_Bluetooth.xlsx.
 *
 * Format: jedes Feld eingerahmt in Asterisken, erstes Zeichen ist der Key.
 *
 *   *A28.3**B25.0**C3**D15:35**E2:15**F25.0**G28.3*…*U70*
 *
 * Felder sind aneinander geklebt: ein einzelnes Asterisk trennt zwei Felder.
 *
 * Wichtig: der Parser ist *zustandslos* und arbeitet feldweise. Er extrahiert
 * jedes `*X<wert>*` einzeln und gibt einen TelemetryModel zurück, in dem nur
 * diese eine Eigenschaft gesetzt ist. Der BluetoothService merged das in das
 * laufende "rollende" Frame. So sind sowohl
 *   - komplette Pakete (alle 21 Felder in einer Zeile)
 *   - als auch ESP-Streaming ohne Newlines
 * sauber verarbeitbar.
 */
public final class TelemetryParser {

    /** Matches *<letter><value>* — Buchstabe + bis zum nächsten `*` alles als Wert. */
    private static final Pattern FIELD = Pattern.compile("\\*([A-Z])([^*]+)\\*");

    private TelemetryParser() {}

    /**
     * Parst ein beliebig langes Stück Stream. Findet alle eingerahmten Felder
     * und füllt sie in einen frischen Model. Felder, die nicht im Input
     * stehen, bleiben auf NaN/-1.
     *
     * Robust gegen Müll davor/danach und Truncation am Ende.
     */
    public static TelemetryModel parsePacket(String chunk) {
        if (chunk == null || chunk.isEmpty()) return null;
        TelemetryModel m = new TelemetryModel();
        boolean anyField = false;

        Matcher matcher = FIELD.matcher(chunk);
        while (matcher.find()) {
            String key = matcher.group(1);
            String val = matcher.group(2);
            if (key == null || val == null) continue;
            val = val.trim();
            if (val.isEmpty()) continue;
            if (applyField(m, key.charAt(0), val)) {
                anyField = true;
            }
        }
        return anyField ? m : null;
    }

    /**
     * Setzt ein einzelnes Feld auf dem Model. Gibt true zurück, wenn der Wert
     * erfolgreich geparsed werden konnte.
     */
    private static boolean applyField(TelemetryModel m, char key, String value) {
        try {
            switch (key) {
                case 'A': m.speedKmh         = Float.parseFloat(value); return true;
                case 'B': m.avgSpeedKmh      = Float.parseFloat(value); return true;
                case 'C': m.laps             = Integer.parseInt(value); return true;
                case 'D': m.totalTimeSec     = parseMinSec(value);     return true;
                case 'E': m.targetLapTimeSec = parseMinSec(value);     return true;
                case 'F': m.optimalSpeedKmh  = Float.parseFloat(value); return true;
                case 'G': m.fcVoltageV       = Float.parseFloat(value); return true;
                case 'H': m.supercapVoltageV = Float.parseFloat(value); return true;
                case 'I': m.motorVoltageV    = Float.parseFloat(value); return true;
                case 'J': m.fcCurrentA       = Float.parseFloat(value); return true;
                case 'K': m.supercapCurrentA = Float.parseFloat(value); return true;
                case 'L': m.motorCurrentA    = Float.parseFloat(value); return true;
                case 'M': m.ownConsumptionA  = Float.parseFloat(value); return true;
                case 'N': m.fcTempC          = Float.parseFloat(value); return true;
                case 'O': m.airPumpDutyPct   = Float.parseFloat(value); return true;
                case 'P': m.drivingHint      = Float.parseFloat(value); return true;
                case 'Q': m.cellVoltDiffMv   = Float.parseFloat(value); return true;
                case 'R': m.fcEnergyWs       = Float.parseFloat(value); return true;
                case 'S': m.motorEnergyWs    = Float.parseFloat(value); return true;
                case 'T': m.fcEfficiencyPct  = Float.parseFloat(value); return true;
                case 'U': m.sysEfficiencyPct = Float.parseFloat(value); return true;
                default:  return false;     // unbekannter Key — ignorieren
            }
        } catch (NumberFormatException e) {
            return false;  // partieller / kaputter Wert — überspringen
        }
    }

    /** "15:35" → 935 (Sekunden). Toleriert reine Sekunden ohne Doppelpunkt. */
    private static int parseMinSec(String s) {
        int idx = s.indexOf(':');
        if (idx < 0) return Integer.parseInt(s);
        int min = Integer.parseInt(s.substring(0, idx).trim());
        int sec = Integer.parseInt(s.substring(idx + 1).trim());
        return min * 60 + sec;
    }
}
