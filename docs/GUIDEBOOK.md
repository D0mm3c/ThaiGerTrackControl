# ThaiGer Track Control — Guidebook

Complete usage guide for the ThaiGer H₂ telemetry system (Android cockpit app +
engineer dashboard). Choose your language:

| Language | File |
|---|---|
| 🇬🇧 **English** | [GUIDEBOOK.en.md](GUIDEBOOK.en.md) |
| 🇩🇪 **Deutsch** | [GUIDEBOOK.de.md](GUIDEBOOK.de.md) |
| 🇨🇳 **中文 (简体)** | [GUIDEBOOK.zh.md](GUIDEBOOK.zh.md) |

**🖨️ Printable edition:** a single **six-language** PDF — English, Deutsch,
Français, Español, Русский, 中文 — in one booklet, built from the LaTeX source in
[`latex/guidebook.tex`](latex/guidebook.tex) (pre-built
[`latex/guidebook.pdf`](latex/guidebook.pdf) included). See
[`latex/README.md`](latex/README.md) for the XeLaTeX build steps.

Each version is self-contained and covers:

- System overview (car → phone → MQTT broker → engineer laptop)
- The Android app — every screen, from car select and Bluetooth pairing through
  the live dashboard, ending a run, the post-run summary, CSV export, and all
  settings (display, alerts, thresholds, MQTT relay)
- The engineer dashboard — connecting, the Operations / Live Tracking / Analytics
  views, the power-colored GPS trail, GPS jitter cleanup, and per-lap snapshots
- The telemetry protocol and full field reference (A–U)
- MQTT topics and JSON payloads
- Vehicle profiles, troubleshooting, tuning constants, and a glossary
