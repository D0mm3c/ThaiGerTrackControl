# ThaiGer Track Control — User Guidebook (English)

> Telemetry cockpit for the **ThaiGer H₂** hydrogen fuel-cell racing vehicles
> (**Thaiger 7** and **Bengalo**).
> 🇬🇧 English · [🇩🇪 Deutsch](GUIDEBOOK.de.md) · [🇨🇳 中文](GUIDEBOOK.zh.md)

---

## Table of contents

1. [What this project is](#1-what-this-project-is)
2. [System overview](#2-system-overview)
3. [Part A — The Android app (driver / car)](#3-part-a--the-android-app)
   - [A.1 Installation & first start](#a1-installation--first-start)
   - [A.2 Permissions](#a2-permissions)
   - [A.3 Screen 1 — Car Select](#a3-screen-1--car-select)
   - [A.4 Screen 2 — Connecting (Bluetooth / Demo Mode)](#a4-screen-2--connecting)
   - [A.5 Screen 3 — Live Dashboard](#a5-screen-3--live-dashboard)
   - [A.6 Screen 4 — Post-Run Summary & CSV export](#a6-screen-4--post-run-summary--csv-export)
   - [A.7 Screen 5 — Settings](#a7-screen-5--settings)
   - [A.8 Screen 6 — MQTT Settings](#a8-screen-6--mqtt-settings)
4. [Part B — The Engineer Dashboard (laptop)](#4-part-b--the-engineer-dashboard)
   - [B.1 Opening & connecting](#b1-opening--connecting)
   - [B.2 View 1 — Operations](#b2-view-1--operations)
   - [B.3 View 2 — Live Tracking & map](#b3-view-2--live-tracking--map)
   - [B.4 View 3 — Analytics](#b4-view-3--analytics)
5. [Telemetry protocol & field reference](#5-telemetry-protocol--field-reference)
6. [MQTT topics & payloads](#6-mqtt-topics--payloads)
7. [Vehicle profiles](#7-vehicle-profiles)
8. [Troubleshooting](#8-troubleshooting)
9. [Tuning & advanced settings](#9-tuning--advanced-settings)
10. [Glossary](#10-glossary)

---

## 1. What this project is

ThaiGer Track Control is a two-part telemetry system for a hydrogen fuel-cell
race car:

- An **Android app** that runs on a phone **inside the car**. It receives live
  telemetry from the car's controller over **Bluetooth Low Energy (BLE)**, shows
  it on a race dashboard for the driver, records the run, and **relays** the data
  over the internet via **MQTT**.
- An **Engineer Dashboard** — a single HTML file (`engineer_dashboard.html`) — that
  runs in a browser on the **engineer's laptop** in the pit. It subscribes to the
  same MQTT stream and shows operations data, a live map with a power-colored GPS
  trail, and analytics charts.

The two halves never talk directly; they meet at the **MQTT broker** in the cloud.

---

## 2. System overview

```
   ┌──────────────┐   BLE    ┌────────────────────┐   MQTT/TLS    ┌──────────────────┐
   │  Car         │ ───────▶ │  Android app        │ ───────────▶ │  MQTT broker     │
   │  controller  │  *A28.3* │  (phone in the car) │  JSON frames │  (HiveMQ Cloud)  │
   └──────────────┘          └────────────────────┘               └────────┬─────────┘
                                      │                                      │ WebSocket/TLS
                                      │ GPS (phone LocationManager)          ▼
                                      ▼                             ┌──────────────────┐
                               thaiger/{car}/gps                    │ Engineer         │
                               thaiger/{car}/telemetry              │ Dashboard (HTML) │
                                                                    └──────────────────┘
```

- **Two MQTT topics** per car: `thaiger/{car}/telemetry` and `thaiger/{car}/gps`.
- `{car}` is the vehicle id: **`thaiger7`** or **`bengalo`**.
- The phone publishes; the engineer dashboard subscribes. Any number of laptops
  can watch the same car at once.

---

## 3. Part A — The Android app

### A.1 Installation & first start

The app is a standard Android project (Gradle). To build a debug APK:

```bash
./gradlew assembleDebug
```

Requirements:

| Item | Value |
|---|---|
| Minimum Android | 9.0 (API 28) |
| Target Android | 14 (API 34) |
| Language | Java 11 |
| App id | `com.thaiger.h2racing` |

Install the resulting APK on the phone that will live in the car. On first launch
the app opens on the **Car Select** screen.

> **Tip:** Mount the phone in landscape where the driver can see it. The dashboard
> keeps the screen awake during a run (see Settings → *Keep screen on*).

### A.2 Permissions

The app asks for permissions on the **Connecting** screen, before any hardware is
used. Grant all of them for full functionality:

| Permission | Why it is needed |
|---|---|
| Bluetooth Connect / Scan (Android 12+) | Pair & talk to the car's BLE module |
| Bluetooth / Bluetooth Admin (Android ≤ 11) | Same, on older Android |
| **Location (fine)** | Required by Android for BLE scanning **and** for GPS |
| Location (coarse) | GPS fallback |
| Internet | MQTT relay to the engineer laptop |
| Wake Lock | Keep the screen on during a run |
| Vibrate | Haptic feedback for alerts |

If you deny Bluetooth, you can still use **Demo Mode** (synthetic data) to explore
the app.

### A.3 Screen 1 — Car Select

- Two cards: **Thaiger 7** and **Bengalo**. Tap a card to select it (a blue
  outline marks the selection). Thaiger 7 is the default.
- The selected profile controls warning thresholds and the max-power scale.
- Tap **Settings** (top) to open app settings before a run.
- Tap **Connect to …** to continue to the Connecting screen.

### A.4 Screen 2 — Connecting

This screen pairs the phone with the car and starts the data stream.

1. The app checks permissions and that Bluetooth is on.
2. A dialog lists your **paired Bluetooth devices** plus a final
   **⚙ Demo Mode** entry.
3. Choose your car's module (e.g. an HM-10 / HC-05 / ESP32-named device), **or**
   pick **Demo Mode** to run with a synthetic data stream and no hardware.
4. **Pair New** opens the Android Bluetooth settings if your module isn't paired
   yet. **Cancel** returns to Car Select.

When the module reports **Connected**, the app **automatically advances** to the
Live Dashboard. If the MQTT relay is enabled in Settings, it starts here too, so
the engineer laptop begins receiving data as soon as the run starts.

> Connection drops are handled automatically with exponential backoff
> (1 → 2 → 4 → 8 → 16 s). You will see *Reconnecting…* — no action needed.

### A.5 Screen 3 — Live Dashboard

The dashboard is the driver's main view (landscape, screen stays awake). It shows
the latest value of every telemetry field, refreshed continuously.

**Key elements**

- **Speed** — the large hero number (km/h). If *Speed color warning* is on
  (Settings), the number turns **red** when you drop below the car's minimum
  target speed and stays light otherwise. A small indicator shows
  *✓ on target* / *✗ too slow*.
- **Lap overlay** (single line above the bottom bar):
  `PREV 1:18 · LAP 3 · CURR 0:09` — previous lap time, current lap number, and the
  running current-lap time.
- **Electrical block** — fuel-cell / supercap / motor voltage and current, own
  consumption, cell voltage difference.
- **Thermal & efficiency** — FC temperature, air-pump duty, FC and system
  efficiency, energies, optimal speed, driving hint.
- **Alert banner** — appears when a threshold is crossed (e.g. FC temperature or
  cell voltage difference too high). With *Vibrate* / *Alert sound* on you also get
  haptic / audio feedback. Tap **Dismiss** to hide the banner.

**Ending a run:** **long-press the speed number.** This stops Bluetooth and the
MQTT relay, finalizes the run statistics, and opens the **Post-Run Summary**.

### A.6 Screen 4 — Post-Run Summary & CSV export

After a run you see aggregated results:

- Run duration, distance, average speed, energy used, peak motor power, max FC
  temperature, and alert count.
- A **power graph** for the whole run.

**Buttons**

- **Export CSV** — writes every recorded telemetry frame to a CSV file and opens
  the Android share sheet so you can send it to email, Drive, a messenger, etc.
  The file is named `thaiger_{car}_{YYYYMMDD_HHMM}.csv` and contains 21 columns
  (time, speed, voltages, currents, temperatures, energies, efficiencies,
  distance, motor power). If no data was recorded you get a *“No data to export”*
  message.
- **New Run** — go straight back to Connecting for another run.
- **Back to Car Select** — return to the start.

### A.7 Screen 5 — Settings

Reached from the **Settings** link on Car Select. All values are saved
immediately.

**Display**

| Setting | Default | Meaning |
|---|---|---|
| Keep screen on | On | Hold a wake lock so the screen never sleeps during a run |
| Speed color warning | On | Turn the speed red when below the car's minimum target speed |
| Update rate | 50 ms | How often the dashboard UI redraws (20–500 ms) |

**Alerts**

| Setting | Default | Meaning |
|---|---|---|
| Vibrate | On | Haptic pulse when an alert fires |
| Alert sound | Off | Play a tone when an alert fires |

**Thresholds (per vehicle)** — stored separately for Thaiger 7 and Bengalo:

| Setting | Range | Default (Thaiger 7 / Bengalo) |
|---|---|---|
| FC temperature max | 30–120 °C | 70 °C / 65 °C |
| Cell voltage diff max | 10–500 mV | 50 mV / 50 mV |

Crossing a threshold raises the dashboard alert.

### A.8 Screen 6 — MQTT Settings

A sub-screen of Settings that configures the relay to the engineer laptop. The
relay only runs if it is enabled **before** you start a run.

| Field | Default | Notes |
|---|---|---|
| Enable relay | Off | Master switch — turn this on to publish telemetry |
| Use TLS | On | Encrypted connection (recommended; required by HiveMQ Cloud) |
| Broker host | — | e.g. `abc123.s1.eu.hivemq.cloud` |
| Port | 8883 | TLS MQTT port (8883 typical; 1883 for plain) |
| Username | — | Broker username |
| Password | — | Broker password (shown masked) |
| Publish rate | 5 Hz | Telemetry publish rate, 1–10 Hz |

> The phone publishes over **native MQTT (TLS)**. The engineer dashboard connects
> to the **same broker over MQTT-over-WebSocket** — so the broker must expose a
> WebSocket listener too (HiveMQ Cloud does, on port 8884). See Part B.

---

## 4. Part B — The Engineer Dashboard

`engineer_dashboard.html` is a **single self-contained file**. It needs no build
step and no server — just a modern browser and internet access (for the map
tiles, fonts, and the MQTT/charts libraries from CDN).

### B.1 Opening & connecting

1. Open `engineer_dashboard.html` in Chrome/Edge/Firefox (double-click, or host it
   on any static web server).
2. The **Connection Settings** dialog opens automatically on first use (or click
   the **⚙** button, top-right).
3. Fill in:
   - **Car** — `Thaiger 7` or `Bengalo` (must match the car the phone is
     publishing as).
   - **Broker URL (WebSocket)** — the full WebSocket URL, e.g.
     `wss://abc123.s1.eu.hivemq.cloud:8884/mqtt`. Use `wss://` for TLS (port 8884
     on HiveMQ Cloud) or `ws://host:port` for a plain broker on a local network.
   - **Username** / **Password** — broker credentials.
4. Click **Connect**. The settings are saved in the browser (`localStorage`) and
   the dashboard reconnects automatically next time you open it.

The status dot, top-right, shows the link: **grey** disconnected, **amber**
connecting, **green** connected. Next to it you see the car badge and the time of
the last received frame.

Three tabs across the top switch between the views: **Operations**,
**Live Tracking**, **Analytics**.

### B.2 View 1 — Operations

A single-glance overview of the whole car:

- A **hero speed card** (target-aware: blue when at/above the optimal speed, red
  below it) with average speed, lap count, run time, target lap and distance.
- An **Electrical** grid: fuel-cell / supercap / motor voltage & current, own
  consumption, cell voltage difference — each with a context bar.
- A **Thermal & Efficiency** grid: FC temperature, air-pump duty, FC and system
  efficiency, energies, optimal speed, driving hint.
- A pulsing **LIVE** pill confirms fresh data is arriving.

### B.3 View 2 — Live Tracking & map

A full-screen dark **OpenStreetMap** (Leaflet) following the car.

- **Power-colored trail** — the path behind the car is drawn segment by segment
  and colored by the motor power at that moment: **green (0 W) → amber (100 W) →
  red (200 W+)**. The newest part of the trail is fully opaque and fades with age,
  so you can read both *where* the car went and *how hard it was working* at every
  point. (200 W is the ThaiGer max tracking power; the scale constant `POWER_MAX`
  can be changed in the file.)
- **Power legend** (bottom-left) — the color scale plus a live watt readout.
- **Rotating car marker** points in the direction of travel.
- **GPS status panel** (right) — fix quality, accuracy, latitude, longitude,
  altitude, GPS speed, heading, live motor power, lap count, and a **Filtered**
  counter (how many noisy GPS fixes were rejected — see below).

**GPS jitter cleanup.** Raw GPS can jump around. Each fix is cleaned before it
touches the map by four steps: (1) drop fixes with accuracy worse than 75 m;
(2) reject “teleports” — fixes implying an impossible speed (> ~162 km/h), with a
re-sync guard so it never gets stuck; (3) ignore sub-1.5 m wobble while
stationary; (4) light smoothing of the remaining noise. The result is a clean
trail with no random jumps.

**Per-lap snapshot + reset.** Every time the car's lap counter increments, the
dashboard automatically **renders the just-finished lap's trail to a PNG image and
downloads it** (`thaiger-lapN-<timestamp>.png`), then **clears the trail** so the
new lap is drawn fresh. A green toast confirms each save.

### B.4 View 3 — Analytics

- A **live telemetry table** listing all 22 fields with their current values.
- A **Speed** chart (km/h over time) and an **Efficiency** chart (FC and system
  efficiency over time), each showing the last ~60 samples.

---

## 5. Telemetry protocol & field reference

The car controller sends ASCII frames over BLE. Each field is wrapped in asterisks
with a single-letter key. Example:

```
*A28.3**B25.0**C3**D15:35**N53.4*
```

The parser is stream-tolerant: it handles partial packets, multi-chunk frames and
missing fields, always keeping the latest known value of each field.

| Key | Field | Unit |
|---|---|---|
| A | Speed | km/h |
| B | Average speed | km/h |
| C | Lap count | — |
| D | Total run time | mm:ss |
| E | Target lap time | mm:ss |
| F | Optimal speed | km/h |
| G | Fuel-cell voltage | V |
| H | Supercap voltage | V |
| I | Motor voltage | V |
| J | Fuel-cell current | A |
| K | Supercap current | A |
| L | Motor current | A |
| M | Own consumption | A |
| N | Fuel-cell temperature | °C |
| O | Air-pump duty cycle | % |
| P | Driving hint | — |
| Q | Cell voltage difference | mV |
| R | Fuel-cell energy (cumulative) | Ws |
| S | Motor energy (cumulative) | Ws |
| T | Fuel-cell efficiency | % |
| U | System efficiency | % |

Distance, motor power (V × A), and energy in Wh are derived on the phone.

---

## 6. MQTT topics & payloads

All messages are **QoS 0** (fire-and-forget). Empty/NaN fields are omitted to keep
payloads small (~100–200 bytes).

**`thaiger/{car}/telemetry`** — published at the configured rate (default 5 Hz):

```json
{"ts":1736517201234,"A":28.3,"B":25.0,"C":3,"D":"15:35","G":28.3,"N":53.4,"dist":0.42}
```

**`thaiger/{car}/gps`** — published when a GPS fix is available:

```json
{"ts":1736517201300,"lat":13.756331,"lon":100.501762,"alt":12.0,"spd":53.4,"acc":3.5,"hdg":270.0}
```

| Field | Meaning | Unit |
|---|---|---|
| ts | Unix timestamp | ms |
| lat / lon | Latitude / longitude | ° |
| alt | Altitude (if available) | m |
| spd | GPS speed (if available) | km/h |
| acc | Horizontal accuracy (if available) | m |
| hdg | Heading / bearing (if available) | ° |

---

## 7. Vehicle profiles

| Profile | id | Max motor power | FC temp limit | Min target speed |
|---|---|---|---|---|
| Thaiger 7 | `thaiger7` | 600 W | 70 °C | 25 km/h |
| Bengalo | `bengalo` | 900 W | 65 °C | 25 km/h |

Thresholds can be overridden per car in **Settings**. Note: the *engineer
dashboard* map trail uses a separate **200 W** power scale (`POWER_MAX`), tuned for
the tracking display rather than the car's absolute peak.

---

## 8. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| App won't connect over Bluetooth | Make sure the module is **paired** in Android Bluetooth settings first; check it's powered; grant Bluetooth + Location permissions. Use **Pair New** from the picker. |
| No data, but “Connected” | Wrong device chosen, or the controller isn't sending. Try Demo Mode to confirm the app works, then re-check the module. |
| Engineer dashboard never goes green | Wrong **WebSocket URL** (must be `wss://…:8884/mqtt` for HiveMQ Cloud, not the `8883` native port), wrong credentials, or the broker has no WebSocket listener. |
| Dashboard green but no telemetry | The **Car** in the dashboard doesn't match the phone's car id, or the relay isn't enabled on the phone. Both must use the same `thaiger7`/`bengalo`. |
| Map is blank | No internet for OSM tiles, or no GPS fixes yet. The map only centers once a fix arrives. |
| GPS trail jumps around | The cleanup should prevent this; if your device only has very coarse fixes, raise `GPS_MAX_ACC` in `engineer_dashboard.html`. The **Filtered** counter shows how many fixes are being dropped. |
| GPS updates only every few seconds | Ensure the phone has a clear sky view and that **Location** permission is granted; indoors it falls back to coarse network fixes. |
| No CSV produced | You must complete a run with recorded frames; the *Export CSV* button reports “No data to export” if nothing was captured. |

---

## 9. Tuning & advanced settings

**On the phone (Settings / MQTT Settings):** screen wake, speed color, UI update
rate, vibrate/sound, per-car thresholds, and all broker/relay parameters
(see A.7 / A.8).

**In `engineer_dashboard.html`** (constants near the top of the script block):

| Constant | Default | Purpose |
|---|---|---|
| `POWER_MAX` | 200 | Watt value mapped to full red on the trail/legend |
| `GPS_MAX_ACC` | 75 | Drop GPS fixes worse than this many metres |
| `GPS_MAX_SPEED` | 45 | m/s; faster implied speed ⇒ treated as a teleport |
| `GPS_MIN_MOVE` | 1.5 | m; smaller moves are treated as stationary jitter |
| `GPS_SMOOTH` | 0.35 | EMA smoothing factor (0 = none, 1 = raw) |
| `GPS_MAX_REJECTS` | 4 | Re-sync after this many consecutive rejected fixes |
| `TRAIL_MAX` | 250 | Max trail points kept in memory |

---

## 10. Glossary

- **BLE** — Bluetooth Low Energy, the radio link from the car controller to the
  phone.
- **MQTT** — a lightweight publish/subscribe messaging protocol; the broker relays
  messages from the phone to the engineer dashboard.
- **Broker** — the MQTT server (e.g. HiveMQ Cloud) both sides connect to.
- **TLS** — encryption for the broker connection.
- **WebSocket** — the transport the browser uses to speak MQTT (`ws://` / `wss://`).
- **Fuel cell (FC)** — the hydrogen power source on the car.
- **Supercap** — supercapacitor buffer storing energy for bursts.
- **Telemetry frame** — one packet of measurements from the car.
- **Trail** — the line drawn behind the car on the map, here colored by motor
  power.
