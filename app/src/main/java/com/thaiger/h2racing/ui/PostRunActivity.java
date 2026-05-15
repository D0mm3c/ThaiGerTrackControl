package com.thaiger.h2racing.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.thaiger.h2racing.App;
import com.thaiger.h2racing.R;
import com.thaiger.h2racing.bt.BluetoothService;
import com.thaiger.h2racing.model.CarProfile;
import com.thaiger.h2racing.model.RunStats;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Screen 5 — Post-Run Summary.
 *
 * Stoppt den BluetoothService und zeigt die aufgezeichneten Werte aus
 * {@link RunStats}. Power-Graph wird programmatisch ins
 * {@code view_power_graph}-Slot gehängt — kein XML-Eingriff.
 *
 * CSV-Export folgt — derzeit nur Toast als Hinweis (braucht alle Frames,
 * nicht nur Aggregate; und MediaStore-Pfad für scoped storage).
 */
public class PostRunActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_run);

        // Run beenden: BluetoothService stoppen, RunStats finalisieren
        App app = (App) getApplication();
        BluetoothService svc = app.getBluetoothService();
        if (svc != null) svc.stop();
        app.setBluetoothService(null);

        RunStats stats = app.getRunStats();
        if (stats != null) stats.end();
        CarProfile car = app.getCarProfile();

        renderHeader(car, stats);
        renderStatRows(stats);
        injectPowerGraph(stats);
        wireActions();
    }

    private void renderHeader(CarProfile car, RunStats stats) {
        TextView tvMeta = findViewById(R.id.tv_run_meta);
        if (tvMeta == null) return;
        long startedAt = stats != null ? stats.startedAtMs : System.currentTimeMillis();
        String when = new SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.US).format(new Date(startedAt));
        tvMeta.setText(car.displayName + " · " + when);
    }

    private void renderStatRows(RunStats stats) {
        if (stats == null) {
            setStat(R.id.row_duration,    "Run duration",  "—");
            setStat(R.id.row_distance,    "Distance",      "—");
            setStat(R.id.row_avg_speed,   "Average speed", "—");
            setStat(R.id.row_energy,      "Energy used",   "—");
            setStat(R.id.row_peak_power,  "Peak power",    "—");
            setStat(R.id.row_max_fc_temp, "Max FC temp",   "—");
            setStat(R.id.row_alerts,      "Alerts",        "—");
            return;
        }

        int dur = stats.runDurationSec();
        setStat(R.id.row_duration,    "Run duration",
                String.format(Locale.US, "%02d:%02d", dur / 60, dur % 60));

        setStat(R.id.row_distance,    "Distance",
                Float.isNaN(stats.lastDistanceKm) ? "—"
                        : String.format(Locale.US, "%.2f km", stats.lastDistanceKm));

        setStat(R.id.row_avg_speed,   "Average speed",
                stats.avgSpeedKmh() <= 0 ? "—"
                        : String.format(Locale.US, "%.1f km/h", stats.avgSpeedKmh()));

        setStat(R.id.row_energy,      "Energy used",
                Float.isNaN(stats.lastEnergyWh) ? "—"
                        : String.format(Locale.US, "%.1f Wh", stats.lastEnergyWh));

        setStat(R.id.row_peak_power,  "Peak power",
                stats.maxMotorPowerW <= 0 ? "—"
                        : String.format(Locale.US, "%.0f W", stats.maxMotorPowerW));

        setStat(R.id.row_max_fc_temp, "Max FC temp",
                stats.maxFcTempC <= 0 ? "—"
                        : String.format(Locale.US, "%.1f °C", stats.maxFcTempC));

        setStat(R.id.row_alerts,      "Alerts",
                String.valueOf(stats.alertCount));
    }

    /** Ersetzt den Layout-Placeholder durch die echte PowerGraphView und füttert ihn. */
    private void injectPowerGraph(RunStats stats) {
        View placeholder = findViewById(R.id.view_power_graph);
        if (placeholder == null) return;
        ViewGroup parent = (ViewGroup) placeholder.getParent();
        int idx = parent.indexOfChild(placeholder);

        PowerGraphView graph = new PowerGraphView(this);
        graph.setLayoutParams(placeholder.getLayoutParams());
        graph.setId(R.id.view_power_graph);
        graph.setBackgroundColor(0xFF111418);
        parent.removeView(placeholder);
        parent.addView(graph, idx);

        if (stats != null) graph.setData(stats.powerSamples);
    }

    private void wireActions() {
        findViewById(R.id.btn_back_to_select).setOnClickListener(v -> {
            Intent i = new Intent(this, CarSelectActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });

        findViewById(R.id.btn_new_run).setOnClickListener(v -> {
            Intent i = new Intent(this, ConnectingActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });

        View export = findViewById(R.id.tv_export_csv);
        if (export != null) {
            export.setOnClickListener(v ->
                    Toast.makeText(this,
                            "CSV-Export folgt — Frame-Recording wird im nächsten Schritt ergänzt",
                            Toast.LENGTH_SHORT).show());
        }
    }

    /** Setzt Key + Value in einer <include>-Stat-Row. */
    private void setStat(int rowId, String key, String value) {
        View row = findViewById(rowId);
        if (row == null) return;
        TextView k = row.findViewById(R.id.tv_stat_key);
        TextView v = row.findViewById(R.id.tv_stat_value);
        if (k != null) k.setText(key);
        if (v != null) v.setText(value);
    }
}