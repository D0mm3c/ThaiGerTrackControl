package com.thaiger.h2racing.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.thaiger.h2racing.App;
import com.thaiger.h2racing.R;
import com.thaiger.h2racing.bt.BluetoothService;
import com.thaiger.h2racing.model.CarProfile;
import com.thaiger.h2racing.model.RunStats;
import com.thaiger.h2racing.model.TelemetryModel;
import com.thaiger.h2racing.util.AlertFx;
import com.thaiger.h2racing.util.Prefs;

import java.util.Locale;

/**
 * Screen 3 — Dashboard. DER Race-Screen.
 *
 * Verantwortlich für:
 *   - Live-Anzeige aller Telemetriewerte
 *   - Threshold-Checks (FC-Temp, Min-Speed)
 *   - Alert-Banner triggern bei Übertretung
 *   - "Sticky Red" — Werte bleiben rot markiert, auch nach Quittung des Banners
 *   - BT-Status-Indikator
 *   - Wake-Lock (Screen darf nicht dimmen)
 *
 * Threading: UI-Updates kommen vom BluetoothService bereits auf dem Main-Thread.
 * Hier wird nichts geblockt, nur findViewById() + setText().
 */
public class DashboardActivity extends AppCompatActivity {

    private CarProfile car;
    private BluetoothService service;
    private PowerManager.WakeLock wakeLock;
    private Prefs    prefs;
    private AlertFx  alertFx;
    private RunStats runStats;
    /** FC-Temp-Threshold aus Prefs (oder CarProfile-Fallback). Wird in onCreate gelesen. */
    private float fcTempThresholdC;
    /** Cell-Voltage-Diff-Threshold aus Prefs (mV). */
    private float cellDiffThresholdMv;
    /** Throttle: minimum delta zwischen UI-Refreshes. */
    private int   updateRateMs;
    private long  lastUiUpdateMs = 0;

    // ─── Hero ───
    private TextView tvSpeed;
    private TextView tvEfficiencyBadge;

    // ─── Linke Zone ───
    private TextView tvFcState;       // wir mappen auf Systemwirkungsgrad
    private ProgressBar pbFcState;
    private TextView tvPower;
    private TextView tvCurrent;

    // ─── Rechte Zone ───
    private TextView tvFcTemp;
    private TextView tvFcAlertTag;
    private CardView cardFcTemp;
    private TextView tvVoltageDifference;    // nicht im Protokoll → "—"
    private TextView tvIdealTime;     // nicht im Protokoll → "—"

    // ─── Top-Bar / Bottom-Bar ───
    private TextView tvBtStatus;
    private TextView tvCarBadge;
    private TextView tvUpdateRate;
    private TextView tvRunTime;
    private TextView tvDistance;
    private TextView tvEnergy;
    private TextView tvLiveIndicator;

    // ─── Alert-Banner ───
    private CardView cardAlertBanner;
    private TextView tvAlertTitle;
    private TextView tvAlertSub;

    // ─── State ───
    /** "Rot bleibt rot": einmal überschritten, bleibt FC-Temp rot bis Activity-Restart. */
    private boolean fcTempStickyRed = false;
    private boolean alertDismissed  = false;
    private long    lastPacketAtMs  = 0;

    // ─── Lap tracking ───
    /** Zuletzt empfangener C-Wert. -1 = noch keine Runde gesehen. */
    private int currentLapNumber       = -1;
    /** D-Wert beim letzten Rundenübergang. Aktuelle Lap-Zeit = aktuelles D minus diesem. */
    private int currentLapStartTotalSec = -1;
    /** Dauer der zuletzt abgeschlossenen Runde [s]. */
    private int previousLapDurationSec = -1;
    /** Lap-Overlay-Views, programmatisch hinzugefügt. */
    private TextView tvLapHeader;
    private TextView tvLapCurr;
    private TextView tvLapPrev;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        car = ((App) getApplication()).getCarProfile();
        service = ((App) getApplication()).getBluetoothService();
        if (service == null) {
            // Defensive: falls jemand das Dashboard direkt öffnet
            finish();
            return;
        }

        bindViews();
        applyCarBadge();
        attachListeners();
        addLapOverlay();

        // Prefs einlesen — werden zur Laufzeit nicht weiter gepollt (Settings nicht
        // während eines Runs erreichbar)
        prefs            = new Prefs(this);
        alertFx          = new AlertFx(this);
        fcTempThresholdC    = prefs.getFcTempMaxC(car);
        cellDiffThresholdMv = prefs.getCellDiffMaxMv(car);
        updateRateMs        = prefs.getUpdateRateMs();
        runStats         = ((App) getApplication()).getRunStats();
        if (runStats == null) {
            // Defensive: falls jemand direkt zum Dashboard navigiert hat
            runStats = new RunStats();
            ((App) getApplication()).setRunStats(runStats);
        }

        if (prefs.isWakeLock()) {
            acquireWakeLock();
        }
        // Der Listener wird in onResume gesetzt, damit er bei Activity-Pause sauber stoppt.
    }

    private void bindViews() {
        tvSpeed           = findViewById(R.id.tv_speed);
        tvEfficiencyBadge = findViewById(R.id.tv_efficiency_badge);
        tvFcState         = findViewById(R.id.tv_fc_state);
        pbFcState         = findViewById(R.id.pb_fc_state);
        tvPower           = findViewById(R.id.tv_power);
        tvCurrent         = findViewById(R.id.tv_current);
        tvFcTemp          = findViewById(R.id.tv_fc_temp);
        tvFcAlertTag      = findViewById(R.id.tv_fc_alert_tag);
        cardFcTemp        = findViewById(R.id.card_fc_temp);
        tvVoltageDifference = findViewById(R.id.tv_voltage_difference);
        tvIdealTime = findViewById(R.id.tv_ideal_time);
        tvBtStatus        = findViewById(R.id.tv_bt_status);
        tvCarBadge        = findViewById(R.id.tv_car_badge);
        tvUpdateRate      = findViewById(R.id.tv_update_rate);
        tvRunTime         = findViewById(R.id.tv_run_time);
        tvDistance        = findViewById(R.id.tv_distance);
        tvEnergy          = findViewById(R.id.tv_energy);
        tvLiveIndicator   = findViewById(R.id.tv_live_indicator);
        cardAlertBanner   = findViewById(R.id.card_alert_banner);
        tvAlertTitle      = findViewById(R.id.tv_alert_title);
        tvAlertSub        = findViewById(R.id.tv_alert_sub);
    }

    private void applyCarBadge() {
        tvCarBadge.setText(car.displayName.toUpperCase(Locale.ROOT));
        // tv_h2_pressure-Slot zeigt jetzt CELL DIFF [mV], tv_motor_temp-Slot zeigt
        // TARGET LAP [mm:ss]. Beide werden von applyTelemetry() befüllt.
        // Bis dahin Layout-Defaults ("4.2" / "58") überschreiben, damit's nicht verwirrt.
        tvVoltageDifference.setText("—");
        tvIdealTime.setText("—:—");
    }

    private void attachListeners() {
        View btnDismiss = findViewById(R.id.btn_dismiss_alert);
        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(v -> {
                cardAlertBanner.setVisibility(View.GONE);
                alertDismissed = true;
            });
        }
        // Long-Press auf den Speed beendet den Run und springt zum Post-Run
        tvSpeed.setOnLongClickListener(v -> {
            startActivity(new Intent(this, PostRunActivity.class));
            finish();
            return true;
        });
    }

    /**
     * Lap-Overlay als einzelne Zeile direkt über der Bottom-Bar:
     *
     *   PREV 1:18  ·  LAP 3  ·  CURR 0:09
     *
     * Drei TextViews in einer horizontalen Linie, alle gleiche Schriftgröße —
     * Hervorhebung läuft über Farbe (CURR weiß/grün/rot, PREV grau) und Weight.
     * Keine Verschachtelung, keine Wrap-Probleme.
     */
    private void addLapOverlay() {
        ViewGroup root = findViewById(android.R.id.content);
        if (root == null) return;
        int dp = (int) getResources().getDisplayMetrics().density;
        float textSize = 16f;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);

        tvLapPrev = mkLapText(0xFF7A8A99, textSize, false);
        tvLapPrev.setText("PREV  —:—");
        container.addView(tvLapPrev);

        container.addView(makeSeparatorDot(dp));

        tvLapHeader = mkLapText(0xFFE8EDF2, textSize, false);
        tvLapHeader.setText("LAP —");
        container.addView(tvLapHeader);

        container.addView(makeSeparatorDot(dp));

        tvLapCurr = mkLapText(0xFFE8EDF2, textSize, true);
        tvLapCurr.setText("CURR  —:—");
        container.addView(tvLapCurr);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = 64 * dp;   // klar über der Bottom-Bar, unter dem η-Badge
        root.addView(container, lp);
    }

    private TextView mkLapText(int color, float sizeSp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        tv.setSingleLine(true);
        tv.setTypeface(android.graphics.Typeface.create(
                bold ? "sans-serif-medium" : "sans-serif",
                android.graphics.Typeface.NORMAL));
        return tv;
    }

    private View makeSeparatorDot(int dp) {
        TextView dot = new TextView(this);
        dot.setText("·");
        dot.setTextColor(0xFF3D4F60);
        dot.setTextSize(16f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(14 * dp, 0, 14 * dp, 0);
        dot.setLayoutParams(lp);
        return dot;
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                    "Thaiger:Dashboard");
            wakeLock.acquire(60 * 60 * 1000L);  // 1h max — auto-released bei stop()
        } catch (Throwable ignored) {}
    }

    // ─────────────────────── BT-Events ───────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        if (service == null) return;
        service.setListener(new BluetoothService.Listener() {
            @Override public void onState(BluetoothService.State s, String detail) { applyState(s, detail); }
            @Override public void onTelemetry(TelemetryModel m) { applyTelemetry(m); }
        });
    }

    private void applyState(BluetoothService.State state, String detail) {
        switch (state) {
            case CONNECTED:    tvBtStatus.setText("BT ●");  tvBtStatus.setTextColor(Color.parseColor("#00D97E")); break;
            case CONNECTING:
            case RECONNECTING: tvBtStatus.setText("BT ○ " + detail); tvBtStatus.setTextColor(Color.parseColor("#FFAA00")); break;
            case FAILED:
            case STOPPED:      tvBtStatus.setText("BT ✕");  tvBtStatus.setTextColor(Color.parseColor("#FF3B3B")); break;
            case IDLE:         tvBtStatus.setText("BT —");  tvBtStatus.setTextColor(Color.parseColor("#7A8A99")); break;
        }
    }

    private void applyTelemetry(TelemetryModel m) {
        long now = System.currentTimeMillis();
        // Stats akkumulieren auf JEDES Frame, auch wenn UI gedrosselt → kein Datenverlust
        if (runStats != null) runStats.update(m, fcTempThresholdC);

        // Throttle: zu schnelle Frames droppen, um UI-Last und Stromverbrauch zu senken.
        if (now - lastUiUpdateMs < updateRateMs) return;
        long sinceLast = lastUiUpdateMs == 0 ? 0 : (now - lastUiUpdateMs);
        lastUiUpdateMs  = now;
        lastPacketAtMs  = now;

        // ─── Hero: Speed ───
        if (!Float.isNaN(m.speedKmh)) {
            tvSpeed.setText(String.format(Locale.US, "%.0f", m.speedKmh));
            // Min-Speed-Warnung
            if (m.speedKmh < car.minSpeedKmh) {
                tvSpeed.setTextColor(Color.parseColor("#FF3B3B"));
            } else {
                tvSpeed.setTextColor(Color.parseColor("#E8EDF2"));
            }
        }

        // ─── Efficiency-Badge ───
        if (!Float.isNaN(m.sysEfficiencyPct)) {
            tvEfficiencyBadge.setText(String.format(Locale.US, "η %.0f%%", m.sysEfficiencyPct));
        }

        // ─── Linke Zone ───
        if (!Float.isNaN(m.sysEfficiencyPct)) {
            tvFcState.setText(String.format(Locale.US, "%.0f%%", m.sysEfficiencyPct));
            pbFcState.setProgress((int) Math.max(0, Math.min(100, m.sysEfficiencyPct)));
        }
        float power = m.motorPowerW();
        if (!Float.isNaN(power)) {
            tvPower.setText(String.format(Locale.US, "%.0f", power));
        }
        if (!Float.isNaN(m.motorCurrentA)) {
            tvCurrent.setText(String.format(Locale.US, "%.1f", m.motorCurrentA));
        }

        // ─── Rechte Zone: FC-Temp (Primär-Health) ───
        if (!Float.isNaN(m.fcTempC)) {
            tvFcTemp.setText(String.format(Locale.US, "%.0f°C", m.fcTempC));
            boolean isOver = m.fcTempC > fcTempThresholdC;
            if (isOver) fcTempStickyRed = true;

            if (fcTempStickyRed) {
                tvFcTemp.setTextColor(Color.parseColor("#FF3B3B"));
                tvFcAlertTag.setVisibility(View.VISIBLE);
                tvFcAlertTag.setText(isOver ? "OVER" : "WAS HIGH");
                tvFcAlertTag.setTextColor(Color.parseColor(isOver ? "#FF3B3B" : "#FFAA00"));
            } else {
                tvFcTemp.setTextColor(Color.parseColor("#E8EDF2"));
                tvFcAlertTag.setVisibility(View.GONE);
            }

            // Alert-Banner triggern (sticky bis dismiss)
            if (isOver && !alertDismissed) {
                showAlertBanner(
                        String.format(Locale.US, "FC Temperature High — %.0f°C", m.fcTempC),
                        String.format(Locale.US, "Threshold: %.0f°C · Reduce power immediately", fcTempThresholdC));
                if (alertFx != null) alertFx.fire();   // Vibration + Ton (gated by prefs)
            }
            if (!isOver && alertDismissed && m.fcTempC < fcTempThresholdC - 5) {
                // Wert ist nachhaltig unter Threshold gefallen → wieder armed
                alertDismissed = false;
            }
        }

        // ─── Top-Bar / Bottom-Bar ───
        if (m.totalTimeSec >= 0) {
            int mn = m.totalTimeSec / 60, sc = m.totalTimeSec % 60;
            tvRunTime.setText(String.format(Locale.US, "%02d:%02d", mn, sc));
        }

        // ─── Lap-Detection: C ändert sich → Runde abgeschlossen ───
        if (m.laps >= 0 && m.totalTimeSec >= 0) {
            if (currentLapNumber < 0) {
                // Erstes Frame mit Runden-Info → Baseline setzen, keine Runde abgeschlossen.
                currentLapNumber        = m.laps;
                currentLapStartTotalSec = m.totalTimeSec;
            } else if (m.laps != currentLapNumber) {
                // Runden-Übergang (egal ob +1 oder unerwarteter Sprung): aktuelle als
                // 'previous' merken und neuen Startpunkt setzen.
                previousLapDurationSec  = Math.max(0, m.totalTimeSec - currentLapStartTotalSec);
                currentLapStartTotalSec = m.totalTimeSec;
                currentLapNumber        = m.laps;
            }
            tvLapHeader.setText("LAP " + currentLapNumber);

            int currLap = Math.max(0, m.totalTimeSec - currentLapStartTotalSec);
            tvLapCurr.setText("CURR  " + formatMinSec(currLap));

            // Live-Vergleich Farbe der CURR-Zeit: schneller als PREV → grün, langsamer → rot
            if (previousLapDurationSec > 0) {
                tvLapPrev.setText("PREV  " + formatMinSec(previousLapDurationSec));
                int color;
                if (currLap == 0) {
                    color = 0xFFE8EDF2;                // weiß: Runde frisch gestartet
                } else if (currLap < previousLapDurationSec) {
                    color = 0xFF00D97E;                // grün: liegen vor
                } else if (currLap > previousLapDurationSec) {
                    color = 0xFFFF3B3B;                // rot: liegen hinter
                } else {
                    color = 0xFFE8EDF2;                // weiß: gleichauf
                }
                tvLapCurr.setTextColor(color);
            } else {
                tvLapPrev.setText("PREV  —:—");
            }
        }

        // ─── Cell Voltage Difference (im tv_h2_pressure-Slot, Label "CELL DIFF") ───
        // Q-Feld vom ESP, in mV. Threshold pro Auto aus Prefs → rot.
        if (!Float.isNaN(m.cellVoltDiffMv)) {
            tvVoltageDifference.setText(String.format(Locale.US, "%.0f", m.cellVoltDiffMv));
            tvVoltageDifference.setTextColor(m.cellVoltDiffMv > cellDiffThresholdMv
                    ? 0xFFFF3B3B    // rot
                    : 0xFFE8EDF2);  // weiß
        }

        // ─── Optimale Rundenzeit (im tv_motor_temp-Slot, Label "TARGET LAP") ───
        // Was muss die NÄCHSTE Runde lang sein, um die kumulierte Zeitschuld
        // (oder den Vorsprung) bis zum Ende der nächsten Runde auszugleichen?
        //
        //     Soll-Gesamtzeit nach nächster Runde:  (C + 1) × E
        //     Ist-Gesamtzeit jetzt:                 D
        //     Optimal nächste Runde:                (C + 1) × E − D
        //
        // Result < E  → muss schneller als Ziel sein  (Rückstand)  → orange
        // Result ≥ E  → im Soll oder Vorsprung                     → weiß
        // Result ≤ 0  → in einer Runde nicht aufholbar             → rot, "—:—"
        if (m.laps >= 0 && m.totalTimeSec >= 0 && m.targetLapTimeSec > 0) {
            int optNextLap = (m.laps + 1) * m.targetLapTimeSec - m.totalTimeSec;
            if (optNextLap <= 0) {
                tvIdealTime.setText("—:—");
                tvIdealTime.setTextColor(0xFFFF3B3B);
            } else {
                tvIdealTime.setText(formatMinSec(optNextLap));
                tvIdealTime.setTextColor(optNextLap < m.targetLapTimeSec
                        ? 0xFFFFAA00     // orange: musst schneller fahren
                        : 0xFFE8EDF2);   // weiß: im Soll oder Vorsprung
            }
        }
        if (!Float.isNaN(m.distanceKm)) {
            tvDistance.setText(String.format(Locale.US, "%.2f km", m.distanceKm));
        }
        if (!Float.isNaN(m.motorEnergyWh())) {
            tvEnergy.setText(String.format(Locale.US, "%.0f Wh", m.motorEnergyWh()));
        }

        // ─── Live-Indikator: Latenz ───
        if (sinceLast > 0) {
            tvLiveIndicator.setText(String.format(Locale.US, "%dms · live", sinceLast));
            tvUpdateRate.setText(String.format(Locale.US, "%dms", sinceLast));
        }
    }

    private void showAlertBanner(String title, String sub) {
        tvAlertTitle.setText(title);
        tvAlertSub.setText(sub);
        cardAlertBanner.setVisibility(View.VISIBLE);
    }

    // ─────────────────────── Lifecycle ───────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Throwable ignored) {}
        }
        if (alertFx != null) alertFx.release();
    }

    @Override
    public void onBackPressed() {
        // Während eines Runs nicht versehentlich verlassen → erst über Long-Press auf Speed
    }

    private static String formatMinSec(int totalSec) {
        if (totalSec < 0) return "—:—";
        return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60);
    }
}