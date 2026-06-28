package com.thaiger.h2racing.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.thaiger.h2racing.App;
import com.thaiger.h2racing.R;
import com.thaiger.h2racing.bt.BluetoothService;
import com.thaiger.h2racing.model.CarProfile;
import com.thaiger.h2racing.model.RunStats;
import com.thaiger.h2racing.model.TelemetryModel;
import com.thaiger.h2racing.relay.MqttRelayService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
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

        // Run beenden: BluetoothService + Relay stoppen, RunStats finalisieren
        App app = (App) getApplication();
        BluetoothService svc = app.getBluetoothService();
        if (svc != null) svc.stop();
        app.setBluetoothService(null);

        MqttRelayService relay = app.getRelayService();
        if (relay != null) relay.stop();
        app.setRelayService(null);

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

        RunStats stats = ((App) getApplication()).getRunStats();
        CarProfile car = ((App) getApplication()).getCarProfile();
        View export = findViewById(R.id.tv_export_csv);
        if (export != null) {
            export.setOnClickListener(v -> exportCsv(stats, car));
        }
    }

    private void exportCsv(RunStats stats, CarProfile car) {
        if (stats == null || stats.frames.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        String ts = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date(stats.startedAtMs));
        String fileName = "thaiger_" + (car != null ? car.id : "run") + "_" + ts + ".csv";
        File outFile = new File(getCacheDir(), fileName);

        try (FileWriter fw = new FileWriter(outFile)) {
            // Real per-frame time first (phone clock) — always present, even when the
            // controller sends no run-time field (e.g. Bengalo). esp_time_s keeps the
            // controller's own run time (may be empty).
            fw.write("timestamp_ms,elapsed_s,esp_time_s," +
                     "speed_kmh,avg_speed_kmh,laps,target_lap_s,optimal_speed_kmh," +
                     "fc_voltage_v,supercap_voltage_v,motor_voltage_v," +
                     "fc_current_a,supercap_current_a,motor_current_a,own_consumption_a," +
                     "fc_temp_c,air_pump_pct,driving_hint,cell_volt_diff_mv," +
                     "fc_energy_ws,motor_energy_ws,fc_efficiency_pct,sys_efficiency_pct," +
                     "distance_km,motor_power_w\n");

            List<TelemetryModel> frames = stats.frames;
            long t0 = frames.get(0).timestampMs;   // elapsed_s is relative to the first frame
            for (TelemetryModel m : frames) {
                fw.write(m.timestampMs + "," +
                         String.format(Locale.US, "%.2f", (m.timestampMs - t0) / 1000.0) + "," +
                         csvInt(m.totalTimeSec) + "," +
                         csvFloat(m.speedKmh) + "," +
                         csvFloat(m.avgSpeedKmh) + "," +
                         csvInt(m.laps) + "," +
                         csvInt(m.targetLapTimeSec) + "," +
                         csvFloat(m.optimalSpeedKmh) + "," +
                         csvFloat(m.fcVoltageV) + "," +
                         csvFloat(m.supercapVoltageV) + "," +
                         csvFloat(m.motorVoltageV) + "," +
                         csvFloat(m.fcCurrentA) + "," +
                         csvFloat(m.supercapCurrentA) + "," +
                         csvFloat(m.motorCurrentA) + "," +
                         csvFloat(m.ownConsumptionA) + "," +
                         csvFloat(m.fcTempC) + "," +
                         csvFloat(m.airPumpDutyPct) + "," +
                         csvFloat(m.drivingHint) + "," +
                         csvFloat(m.cellVoltDiffMv) + "," +
                         csvFloat(m.fcEnergyWs) + "," +
                         csvFloat(m.motorEnergyWs) + "," +
                         csvFloat(m.fcEfficiencyPct) + "," +
                         csvFloat(m.sysEfficiencyPct) + "," +
                         csvFloat(m.distanceKm) + "," +
                         csvFloat(m.motorPowerW()) + "\n");
            }
        } catch (IOException e) {
            Log.e("PostRun", "CSV write failed", e);
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", outFile);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, "ThaiGer run export — " + fileName);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share CSV"));
    }

    private static String csvFloat(float v) {
        return Float.isNaN(v) ? "" : String.format(Locale.US, "%.4f", v);
    }

    private static String csvInt(int v) {
        return v < 0 ? "" : String.valueOf(v);
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