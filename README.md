# Thaiger H2 Racing — Telemetrie-System

Echtzeit-Telemetrie für Wasserstoff-Brennstoffzellen-Fahrzeuge der
**Fachhochschule Stralsund**. Das System überträgt Betriebsdaten
vom ESP32 per BLE (HM-10) an eine Android-Cockpit-App und leitet
sie per MQTT an ein stationäres Engineer-Dashboard weiter.

---

## Übersicht

```
ESP32 ──UART 19200 Bd──► HM-10 (BLE 4.0) ──GATT FFE1──► Android-App
                                                              │
                                                    MQTT / TLS:8883
                                                              │
                                                         HiveMQ Cloud
                                                              │
                                                    WebSocket / TLS:8884
                                                              │
                                                    engineer_dashboard.html
```

### Fahrzeuge

| Fahrzeug   | Typ              |
|------------|-----------------|
| Thaiger 7  | Prototype · H₂  |
| Bengalo    | Urban Concept · H₂ |

---

## Repository-Struktur

```
thaiger_app/                        Android-Studio-Projekt
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/thaiger/h2racing/
│       │   ├── App.java
│       │   ├── bt/BluetoothService.java      BLE GATT (HM-10)
│       │   ├── model/
│       │   │   ├── TelemetryModel.java        21 Felder + derived
│       │   │   ├── CarProfile.java            Schwellwerte pro Auto
│       │   │   └── RunStats.java              Run-Akkumulator
│       │   ├── parser/TelemetryParser.java    Asterisk-Format-Parser
│       │   ├── relay/
│       │   │   ├── FrameRelay.java
│       │   │   ├── MqttRelayService.java      Paho MQTT + Queue
│       │   │   └── TelemetryJsonEncoder.java
│       │   ├── ui/                            6 Activities
│       │   └── util/
│       │       ├── Prefs.java                 SharedPreferences-Wrapper
│       │       └── AlertFx.java               Vibration + Ton
│       └── res/layout/                        7 XML-Layouts
│
engineer_dashboard.html             Engineer-Dashboard (Browser, kein Server)
thaiger_dokumentation.tex           Vollständige Systemdokumentation (LaTeX)
```

---

## Voraussetzungen

### Android-App
- Android Studio Hedgehog (2023.1) oder neuer
- Android-Gerät mit BLE-Support, Android 9+ (getestet: Samsung Galaxy A20, Android 11)
- HM-10 BLE-Modul am ESP32, Baudrate **19200**

### Engineer-Dashboard
- Aktueller Browser (Chrome / Firefox)
- HiveMQ Cloud Account (kostenloser Free-Tier)

---

## Schnellstart

### 1. Android-App bauen

```bash
# Projekt in Android Studio öffnen:
# File → Open → thaiger_app/   (Wurzel-Ordner)
# Gradle-Sync abwarten → Run ▶
```

### 2. HM-10 pairen

In den System-Bluetooth-Einstellungen des Smartphones das HM-10 einmalig
pairen (PIN: `000000` oder `1234`).

### 3. App starten

1. Fahrzeug wählen → **CONNECT**
2. HM-10 in der Geräteliste auswählen
3. Dashboard öffnet sich automatisch

> **Demo Mode** — kein ESP nötig: beim Connecting-Screen
> **⚙ Demo Mode** auswählen. Generiert synthetischen Telemetrie-Stream.

### 4. Engineer-Dashboard

```bash
# engineer_dashboard.html im Browser öffnen (Doppelklick)
# → Einstellungs-Dialog erscheint automatisch
# Host, Port (8884), Username, Passwort eintragen → Connect
```

---

## Konfiguration

### MQTT-Relay (optional)

In der App: **Einstellungen → MQTT broker →**

| Feld         | Wert                              |
|--------------|-----------------------------------|
| Broker host  | `abc123.s1.eu.hivemq.cloud`       |
| Port (App)   | `8883` (MQTT/TLS)                 |
| Port (Browser)| `8884` (WebSocket/TLS)           |
| TLS          | Ein                               |
| Publish rate | 5 Hz                              |

Topic-Schema: `thaiger/<carId>/telemetry` (`carId` = `thaiger7` oder `bengalo`)

### Schwellwerte (App-Einstellungen)

| Parameter       | Thaiger 7 | Bengalo | Alarm                    |
|-----------------|-----------|---------|--------------------------|
| FC Temp max     | 70 °C     | 65 °C   | Rot + Vibration + Banner |
| Cell Diff max   | 50 mV     | 50 mV   | Rot                      |
| Min Speed       | 25 km/h   | 25 km/h | Geschwindigkeit rot       |

---

## ESP-Protokoll

Format: `*<Key><Wert>*` — Asterisk-eingerahmt, aneinandergereiht.

```
*A28.3**B25.0**C3**D15:35**E2:15**F25.0**G28.3**H27.4*
*I27.4**J5.4**K2.7**L8.1**M0.7**N53.4**O63**P65**Q150*
*R123456**S234567**T70**U70*
```

| Key | Feld                  | Einheit | Alarm      |
|-----|-----------------------|---------|------------|
| A   | Geschwindigkeit       | km/h    | < 25 km/h  |
| B   | Durchschnittgeschw.   | km/h    |            |
| C   | Rundenzahl            | —       |            |
| D   | Gesamtzeit            | mm:ss   |            |
| E   | Ziel-Rundenzeit       | mm:ss   |            |
| F   | Optimale Geschw.      | km/h    |            |
| G–M | Spannungen / Ströme   | V / A   |            |
| N   | FC-Temperatur         | °C      | > Schwelle |
| O   | Luftpumpen-Duty       | %       |            |
| P   | Fahranweisung         | 0–100   |            |
| Q   | Zellspannungsdiff.    | mV      | > 50 mV    |
| R   | FC-Energie            | Ws      |            |
| S   | Motor-Energie         | Ws      |            |
| T   | FC-Wirkungsgrad       | %       |            |
| U   | Systemwirkungsgrad    | %       |            |

Vollständige Referenz: **Kapitel 3** der Systemdokumentation.

---

## Abhängigkeiten

```groovy
// app/build.gradle
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'androidx.cardview:cardview:1.0.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
implementation 'androidx.core:core:1.13.1'
implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
```

---

## Run beenden

**Long-Press** auf den Geschwindigkeitswert im Dashboard → Post-Run-Screen
mit Statistiken (Duration, Distance, Avg Speed, Peak Power, Max FC Temp,
Alerts, Power-Graph).

---

## Häufige Probleme

| Problem | Lösung |
|---------|--------|
| HM-10 nicht in Liste | Erst in Android-Bluetooth-Einstellungen pairen |
| GATT Error 133 | Bluetooth aus/ein, Modul-Neustart |
| Keine Daten | HM-10 Baudrate = 19200 (`AT+BAUD2`) prüfen |
| RELAY ✕ | Internetverbindung + Broker-Credentials prüfen |
| Dashboard leer | Port 8884 (WebSocket) verwenden |

Ausführliche Fehlerbehebung: **Kapitel 9** der Systemdokumentation.

---

## Dokumentation

```bash
# PDF kompilieren (LaTeX erforderlich):
pdflatex thaiger_dokumentation.tex
pdflatex thaiger_dokumentation.tex   # zweimal für Inhaltsverzeichnis
```

Kapitel: Systemübersicht · Hardware-Setup · ESP-Protokoll · Android-App ·
Bedienung · Einstellungen · MQTT & Engineer-Dashboard · Technische Referenz ·
Fehlerbehebung · Protokoll-Anhang

---

## Entwicklung

### Neues ESP-Feld hinzufügen

1. `TelemetryModel.java` — neues `float`-Feld + `mergeFrom()`
2. `TelemetryParser.java` — neuer `case` in `applyField()`
3. `TelemetryJsonEncoder.java` — neuer `appendF()`-Aufruf
4. `DashboardActivity.java` — Feld auf View-ID mappen

### Build-Varianten

| Modus      | Aktivierung                          | Verhalten                      |
|------------|--------------------------------------|--------------------------------|
| Real BLE   | Geräteauswahl im Connecting-Screen   | HM-10 per GATT                |
| Demo Mode  | „⚙ Demo Mode" im Connecting-Dialog  | Synthetischer Stream           |
| MQTT Relay | In Einstellungen aktivieren          | Parallel zu BLE, eigener Thread|

---

*FH Stralsund · Thaiger H2 Racing Team · v0.1*