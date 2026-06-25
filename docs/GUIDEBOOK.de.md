# ThaiGer Track Control — Benutzerhandbuch (Deutsch)

> Telemetrie-Cockpit für die **ThaiGer H₂** Wasserstoff-Brennstoffzellen-Rennfahrzeuge
> (**Thaiger 7** und **Bengalo**).
> [🇬🇧 English](GUIDEBOOK.en.md) · 🇩🇪 Deutsch · [🇨🇳 中文](GUIDEBOOK.zh.md)

---

## Inhaltsverzeichnis

1. [Was dieses Projekt ist](#1-was-dieses-projekt-ist)
2. [Systemüberblick](#2-systemüberblick)
3. [Teil A — Die Android-App (Fahrer / Fahrzeug)](#3-teil-a--die-android-app)
   - [A.1 Installation & erster Start](#a1-installation--erster-start)
   - [A.2 Berechtigungen](#a2-berechtigungen)
   - [A.3 Bildschirm 1 — Fahrzeugauswahl](#a3-bildschirm-1--fahrzeugauswahl)
   - [A.4 Bildschirm 2 — Verbinden (Bluetooth / Demo-Modus)](#a4-bildschirm-2--verbinden)
   - [A.5 Bildschirm 3 — Live-Dashboard](#a5-bildschirm-3--live-dashboard)
   - [A.6 Bildschirm 4 — Run-Zusammenfassung & CSV-Export](#a6-bildschirm-4--run-zusammenfassung--csv-export)
   - [A.7 Bildschirm 5 — Einstellungen](#a7-bildschirm-5--einstellungen)
   - [A.8 Bildschirm 6 — MQTT-Einstellungen](#a8-bildschirm-6--mqtt-einstellungen)
4. [Teil B — Das Engineer-Dashboard (Laptop)](#4-teil-b--das-engineer-dashboard)
   - [B.1 Öffnen & verbinden](#b1-öffnen--verbinden)
   - [B.2 Ansicht 1 — Operations](#b2-ansicht-1--operations)
   - [B.3 Ansicht 2 — Live-Tracking & Karte](#b3-ansicht-2--live-tracking--karte)
   - [B.4 Ansicht 3 — Analytics](#b4-ansicht-3--analytics)
5. [Telemetrie-Protokoll & Feldreferenz](#5-telemetrie-protokoll--feldreferenz)
6. [MQTT-Topics & Payloads](#6-mqtt-topics--payloads)
7. [Fahrzeugprofile](#7-fahrzeugprofile)
8. [Fehlerbehebung](#8-fehlerbehebung)
9. [Feinabstimmung & erweiterte Einstellungen](#9-feinabstimmung--erweiterte-einstellungen)
10. [Glossar](#10-glossar)

---

## 1. Was dieses Projekt ist

ThaiGer Track Control ist ein zweiteiliges Telemetriesystem für ein
Wasserstoff-Brennstoffzellen-Rennauto:

- Eine **Android-App**, die auf einem Smartphone **im Fahrzeug** läuft. Sie
  empfängt Live-Telemetrie vom Steuergerät des Autos über **Bluetooth Low Energy
  (BLE)**, zeigt sie dem Fahrer auf einem Renn-Dashboard, zeichnet den Lauf auf und
  **leitet** die Daten über das Internet per **MQTT** weiter.
- Ein **Engineer-Dashboard** — eine einzelne HTML-Datei
  (`engineer_dashboard.html`) — das im Browser auf dem **Laptop des Ingenieurs** in
  der Box läuft. Es abonniert denselben MQTT-Stream und zeigt Betriebsdaten, eine
  Live-Karte mit leistungsgefärbter GPS-Spur und Analyse-Diagramme.

Beide Hälften kommunizieren nie direkt; sie treffen sich am **MQTT-Broker** in der
Cloud.

---

## 2. Systemüberblick

```
   ┌──────────────┐   BLE    ┌────────────────────┐   MQTT/TLS    ┌──────────────────┐
   │  Fahrzeug-   │ ───────▶ │  Android-App        │ ───────────▶ │  MQTT-Broker     │
   │  Steuergerät │  *A28.3* │  (Handy im Auto)    │  JSON-Frames │  (HiveMQ Cloud)  │
   └──────────────┘          └────────────────────┘               └────────┬─────────┘
                                      │                                      │ WebSocket/TLS
                                      │ GPS (Handy-LocationManager)          ▼
                                      ▼                             ┌──────────────────┐
                               thaiger/{car}/gps                    │ Engineer-        │
                               thaiger/{car}/telemetry              │ Dashboard (HTML) │
                                                                    └──────────────────┘
```

- **Zwei MQTT-Topics** pro Fahrzeug: `thaiger/{car}/telemetry` und
  `thaiger/{car}/gps`.
- `{car}` ist die Fahrzeug-ID: **`thaiger7`** oder **`bengalo`**.
- Das Handy veröffentlicht (publish); das Engineer-Dashboard abonniert
  (subscribe). Beliebig viele Laptops können dasselbe Fahrzeug gleichzeitig
  beobachten.

---

## 3. Teil A — Die Android-App

### A.1 Installation & erster Start

Die App ist ein Standard-Android-Projekt (Gradle). Debug-APK bauen:

```bash
./gradlew assembleDebug
```

Anforderungen:

| Punkt | Wert |
|---|---|
| Minimum-Android | 9.0 (API 28) |
| Ziel-Android | 14 (API 34) |
| Sprache | Java 11 |
| App-ID | `com.thaiger.h2racing` |

Installiere die APK auf dem Handy, das im Auto bleibt. Beim ersten Start öffnet
die App den Bildschirm **Fahrzeugauswahl**.

> **Tipp:** Montiere das Handy im Querformat so, dass der Fahrer es sieht. Das
> Dashboard hält den Bildschirm während eines Laufs wach (siehe Einstellungen →
> *Bildschirm anlassen*).

### A.2 Berechtigungen

Die App fragt Berechtigungen auf dem **Verbinden**-Bildschirm ab, bevor Hardware
genutzt wird. Erteile alle für volle Funktionalität:

| Berechtigung | Wozu sie nötig ist |
|---|---|
| Bluetooth Connect / Scan (Android 12+) | BLE-Modul des Autos koppeln & ansprechen |
| Bluetooth / Bluetooth Admin (Android ≤ 11) | Dasselbe auf älterem Android |
| **Standort (genau)** | Von Android für BLE-Scan **und** für GPS verlangt |
| Standort (grob) | GPS-Fallback |
| Internet | MQTT-Relay zum Ingenieur-Laptop |
| Wake Lock | Bildschirm während eines Laufs anlassen |
| Vibration | Haptisches Feedback bei Warnungen |

Verweigerst du Bluetooth, kannst du trotzdem den **Demo-Modus** (synthetische
Daten) nutzen, um die App zu erkunden.

### A.3 Bildschirm 1 — Fahrzeugauswahl

- Zwei Karten: **Thaiger 7** und **Bengalo**. Tippe eine Karte an, um sie
  auszuwählen (ein blauer Rahmen markiert die Auswahl). Thaiger 7 ist Standard.
- Das gewählte Profil steuert die Warnschwellen und die Maximalleistungs-Skala.
- Tippe oben auf **Settings**, um vor einem Lauf die App-Einstellungen zu öffnen.
- Tippe auf **Connect to …**, um zum Verbinden-Bildschirm zu gehen.

### A.4 Bildschirm 2 — Verbinden

Dieser Bildschirm koppelt das Handy mit dem Auto und startet den Datenstrom.

1. Die App prüft Berechtigungen und ob Bluetooth an ist.
2. Ein Dialog listet deine **gekoppelten Bluetooth-Geräte** plus einen letzten
   Eintrag **⚙ Demo-Modus**.
3. Wähle das Modul deines Autos (z. B. ein Gerät mit Namen HM-10 / HC-05 / ESP32)
   **oder** den **Demo-Modus** für einen synthetischen Datenstrom ohne Hardware.
4. **Pair Neu** öffnet die Android-Bluetooth-Einstellungen, falls dein Modul noch
   nicht gekoppelt ist. **Cancel** kehrt zur Fahrzeugauswahl zurück.

Meldet das Modul **Connected**, wechselt die App **automatisch** zum
Live-Dashboard. Ist das MQTT-Relay in den Einstellungen aktiviert, startet es hier
ebenfalls — der Ingenieur-Laptop empfängt also Daten, sobald der Lauf beginnt.

> Verbindungsabbrüche werden automatisch mit exponentiellem Backoff behandelt
> (1 → 2 → 4 → 8 → 16 s). Du siehst *Reconnecting…* — kein Eingreifen nötig.

### A.5 Bildschirm 3 — Live-Dashboard

Das Dashboard ist die Hauptansicht des Fahrers (Querformat, Bildschirm bleibt
wach). Es zeigt den jeweils neuesten Wert jedes Telemetriefelds, laufend
aktualisiert.

**Wichtige Elemente**

- **Geschwindigkeit** — die große Hero-Zahl (km/h). Ist *Geschwindigkeits-Farbwarnung*
  an (Einstellungen), wird die Zahl **rot**, wenn du unter die Mindest-Zielgeschwindigkeit
  des Autos fällst, sonst hell. Ein kleiner Indikator zeigt *✓ on target* /
  *✗ too slow*.
- **Runden-Overlay** (einzelne Zeile über der unteren Leiste):
  `PREV 1:18 · LAP 3 · CURR 0:09` — vorherige Rundenzeit, aktuelle Rundennummer und
  die laufende aktuelle Rundenzeit.
- **Elektrik-Block** — Spannung & Strom von Brennstoffzelle / Supercap / Motor,
  Eigenverbrauch, Zellspannungsdifferenz.
- **Thermik & Effizienz** — BZ-Temperatur, Luftpumpen-Duty, BZ- und
  System-Wirkungsgrad, Energien, optimale Geschwindigkeit, Fahrhinweis.
- **Warn-Banner** — erscheint, wenn eine Schwelle überschritten wird (z. B.
  BZ-Temperatur oder Zellspannungsdifferenz zu hoch). Mit *Vibration* /
  *Warnton* an gibt es zusätzlich haptisches / akustisches Feedback. Tippe auf
  **Dismiss**, um das Banner auszublenden.

**Einen Lauf beenden:** **lange auf die Geschwindigkeitszahl drücken.** Das stoppt
Bluetooth und das MQTT-Relay, schließt die Lauf-Statistik ab und öffnet die
**Run-Zusammenfassung**.

### A.6 Bildschirm 4 — Run-Zusammenfassung & CSV-Export

Nach einem Lauf siehst du aggregierte Ergebnisse:

- Laufdauer, Distanz, Durchschnittsgeschwindigkeit, verbrauchte Energie, Spitzen-
  Motorleistung, maximale BZ-Temperatur und Anzahl der Warnungen.
- Ein **Leistungsdiagramm** für den gesamten Lauf.

**Schaltflächen**

- **Export CSV** — schreibt jedes aufgezeichnete Telemetrie-Frame in eine
  CSV-Datei und öffnet das Android-Teilen-Menü, sodass du sie per E-Mail, Drive,
  Messenger usw. versenden kannst. Die Datei heißt
  `thaiger_{car}_{YYYYMMDD_HHMM}.csv` und enthält 21 Spalten (Zeit,
  Geschwindigkeit, Spannungen, Ströme, Temperaturen, Energien, Wirkungsgrade,
  Distanz, Motorleistung). Wurden keine Daten aufgezeichnet, erscheint
  *„No data to export“*.
- **New Run** — direkt zurück zum Verbinden für einen weiteren Lauf.
- **Back to Car Select** — zurück zum Anfang.

### A.7 Bildschirm 5 — Einstellungen

Erreichbar über den Link **Settings** in der Fahrzeugauswahl. Alle Werte werden
sofort gespeichert.

**Anzeige**

| Einstellung | Standard | Bedeutung |
|---|---|---|
| Bildschirm anlassen | An | Wake-Lock halten, damit der Bildschirm im Lauf nie schläft |
| Geschwindigkeits-Farbwarnung | An | Geschwindigkeit rot färben, wenn unter der Mindest-Zielgeschwindigkeit |
| Aktualisierungsrate | 50 ms | Wie oft die Dashboard-UI neu zeichnet (20–500 ms) |

**Warnungen**

| Einstellung | Standard | Bedeutung |
|---|---|---|
| Vibration | An | Haptischer Impuls bei einer Warnung |
| Warnton | Aus | Ton bei einer Warnung abspielen |

**Schwellwerte (pro Fahrzeug)** — separat für Thaiger 7 und Bengalo gespeichert:

| Einstellung | Bereich | Standard (Thaiger 7 / Bengalo) |
|---|---|---|
| BZ-Temperatur max | 30–120 °C | 70 °C / 65 °C |
| Zellspannungsdifferenz max | 10–500 mV | 50 mV / 50 mV |

Das Überschreiten einer Schwelle löst die Dashboard-Warnung aus.

### A.8 Bildschirm 6 — MQTT-Einstellungen

Ein Unterbildschirm der Einstellungen, der das Relay zum Ingenieur-Laptop
konfiguriert. Das Relay läuft nur, wenn es **vor** dem Start eines Laufs aktiviert
ist.

| Feld | Standard | Hinweise |
|---|---|---|
| Relay aktivieren | Aus | Hauptschalter — einschalten, um Telemetrie zu senden |
| TLS verwenden | An | Verschlüsselte Verbindung (empfohlen; von HiveMQ Cloud verlangt) |
| Broker-Host | — | z. B. `abc123.s1.eu.hivemq.cloud` |
| Port | 8883 | TLS-MQTT-Port (8883 typisch; 1883 unverschlüsselt) |
| Benutzername | — | Broker-Benutzername |
| Passwort | — | Broker-Passwort (maskiert angezeigt) |
| Publish-Rate | 5 Hz | Telemetrie-Senderate, 1–10 Hz |

> Das Handy sendet über **natives MQTT (TLS)**. Das Engineer-Dashboard verbindet
> sich mit demselben Broker über **MQTT-über-WebSocket** — der Broker muss also
> auch einen WebSocket-Listener anbieten (HiveMQ Cloud tut das, auf Port 8884).
> Siehe Teil B.

---

## 4. Teil B — Das Engineer-Dashboard

`engineer_dashboard.html` ist eine **einzelne, eigenständige Datei**. Sie braucht
keinen Build-Schritt und keinen Server — nur einen modernen Browser und
Internetzugang (für Kartenkacheln, Schriften und die MQTT-/Diagramm-Bibliotheken
vom CDN).

### B.1 Öffnen & verbinden

1. Öffne `engineer_dashboard.html` in Chrome/Edge/Firefox (Doppelklick oder auf
   einem beliebigen statischen Webserver hosten).
2. Der Dialog **Connection Settings** öffnet sich beim ersten Mal automatisch (oder
   klicke oben rechts auf **⚙**).
3. Fülle aus:
   - **Car** — `Thaiger 7` oder `Bengalo` (muss zum Auto passen, als das das Handy
     veröffentlicht).
   - **Broker URL (WebSocket)** — die vollständige WebSocket-URL, z. B.
     `wss://abc123.s1.eu.hivemq.cloud:8884/mqtt`. Nutze `wss://` für TLS (Port 8884
     bei HiveMQ Cloud) oder `ws://host:port` für einen unverschlüsselten Broker im
     lokalen Netz.
   - **Username** / **Password** — Broker-Zugangsdaten.
4. Klicke **Connect**. Die Einstellungen werden im Browser gespeichert
   (`localStorage`); das Dashboard verbindet sich beim nächsten Öffnen automatisch.

Der Status-Punkt oben rechts zeigt die Verbindung: **grau** getrennt, **gelb**
verbindet, **grün** verbunden. Daneben siehst du das Fahrzeug-Badge und die Zeit
des zuletzt empfangenen Frames.

Drei Tabs oben wechseln zwischen den Ansichten: **Operations**, **Live Tracking**,
**Analytics**.

### B.2 Ansicht 1 — Operations

Ein Gesamtüberblick über das ganze Auto auf einen Blick:

- Eine **Hero-Geschwindigkeitskarte** (zielbewusst: blau bei/über der optimalen
  Geschwindigkeit, rot darunter) mit Durchschnittsgeschwindigkeit, Rundenzahl,
  Laufzeit, Zielrunde und Distanz.
- Ein **Elektrik**-Raster: Spannung & Strom von Brennstoffzelle / Supercap / Motor,
  Eigenverbrauch, Zellspannungsdifferenz — jeweils mit Kontextbalken.
- Ein **Thermik & Effizienz**-Raster: BZ-Temperatur, Luftpumpen-Duty, BZ- und
  System-Wirkungsgrad, Energien, optimale Geschwindigkeit, Fahrhinweis.
- Eine pulsierende **LIVE**-Pille bestätigt, dass frische Daten ankommen.

### B.3 Ansicht 2 — Live-Tracking & Karte

Eine bildschirmfüllende dunkle **OpenStreetMap** (Leaflet), die dem Auto folgt.

- **Leistungsgefärbte Spur** — der Weg hinter dem Auto wird segmentweise
  gezeichnet und nach der Motorleistung in diesem Moment gefärbt:
  **grün (0 W) → gelb (100 W) → rot (200 W+)**. Der neueste Teil der Spur ist voll
  deckend und verblasst mit dem Alter — so liest du sowohl *wo* das Auto fuhr als
  auch *wie hart es arbeitete* an jedem Punkt. (200 W ist die maximale
  ThaiGer-Tracking-Leistung; die Skalenkonstante `POWER_MAX` ist in der Datei
  änderbar.)
- **Leistungslegende** (unten links) — die Farbskala plus eine Live-Watt-Anzeige.
- **Rotierender Fahrzeugmarker** zeigt in die Fahrtrichtung.
- **GPS-Statuspanel** (rechts) — Fix-Qualität, Genauigkeit, Breite, Länge, Höhe,
  GPS-Geschwindigkeit, Kurs, Live-Motorleistung, Rundenzahl und ein
  **Filtered**-Zähler (wie viele verrauschte GPS-Fixes verworfen wurden — siehe
  unten).

**GPS-Jitter-Bereinigung.** Rohes GPS kann springen. Jeder Fix wird bereinigt,
bevor er die Karte berührt, in vier Schritten: (1) Fixes mit Genauigkeit schlechter
als 75 m verwerfen; (2) „Teleports“ ablehnen — Fixes, die eine unmögliche
Geschwindigkeit implizieren (> ~162 km/h), mit einer Re-Sync-Sicherung, damit es
nie hängenbleibt; (3) Wackeln unter 1,5 m im Stand ignorieren; (4) leichte
Glättung des restlichen Rauschens. Ergebnis ist eine saubere Spur ohne zufällige
Sprünge.

**Snapshot & Reset pro Runde.** Jedes Mal, wenn der Rundenzähler des Autos
hochzählt, **rendert das Dashboard die Spur der gerade beendeten Runde automatisch
zu einem PNG-Bild und lädt es herunter** (`thaiger-lapN-<Zeitstempel>.png`),
**leert dann die Spur**, damit die neue Runde frisch gezeichnet wird. Ein grüner
Toast bestätigt jede Speicherung.

### B.4 Ansicht 3 — Analytics

- Eine **Live-Telemetrietabelle** mit allen 22 Feldern und ihren aktuellen Werten.
- Ein **Speed**-Diagramm (km/h über Zeit) und ein **Efficiency**-Diagramm (BZ- und
  System-Wirkungsgrad über Zeit), jeweils die letzten ~60 Messwerte.

---

## 5. Telemetrie-Protokoll & Feldreferenz

Das Steuergerät sendet ASCII-Frames über BLE. Jedes Feld ist in Asterisken
eingerahmt, mit einem Einzelbuchstaben als Schlüssel. Beispiel:

```
*A28.3**B25.0**C3**D15:35**N53.4*
```

Der Parser ist stream-tolerant: Er behandelt Teilpakete, mehrteilige Frames und
fehlende Felder und behält stets den letzten bekannten Wert jedes Felds.

| Schlüssel | Feld | Einheit |
|---|---|---|
| A | Geschwindigkeit | km/h |
| B | Durchschnittsgeschwindigkeit | km/h |
| C | Rundenzahl | — |
| D | Gesamtlaufzeit | mm:ss |
| E | Zielrundenzeit | mm:ss |
| F | Optimale Geschwindigkeit | km/h |
| G | Brennstoffzellen-Spannung | V |
| H | Supercap-Spannung | V |
| I | Motorspannung | V |
| J | Brennstoffzellen-Strom | A |
| K | Supercap-Strom | A |
| L | Motorstrom | A |
| M | Eigenverbrauch | A |
| N | Brennstoffzellen-Temperatur | °C |
| O | Luftpumpen-Duty-Cycle | % |
| P | Fahrhinweis | — |
| Q | Zellspannungsdifferenz | mV |
| R | Brennstoffzellen-Energie (kumuliert) | Ws |
| S | Motorenergie (kumuliert) | Ws |
| T | Brennstoffzellen-Wirkungsgrad | % |
| U | System-Wirkungsgrad | % |

Distanz, Motorleistung (V × A) und Energie in Wh werden auf dem Handy berechnet.

---

## 6. MQTT-Topics & Payloads

Alle Nachrichten sind **QoS 0** (fire-and-forget). Leere/NaN-Felder werden
weggelassen, damit die Payloads klein bleiben (~100–200 Bytes).

**`thaiger/{car}/telemetry`** — gesendet mit der konfigurierten Rate (Standard 5 Hz):

```json
{"ts":1736517201234,"A":28.3,"B":25.0,"C":3,"D":"15:35","G":28.3,"N":53.4,"dist":0.42}
```

**`thaiger/{car}/gps`** — gesendet, wenn ein GPS-Fix verfügbar ist:

```json
{"ts":1736517201300,"lat":13.756331,"lon":100.501762,"alt":12.0,"spd":53.4,"acc":3.5,"hdg":270.0}
```

| Feld | Bedeutung | Einheit |
|---|---|---|
| ts | Unix-Zeitstempel | ms |
| lat / lon | Breite / Länge | ° |
| alt | Höhe (falls verfügbar) | m |
| spd | GPS-Geschwindigkeit (falls verfügbar) | km/h |
| acc | Horizontale Genauigkeit (falls verfügbar) | m |
| hdg | Kurs / Peilung (falls verfügbar) | ° |

---

## 7. Fahrzeugprofile

| Profil | id | Max. Motorleistung | BZ-Temp-Limit | Mindest-Zielgeschwindigkeit |
|---|---|---|---|---|
| Thaiger 7 | `thaiger7` | 600 W | 70 °C | 25 km/h |
| Bengalo | `bengalo` | 900 W | 65 °C | 25 km/h |

Schwellwerte können pro Auto in den **Einstellungen** überschrieben werden.
Hinweis: Die Kartenspur des *Engineer-Dashboards* nutzt eine separate **200 W**-
Leistungsskala (`POWER_MAX`), abgestimmt auf die Tracking-Anzeige statt auf den
absoluten Spitzenwert des Autos.

---

## 8. Fehlerbehebung

| Symptom | Ursache / Abhilfe |
|---|---|
| App verbindet nicht über Bluetooth | Sicherstellen, dass das Modul zuerst in den Android-Bluetooth-Einstellungen **gekoppelt** ist; Stromversorgung prüfen; Bluetooth- + Standort-Berechtigungen erteilen. **Pair Neu** aus dem Auswahldialog nutzen. |
| Keine Daten, aber „Connected“ | Falsches Gerät gewählt oder das Steuergerät sendet nicht. Demo-Modus testen, um die App zu prüfen, dann Modul erneut kontrollieren. |
| Engineer-Dashboard wird nie grün | Falsche **WebSocket-URL** (muss `wss://…:8884/mqtt` für HiveMQ Cloud sein, nicht der native Port `8883`), falsche Zugangsdaten oder der Broker hat keinen WebSocket-Listener. |
| Dashboard grün, aber keine Telemetrie | Das **Car** im Dashboard passt nicht zur Fahrzeug-ID des Handys, oder das Relay ist am Handy nicht aktiviert. Beide müssen dasselbe `thaiger7`/`bengalo` nutzen. |
| Karte ist leer | Kein Internet für OSM-Kacheln oder noch keine GPS-Fixes. Die Karte zentriert erst, wenn ein Fix ankommt. |
| GPS-Spur springt | Die Bereinigung sollte das verhindern; hat dein Gerät nur sehr grobe Fixes, `GPS_MAX_ACC` in `engineer_dashboard.html` erhöhen. Der **Filtered**-Zähler zeigt, wie viele Fixes verworfen werden. |
| GPS aktualisiert nur alle paar Sekunden | Freie Sicht zum Himmel sicherstellen und **Standort**-Berechtigung erteilen; drinnen fällt es auf grobe Netzwerk-Fixes zurück. |
| Kein CSV erzeugt | Du musst einen Lauf mit aufgezeichneten Frames abschließen; die *Export CSV*-Schaltfläche meldet „No data to export“, wenn nichts erfasst wurde. |

---

## 9. Feinabstimmung & erweiterte Einstellungen

**Am Handy (Einstellungen / MQTT-Einstellungen):** Bildschirm-Wach, Geschwindigkeits-
Farbe, UI-Aktualisierungsrate, Vibration/Ton, Schwellwerte pro Auto sowie alle
Broker-/Relay-Parameter (siehe A.7 / A.8).

**In `engineer_dashboard.html`** (Konstanten oben im Script-Block):

| Konstante | Standard | Zweck |
|---|---|---|
| `POWER_MAX` | 200 | Watt-Wert, der auf volles Rot der Spur/Legende abgebildet wird |
| `GPS_MAX_ACC` | 75 | GPS-Fixes schlechter als so viele Meter verwerfen |
| `GPS_MAX_SPEED` | 45 | m/s; schnellere implizierte Geschwindigkeit ⇒ als Teleport behandelt |
| `GPS_MIN_MOVE` | 1.5 | m; kleinere Bewegungen gelten als Stillstands-Jitter |
| `GPS_SMOOTH` | 0.35 | EMA-Glättungsfaktor (0 = keine, 1 = roh) |
| `GPS_MAX_REJECTS` | 4 | Re-Sync nach so vielen aufeinanderfolgenden verworfenen Fixes |
| `TRAIL_MAX` | 250 | Maximale Anzahl im Speicher gehaltener Spurpunkte |

---

## 10. Glossar

- **BLE** — Bluetooth Low Energy, die Funkverbindung vom Steuergerät zum Handy.
- **MQTT** — ein leichtgewichtiges Publish/Subscribe-Nachrichtenprotokoll; der
  Broker leitet Nachrichten vom Handy zum Engineer-Dashboard weiter.
- **Broker** — der MQTT-Server (z. B. HiveMQ Cloud), mit dem sich beide Seiten
  verbinden.
- **TLS** — Verschlüsselung für die Broker-Verbindung.
- **WebSocket** — der Transport, über den der Browser MQTT spricht (`ws://` /
  `wss://`).
- **Brennstoffzelle (BZ)** — die Wasserstoff-Energiequelle im Auto.
- **Supercap** — Superkondensator-Puffer, der Energie für Lastspitzen speichert.
- **Telemetrie-Frame** — ein Messpaket vom Auto.
- **Spur (Trail)** — die hinter dem Auto auf der Karte gezeichnete Linie, hier nach
  Motorleistung gefärbt.
