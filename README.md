# ThaiGerTrackControl

Android cockpit display for the ThaiGer H2 hydrogen fuel cell racing vehicle. The app receives live telemetry from the car over Bluetooth Low Energy, displays it on a race dashboard, and relays it via MQTT to an engineer's laptop.

📖 **User guidebook:** [English](docs/GUIDEBOOK.en.md) · [Deutsch](docs/GUIDEBOOK.de.md) · [中文](docs/GUIDEBOOK.zh.md) — full step-by-step usage of the app and the engineer dashboard.

---

## Features

- Live telemetry dashboard (speed, power, FC temperature, efficiency, lap times)
- Threshold-based alerts with haptic and audio feedback
- Lap timing with live comparison against the previous lap
- GPS position and speed published alongside telemetry
- MQTT relay to engineer station over TLS
- Post-run summary with power graph
- Two vehicle profiles: **THAIGER 7** and **BENGALO**
- Demo mode — synthetic data stream without hardware

---

## Screens

| Screen | Purpose |
|---|---|
| Car Select | Choose vehicle profile |
| Connecting | Pair Bluetooth device or start Demo Mode |
| Dashboard | Live race display (landscape, screen always on) |
| Post-Run | Run summary — duration, energy, peak power, alert count |
| Settings | Display options, per-vehicle thresholds |
| MQTT Settings | Broker host, port, TLS, credentials, publish rate |

---

## Architecture

```
BLE Module (car)
      │  Bluetooth Low Energy (GATT)
      ▼
BluetoothService          — connection, reconnect, stream accumulation
      │
TelemetryParser           — stateless field extractor (*A28.3**B25.0*…)
      │
      ├──► DashboardActivity   — UI thread updates, alert logic, lap tracking
      │
      └──► MqttRelayService    — BlockingQueue → RelayThread → Paho publish
                │
                ├── thaiger/{car}/telemetry   (JSON, configurable rate 1–10 Hz)
                └── thaiger/{car}/gps         (JSON, 1 Hz)

GpsService (LocationManager)
      │
      └──► MqttRelayService.onGps()
```

**Threading model**

| Thread | Responsibility |
|---|---|
| Main / UI | Activity lifecycle, all view updates |
| BLE Binder | GATT callbacks — parsed immediately, output marshalled to Main |
| MQTT-Relay (daemon) | MQTT connection, publish loop |

State is held in the `App` singleton (`BluetoothService`, `MqttRelayService`, `CarProfile`, `RunStats`) so it survives Activity transitions without Bound Service boilerplate.

---

## Telemetry Protocol

The car controller sends ASCII frames over BLE. Each field is enclosed in asterisks with a single-letter key:

```
*A28.3**B25.0**C3**D15:35**N53.4*…
```

The parser is stateless and stream-tolerant — it handles partial packets, multi-chunk BLE frames, and missing fields gracefully. The latest known value for each field is kept in a rolling `TelemetryModel`.

### Field reference

| Key | Field | Unit |
|---|---|---|
| A | Speed | km/h |
| B | Average speed | km/h |
| C | Lap count | — |
| D | Total run time | mm:ss |
| E | Target lap time | mm:ss |
| F | Optimal speed | km/h |
| G | FC voltage | V |
| H | Supercap voltage | V |
| I | Motor voltage | V |
| J | FC current | A |
| K | Supercap current | A |
| L | Motor current | A |
| M | Own consumption | A |
| N | FC temperature | °C |
| O | Air pump duty cycle | % |
| P | Driving hint | — |
| Q | Cell voltage difference | mV |
| R | FC energy cumulative | Ws |
| S | Motor energy cumulative | Ws |
| T | FC efficiency | % |
| U | System efficiency | % |

Distance, motor power, and motor energy in Wh are derived on the phone.

---

## MQTT Topics

All messages are QoS 0 (fire-and-forget). TLS is enabled by default.

### `thaiger/{car_id}/telemetry`

Published at the configured rate (default 1 Hz). Fields match the protocol above plus phone-derived values.

```json
{"ts":1736517201234,"A":28.3,"B":25.0,"C":3,"D":"15:35","G":28.3,"N":53.4,"dist":0.42}
```

NaN and unset fields are omitted to keep payloads small (typically 100–200 bytes).

### `thaiger/{car_id}/gps`

Published at 1 Hz when a GPS fix is available.

```json
{"ts":1736517201300,"lat":13.756331,"lon":100.501762,"alt":12.0,"spd":53.4,"acc":3.5,"hdg":270.0}
```

| Field | Description | Unit |
|---|---|---|
| ts | Unix timestamp | ms |
| lat | Latitude | ° |
| lon | Longitude | ° |
| alt | Altitude (if available) | m |
| spd | GPS speed (if available) | km/h |
| acc | Horizontal accuracy (if available) | m |
| hdg | Bearing (if available) | ° |

---

## Vehicle Profiles

| Profile | Max motor power | Thresholds |
|---|---|---|
| THAIGER_7 | 600 W | FC temp, cell diff — configurable in Settings |
| BENGALO | 900 W | FC temp, cell diff — configurable in Settings |

`CarProfile` provides fallback defaults when Settings have not been customised.

---

## Reconnection

Both BLE and MQTT use exponential backoff on disconnect:

```
1 s → 2 s → 4 s → 8 s → 16 s (max)
```

---

## Permissions

| Permission | Reason |
|---|---|
| `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` | BLE pairing and scanning (Android 12+) |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | BLE (Android ≤ 11) |
| `ACCESS_FINE_LOCATION` | BLE scanning (Android ≤ 11) + GPS |
| `ACCESS_COARSE_LOCATION` | GPS fallback |
| `INTERNET` | MQTT relay |
| `WAKE_LOCK` | Keep screen on during run |
| `VIBRATE` | Alert haptic feedback |

Location permission is requested on the Connecting screen before any GPS use.

---

## Build

- **Min SDK**: 28 (Android 9)
- **Target SDK**: 34
- **Language**: Java 11
- **Build system**: Gradle

```bash
./gradlew assembleDebug
```

Key dependencies (see `app/build.gradle`):

| Library | Purpose |
|---|---|
| AndroidX AppCompat / CardView / ConstraintLayout | UI |
| Eclipse Paho MQTT Client 1.2.5 | MQTT relay |

No GPS or mapping library is required — `android.location.LocationManager` is used directly.
