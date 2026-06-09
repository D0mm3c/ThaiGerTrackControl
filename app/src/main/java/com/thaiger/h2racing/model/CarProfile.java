package com.thaiger.h2racing.model;

/**
 * Pro-Fahrzeug-Konfiguration: Schwellwerte, Mindestgeschwindigkeit, Anzeigename.
 *
 * Die Zahlen sind Platzhalter — sobald das Team Erfahrungswerte vom realen
 * Stack hat, hier (oder via Settings-Screen) anpassen.
 *
 * Quelle für cellDiffMaxMv: ThaiGerTechnik_Bluetooth.xlsx — "more than 50mV red".
 */
public enum CarProfile {

    THAIGER_7(
        "thaiger7",
        "Thaiger 7",
        "Prototype · H₂ Fuel Cell",
        /* fcTempMaxC     */ 70.0f,
        /* cellDiffMaxMv  */ 50.0f,
        /* motorPowerMaxW */ 600.0f,
        /* minSpeedKmh    */ 25.0f),

    BENGALO(
        "bengalo",
        "Bengalo",
        "Urban Concept · H₂ Fuel Cell",
        /* fcTempMaxC     */ 65.0f,
        /* cellDiffMaxMv  */ 50.0f,
        /* motorPowerMaxW */ 900.0f,
        /* minSpeedKmh    */ 25.0f);

    public final String id;
    public final String displayName;
    public final String subtitle;
    public final float  fcTempMaxC;
    public final float  cellDiffMaxMv;
    public final float  motorPowerMaxW;
    public final float  minSpeedKmh;

    CarProfile(String id, String displayName, String subtitle,
               float fcTempMaxC, float cellDiffMaxMv,
               float motorPowerMaxW, float minSpeedKmh) {
        this.id = id;
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.fcTempMaxC = fcTempMaxC;
        this.cellDiffMaxMv = cellDiffMaxMv;
        this.motorPowerMaxW = motorPowerMaxW;
        this.minSpeedKmh = minSpeedKmh;
    }

    public static CarProfile fromId(String id) {
        for (CarProfile p : values()) if (p.id.equals(id)) return p;
        return THAIGER_7;
    }
}
