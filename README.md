# Thaiger H2 Racing — Telemetry App (Prototyp)

Android-App für das Cockpit-Display im Wasserstoff-Leichtfahrzeug.
Empfängt Live-Telemetrie vom ESP32 über Bluetooth SPP, zeigt die Daten
auf einem im Landscape gesperrten Dashboard.

**Status:** Prototyp v0.1 — Bluetooth + GUI funktionieren, **kein** Datenupload
(CSV-Export, MQTT-Relay) — das kommt im nächsten Schritt.

---

## 1. Was drin ist

| Komponente | Status |
|---|---|
| Bluetooth Classic SPP (HC-05 / ESP32) | ✓ funktional |
| Auto-Reconnect mit Backoff (1s → 2s → 4s → 8s) | ✓ |
| Asterisk-Parser für das echte ESP-Protokoll (21 Felder) | ✓ |
| Mock-Mode (synthetischer Stream, ohne BT testbar) | ✓ |
| 6 Activities (Car Select → Connecting → Dashboard → Alert → Post-Run → Settings) | ✓ |
| FC-Temp-Threshold + Alert-Banner | ✓ |
| Min-Speed-Warnung | ✓ |
| Wake Lock (Screen bleibt an) | ✓ |
| CSV-Export | ☐ folgt |
| MQTT-Relay zum Engineer | ☐ folgt |

---

## 2. Setup in Android Studio (Schritt für Schritt)

### 2.1 Vorbereitung

- **Android Studio** Hedgehog (2023.1) oder neuer. [Download](https://developer.android.com/studio)
- **JDK 17** (kommt mit Android Studio mit)
- **Galaxy A20** (Android 9/10) oder ein anderes Android-9+-Gerät mit USB-Debugging an

### 2.2 Projekt importieren

1. Den kompletten `thaiger_app/`-Ordner entpacken und an einen Ort legen, an dem dein Pfad **keine Umlaute oder Leerzeichen** enthält (z. B. `C:\AndroidProjects\thaiger_app\` oder `~/AndroidProjects/thaiger_app/`).
2. Android Studio öffnen → **File → Open** → den `thaiger_app/`-Ordner wählen (nicht den `app/`-Unterordner — die ganze Wurzel).
3. Android Studio fragt einmal "Trust project?" → **Trust Project**.
4. Es lädt jetzt Gradle herunter (einmalig, ~5–10 Min beim ersten Mal). Den Sync abwarten.
5. Falls ein Popup kommt "Android Gradle Plugin upgrade" → akzeptieren.

### 2.3 Erste Build & Run

1. Phone per USB anschließen, USB-Debugging an, Phone akzeptiert den Computer.
2. In der Toolbar oben rechts: Run-Button (▶) drücken, dein Gerät auswählen.
3. App startet → **Car Select** öffnet sich.

### 2.4 Häufige Erstbefehl-Probleme

- **"SDK location not found"** → in `local.properties` Pfad zum Android SDK eintragen, oder einmal im Android Studio die Defaults setzen.
- **Gradle-Versionsfehler** → in `gradle/wrapper/gradle-wrapper.properties` läuft das Tool automatisch auf einer kompatiblen Version. Sollte Android Studio mit dem Vorschlag kommen, "Update Gradle plugin and sync" — annehmen.

---

## 3. Erstmal ohne ESP32 testen (Demo Mode)

Damit du das gesamte UI sofort durchklicken kannst, gibt's einen Mock-Mode,
der ein synthetisches Telemetrie-Frame im exakten ESP-Format erzeugt:

1. App starten.
2. **Car Select** → "Thaiger 7" wählen → **CONNECT**.
3. **Connecting Screen** zeigt einen Auswahldialog mit gepairten Geräten + "⚙ Demo Mode".
4. Auf **Demo Mode** tappen.
5. Du landest direkt im Dashboard. Die Werte ändern sich live (Speed schwankt um 27, FC-Temp steigt langsam, kreuzt nach einigen Minuten die 70°-Schwelle → Alert-Banner erscheint).
6. **Long-Press auf den Speed** beendet den Run und springt zur Post-Run-Summary.

---

## 4. Mit echtem ESP32 verbinden

### 4.1 ESP-seitig

- ESP32 mit eingebautem Classic-BT (z. B. ESP32-WROOM-32) oder ein HC-05 an UART hängen.
- Der ESP sendet die Telemetrie im Format aus `ThaiGerTechnik_Bluetooth.xlsx`:
  ```
  *A28.3**B25.0**C3**D15:35**E2:15**F25.0**G28.3**H27.4**I27.4**J5.4*
  *K2.7**L8.1**M0.7**N53.4**O63**P65**Q150**R123456**S234567**T70**U70*
  ```
- Baudrate ESP↔HC-05: **19200**.
- Bluetooth-Pairing-Pin am HC-05 üblicherweise `1234` oder `0000`.

### 4.2 Phone-seitig

1. In den **System-Bluetooth-Einstellungen** das ESP/HC-05 einmalig pairen.
2. App starten → Car Select → CONNECT.
3. Im Connecting-Dialog erscheint dein Gerät in der Liste → antappen.
4. Bei Erfolg: automatischer Wechsel ins Dashboard.

Wenn die Verbindung im Run abreißt, läuft Auto-Reconnect automatisch (1s, 2s, 4s, 8s). Die Bottom-Bar zeigt orange `BT ○ Reconnect in 2s…`.

---

## 5. Projektstruktur

```
thaiger_app/
├── README.md
├── build.gradle              ← Top-Level
├── settings.gradle
├── gradle.properties
└── app/
    ├── build.gradle           ← Module-Level
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/thaiger/h2racing/
        │   ├── App.java                    ← Process-Singleton
        │   ├── bt/
        │   │   └── BluetoothService.java   ← BT-Layer, HandlerThread, Auto-Reconnect
        │   ├── model/
        │   │   ├── TelemetryModel.java     ← 21 Felder + Derived
        │   │   └── CarProfile.java         ← Thresholds pro Fahrzeug
        │   ├── parser/
        │   │   └── TelemetryParser.java    ← Asterisk-Format-Parser
        │   └── ui/
        │       ├── CarSelectActivity.java       (Screen 1)
        │       ├── ConnectingActivity.java      (Screen 2)
        │       ├── DashboardActivity.java       (Screen 3)
        │       ├── AlertActivity.java           (Screen 4)
        │       ├── PostRunActivity.java         (Screen 5)
        │       └── SettingsActivity.java        (Screen 6)
        └── res/
            ├── drawable/        ← 6 Shape-Drawables
            ├── layout/          ← 7 XML-Layouts (deine existierenden)
            ├── values/          ← colors.xml, strings.xml, themes.xml
            └── xml/              ← backup-rules
```

---

## 6. Wichtige Architektur-Entscheidungen

### 6.1 Threading

```
ESP32 ──Bluetooth SPP──▶ BluetoothSocket
                              │
                         InputStream.read()
                              │ (HandlerThread "BT-IO")
                              ▼
                         Akkumulator-Buffer
                              │
                         TelemetryParser.parsePacket()  ← regex, zustandslos
                              │
                         rollingFrame.mergeFrom()       ← letzte bekannte Werte
                              │
                         uiHandler.post()
                              │
                              ▼
                         DashboardActivity.applyTelemetry()  ← UI-Thread
```

UI-Thread blockiert **nie** auf BT-Reads.

### 6.2 Parser ist Stream-tolerant

Der ESP sendet das Asterisk-Format ohne Newline-Trenner zwischen Paketen.
Der Parser arbeitet feldweise per Regex `\*([A-Z])([^*]+)\*` und ist zustandslos.
Pakete dürfen mittendrin getrennt ankommen — der Akkumulator-Puffer hält das
unvollständige Stück bis zum nächsten Read.

### 6.3 "Rot bleibt rot"

Wenn ein kritischer Wert (FC-Temp) einmal seine Schwelle überschritten hat,
bleibt der Wert auch nach dem Quittieren des Banners rot markiert. Das ist
explizit gewünscht — der Fahrer soll wissen, dass die FC mal heiß war,
auch wenn sie wieder OK ist.

### 6.4 Mock-Mode nutzt denselben Code-Pfad

Der Mock-Loop erzeugt echte XLSX-Format-Pakete und schickt sie durch
denselben `TelemetryParser`. Was im Demo-Mode funktioniert, wird auch
mit dem ESP funktionieren.

---

## 7. Bekannte Lücken / Roadmap

In dieser Reihenfolge angehen:

1. **Settings-Persistenz** — Wake Lock / Vibration / Thresholds in `SharedPreferences` ablegen
2. **CSV-Logger** — Ring-Buffer im `BluetoothService`, `PostRunActivity` schreibt nach `Downloads/`
3. **Post-Run-Stats** — `RunStats`-Akkumulator (max Power, max FC-Temp, avg Speed) im Service
4. **Power-Graph** — Mini-Graph im Post-Run-Screen mit gespeicherten Power-Werten
5. **MQTT-Relay** — separate Queue + Thread, Eclipse Paho als Dependency
6. **Alert-Sound + Vibration** — bei FC-Temp-Übertretung

---

## 8. ESP-Protokoll-Referenz (für die Firmware-Seite)

| Präfix | Bedeutung | Einheit | Bsp. |
|--------|-----------|---------|------|
| A | Geschwindigkeit | km/h | `*A28.3*` |
| B | Durchschnittsgeschw. | km/h | `*B25.0*` |
| C | Rundenzahl | — | `*C3*` |
| D | Gesamtzeit | mm:ss | `*D15:35*` |
| E | Ziel-Rundenzeit | mm:ss | `*E2:15*` |
| F | Optimale Geschwindigkeit | km/h | `*F25.0*` |
| G | FC-Spannung | V | `*G28.3*` |
| H | Supercap-Spannung | V | `*H27.4*` |
| I | Motor-Spannung | V | `*I27.4*` |
| J | FC-Strom | A | `*J5.4*` |
| K | Supercap-Strom | A | `*K2.7*` |
| L | Motor-Strom | A | `*L8.1*` |
| M | Eigenverbrauchsstrom | A | `*M0.7*` |
| N | FC-Temperatur | °C | `*N53.4*` |
| O | Duty Cycle Luftpumpe | % | `*O63*` |
| P | Fahranweisung | 0..100 | `*P65*` |
| Q | Zell-Spannungsdiff. | mV | `*Q150*` |
| R | FC-Energie | Ws | `*R123456*` |
| S | Motor-Energie | Ws | `*S234567*` |
| T | FC-Wirkungsgrad | % | `*T70*` |
| U | Systemwirkungsgrad | % | `*U70*` |

Quelle: `ThaiGerTechnik_Bluetooth.xlsx`. Baudrate ESP↔HC-05: **19200**.

---

## 9. Was die App **nicht** vom ESP bekommt

Die Layouts zeigen ein paar Felder, die das ESP-Protokoll aktuell nicht sendet:

- **Motor-Temperatur** (`tv_motor_temp`) → wird als `—` angezeigt
- **H₂-Druck** (`tv_h2_pressure`) → wird als `—` angezeigt
- **Distanz** → wird auf dem Phone aus ∫(Speed dt) integriert

Sobald das ESP-Protokoll erweitert wird, ergänze die entsprechenden
Präfixe (V, W, X …) in `TelemetryParser.applyField()` und in `TelemetryModel`.
