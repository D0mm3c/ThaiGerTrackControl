package com.thaiger.h2racing.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.thaiger.h2racing.App;
import com.thaiger.h2racing.R;
import com.thaiger.h2racing.bt.BluetoothService;
import com.thaiger.h2racing.gps.GpsService;
import com.thaiger.h2racing.model.CarProfile;
import com.thaiger.h2racing.model.RunStats;
import com.thaiger.h2racing.model.TelemetryModel;
import com.thaiger.h2racing.relay.MqttRelayService;
import com.thaiger.h2racing.util.Prefs;

import java.util.Locale;

/**
 * Bengalo race dashboard — glove-friendly, manual lap button.
 *
 * The Bengalo has no physical lap switch, so the driver taps one large button to
 * mark each lap. The run timer starts on the first tap. Shows speed, supercap
 * voltage, total race time and rolling lap times (PREV / LAP / CURR), mirroring
 * the Thaiger 7 lap display.
 *
 * Intentionally kept separate from {@link DashboardActivity} (the Thaiger 7
 * cockpit) — that screen is not modified.
 */
public class BengaloDashboardActivity extends AppCompatActivity {

    private CarProfile       car;
    private BluetoothService service;
    private MqttRelayService relayService;
    private RunStats         runStats;
    private GpsService       gpsService;
    private Prefs            prefs;

    private float   fcTempThresholdC;
    private int     updateRateMs;
    private boolean speedColorEnabled = true;
    private long    lastUiUpdateMs = 0;

    // ─── Views ───
    private TextView tvSpeed, tvSupercap, tvTime, tvCarBadge, tvBtStatus;
    private TextView tvLapPrev, tvLapHeader, tvLapCurr;
    private TextView btnLap;

    // ─── Lap / timer state (driven by the button, not telemetry) ───
    private boolean raceStarted = false;
    private long    raceStartMs = 0;
    private long    lapStartMs  = 0;
    private int     lapCount    = 0;
    private long    prevLapMs   = -1;

    private final Handler  tickHandler  = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = this::tick;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_bengalo);

        App app = (App) getApplication();
        car     = app.getCarProfile();
        service = app.getBluetoothService();
        if (service == null) { finish(); return; }

        prefs             = new Prefs(this);
        fcTempThresholdC  = prefs.getFcTempMaxC(car);
        updateRateMs      = prefs.getUpdateRateMs();
        speedColorEnabled = prefs.isSpeedColorEnabled();
        runStats          = app.getRunStats();
        if (runStats == null) { runStats = new RunStats(); app.setRunStats(runStats); }
        relayService      = app.getRelayService();

        bindViews();
        tvCarBadge.setText(car.displayName.toUpperCase(Locale.ROOT));
        renderLaps(System.currentTimeMillis());   // initial "—" state

        btnLap.setOnClickListener(v -> onLapPress());

        // Long-press the info column ends the run (deliberate — glove-safe).
        View info = findViewById(R.id.ll_bengalo_info);
        if (info != null) info.setOnLongClickListener(v -> { endRun(); return true; });

        if (prefs.isWakeLock()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void bindViews() {
        tvSpeed     = findViewById(R.id.tv_b_speed);
        tvSupercap  = findViewById(R.id.tv_b_supercap);
        tvTime      = findViewById(R.id.tv_b_time);
        tvCarBadge  = findViewById(R.id.tv_b_car_badge);
        tvBtStatus  = findViewById(R.id.tv_b_bt_status);
        tvLapPrev   = findViewById(R.id.tv_b_lap_prev);
        tvLapHeader = findViewById(R.id.tv_b_lap_header);
        tvLapCurr   = findViewById(R.id.tv_b_lap_curr);
        btnLap      = findViewById(R.id.btn_b_lap);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (service == null) return;
        service.setListener(new BluetoothService.Listener() {
            @Override public void onState(BluetoothService.State s, String detail) { renderBtState(s); }
            @Override public void onTelemetry(TelemetryModel m) { applyTelemetry(m); }
        });
        // Re-apply the derived lap count after a pause/resume (fresh listener).
        if (raceStarted) service.setLapOverride(lapCount);
        tickHandler.post(tickRunnable);
        startGps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (service != null) service.setListener(null);
        tickHandler.removeCallbacks(tickRunnable);
        if (gpsService != null) { gpsService.stop(); gpsService = null; }
    }

    /** GPS for the engineer-dashboard map / relay (same as the Thaiger cockpit). */
    private void startGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        if (gpsService != null) gpsService.stop();
        gpsService = new GpsService(this);
        boolean started = gpsService.start(location -> {
            if (relayService != null) relayService.onGps(location);
        });
        if (!started) gpsService = null;
    }

    private void renderBtState(BluetoothService.State state) {
        String s; int c;
        switch (state) {
            case CONNECTED:    s = "BT ●"; c = 0xFF00D97E; break;
            case CONNECTING:
            case RECONNECTING: s = "BT ○"; c = 0xFFFFAA00; break;
            case FAILED:
            case STOPPED:      s = "BT ✕"; c = 0xFFFF3B3B; break;
            default:           s = "BT —"; c = 0xFF7A8A99; break;
        }
        tvBtStatus.setText(s);
        tvBtStatus.setTextColor(c);
    }

    private void applyTelemetry(TelemetryModel m) {
        long now = System.currentTimeMillis();
        // Feed RunStats on every frame (post-run summary + CSV export).
        if (runStats != null) { runStats.update(m, fcTempThresholdC); runStats.addFrame(m); }

        if (now - lastUiUpdateMs < updateRateMs) return;
        lastUiUpdateMs = now;

        if (!Float.isNaN(m.speedKmh)) {
            tvSpeed.setText(String.format(Locale.US, "%.0f", m.speedKmh));
            boolean onTarget = m.speedKmh >= car.minSpeedKmh;
            tvSpeed.setTextColor(speedColorEnabled && !onTarget ? 0xFFFF3B3B : 0xFFE8EDF2);
        }
        if (!Float.isNaN(m.supercapVoltageV)) {
            tvSupercap.setText(String.format(Locale.US, "%.1f", m.supercapVoltageV));
        }
    }

    /** Big lap button: first tap starts the race, each later tap closes a lap. */
    private void onLapPress() {
        long now = System.currentTimeMillis();
        if (!raceStarted) {
            raceStarted = true;
            raceStartMs = now;
            lapStartMs  = now;
            lapCount    = 1;
            prevLapMs   = -1;
        } else {
            prevLapMs  = now - lapStartMs;
            lapStartMs = now;
            lapCount++;
        }
        // Feed the derived lap count into the relay so the engineer dashboard's
        // per-lap map snapshot / trail reset work for the Bengalo too.
        if (service != null) service.setLapOverride(lapCount);
        btnLap.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        renderLaps(now);
    }

    /** 1-second ticker — keeps TIME and CURR moving between telemetry frames. */
    private void tick() {
        if (raceStarted) renderLaps(System.currentTimeMillis());
        tickHandler.postDelayed(tickRunnable, 1000);
    }

    private void renderLaps(long now) {
        if (!raceStarted) {
            tvTime.setText("0:00");
            btnLap.setText("TAP TO\nSTART");
            tvLapHeader.setText("LAP —");
            tvLapCurr.setText("CURR  0:00");
            tvLapCurr.setTextColor(0xFFE8EDF2);
            tvLapPrev.setText("PREV  —:—");
            return;
        }
        int raceSec = (int) ((now - raceStartMs) / 1000);
        tvTime.setText(formatMinSec(raceSec));
        btnLap.setText("LAP\n" + lapCount);
        tvLapHeader.setText("LAP " + lapCount);

        int curr = (int) ((now - lapStartMs) / 1000);
        tvLapCurr.setText("CURR  " + formatMinSec(curr));

        if (prevLapMs >= 0) {
            int prevSec = (int) (prevLapMs / 1000);
            tvLapPrev.setText("PREV  " + formatMinSec(prevSec));
            int color;
            if (curr == 0)           color = 0xFFE8EDF2;   // fresh lap
            else if (curr < prevSec) color = 0xFF00D97E;   // ahead
            else if (curr > prevSec) color = 0xFFFF3B3B;   // behind
            else                     color = 0xFFE8EDF2;   // level
            tvLapCurr.setTextColor(color);
        } else {
            tvLapPrev.setText("PREV  —:—");
        }
    }

    private void endRun() {
        startActivity(new Intent(this, PostRunActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        // No accidental exit mid-run — end via long-press on the info area.
    }

    private static String formatMinSec(int totalSec) {
        if (totalSec < 0) return "—:—";
        return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60);
    }
}
