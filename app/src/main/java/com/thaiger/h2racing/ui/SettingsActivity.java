package com.thaiger.h2racing.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.thaiger.h2racing.R;
import com.thaiger.h2racing.model.CarProfile;
import com.thaiger.h2racing.util.Prefs;

import java.util.Locale;

/**
 * Screen 6 — Einstellungen.
 *
 * Persistiert via {@link Prefs}. Alle Änderungen werden sofort gespeichert
 * und beim nächsten Start des Dashboards übernommen.
 */
public class SettingsActivity extends AppCompatActivity {

    private Prefs prefs;

    private Switch swWakeLock;
    private Switch swVibrate;
    private Switch swAlertSound;
    private TextView tvUpdateRate;
    private TextView tvThresholdThaiger;
    private TextView tvThresholdBengalo;
    private TextView tvVoltDiffThaiger;
    private TextView tvVoltDiffBengalo;
    private TextView tvMqttSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new Prefs(this);

        swWakeLock         = findViewById(R.id.sw_wake_lock);
        swVibrate          = findViewById(R.id.sw_vibrate);
        swAlertSound       = findViewById(R.id.sw_alert_sound);
        tvUpdateRate       = findViewById(R.id.tv_update_rate_value);
        tvThresholdThaiger = findViewById(R.id.tv_fc_threshold_thaiger);
        tvThresholdBengalo = findViewById(R.id.tv_fc_threshold_bengalo);
        tvVoltDiffThaiger  = findViewById(R.id.tv_voltdiff_threshold_thaiger);
        tvVoltDiffBengalo  = findViewById(R.id.tv_voltdiff_threshold_bengalo);
        tvMqttSummary      = findViewById(R.id.tv_mqtt_summary);

        findViewById(R.id.row_mqtt_open).setOnClickListener(v ->
                startActivity(new Intent(this, MqttSettingsActivity.class)));

        // Aktuelle Werte einfüllen
        swWakeLock.setChecked(prefs.isWakeLock());
        swVibrate.setChecked(prefs.isVibrate());
        swAlertSound.setChecked(prefs.isAlertSound());
        renderUpdateRate();
        renderThresholds();
        renderMqttSummary();

        // Switch-Listener
        swWakeLock.setOnCheckedChangeListener((b, checked) -> prefs.setWakeLock(checked));
        swVibrate.setOnCheckedChangeListener((b, checked) -> prefs.setVibrate(checked));
        swAlertSound.setOnCheckedChangeListener((b, checked) -> prefs.setAlertSound(checked));

        // TextView-Klicks öffnen Dialoge
        tvUpdateRate.setOnClickListener(v -> showIntDialog(
                "Update rate (ms)",
                prefs.getUpdateRateMs(),
                20, 500,
                newVal -> {
                    prefs.setUpdateRateMs(newVal);
                    renderUpdateRate();
                }));

        tvThresholdThaiger.setOnClickListener(v -> showFloatDialog(
                "FC Temp Limit — Thaiger 7 (°C)",
                prefs.getFcTempMaxC(CarProfile.THAIGER_7),
                30, 120, "°C",
                newVal -> {
                    prefs.setFcTempMaxC(CarProfile.THAIGER_7, newVal);
                    renderThresholds();
                }));

        tvThresholdBengalo.setOnClickListener(v -> showFloatDialog(
                "FC Temp Limit — Bengalo (°C)",
                prefs.getFcTempMaxC(CarProfile.BENGALO),
                30, 120, "°C",
                newVal -> {
                    prefs.setFcTempMaxC(CarProfile.BENGALO, newVal);
                    renderThresholds();
                }));

        tvVoltDiffThaiger.setOnClickListener(v -> showFloatDialog(
                "Cell Voltage Diff Limit — Thaiger 7 (mV)",
                prefs.getCellDiffMaxMv(CarProfile.THAIGER_7),
                10, 500, "mV",
                newVal -> {
                    prefs.setCellDiffMaxMv(CarProfile.THAIGER_7, newVal);
                    renderThresholds();
                }));

        tvVoltDiffBengalo.setOnClickListener(v -> showFloatDialog(
                "Cell Voltage Diff Limit — Bengalo (mV)",
                prefs.getCellDiffMaxMv(CarProfile.BENGALO),
                10, 500, "mV",
                newVal -> {
                    prefs.setCellDiffMaxMv(CarProfile.BENGALO, newVal);
                    renderThresholds();
                }));

        findViewById(R.id.tv_settings_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Wenn der User aus dem MQTT-Untermenü zurückkommt, neu rendern
        renderMqttSummary();
    }

    private void renderMqttSummary() {
        if (tvMqttSummary == null) return;
        String host = prefs.getMqttHost();
        if (!prefs.isMqttEnabled() || host.isEmpty()) {
            tvMqttSummary.setText("Not configured");
        } else {
            tvMqttSummary.setText(String.format(Locale.US, "%s:%d · %s",
                    host, prefs.getMqttPort(),
                    prefs.isMqttUseTls() ? "TLS" : "plain"));
        }
    }

    private void renderUpdateRate() {
        tvUpdateRate.setText(String.format(Locale.US, "%d ms ›", prefs.getUpdateRateMs()));
    }

    private void renderThresholds() {
        tvThresholdThaiger.setText(String.format(Locale.US, "%.0f °C ›",
                prefs.getFcTempMaxC(CarProfile.THAIGER_7)));
        tvThresholdBengalo.setText(String.format(Locale.US, "%.0f °C ›",
                prefs.getFcTempMaxC(CarProfile.BENGALO)));
        tvVoltDiffThaiger.setText(String.format(Locale.US, "%.0f mV ›",
                prefs.getCellDiffMaxMv(CarProfile.THAIGER_7)));
        tvVoltDiffBengalo.setText(String.format(Locale.US, "%.0f mV ›",
                prefs.getCellDiffMaxMv(CarProfile.BENGALO)));
    }

    // ─── Dialog-Helper ───

    private interface IntCallback   { void onValue(int v); }
    private interface FloatCallback { void onValue(float v); }

    private void showIntDialog(String title, int current, int min, int max, IntCallback cb) {
        EditText input = buildEditText(InputType.TYPE_CLASS_NUMBER, String.valueOf(current));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Range: " + min + " – " + max)
                .setView(wrap(input))
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        int v = Integer.parseInt(input.getText().toString().trim());
                        v = Math.max(min, Math.min(max, v));
                        cb.onValue(v);
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFloatDialog(String title, float current, float min, float max,
                                 String unitHint, FloatCallback cb) {
        EditText input = buildEditText(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
                String.format(Locale.US, "%.1f", current));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Range: " + (int) min + " – " + (int) max + " " + unitHint)
                .setView(wrap(input))
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        float v = Float.parseFloat(input.getText().toString().trim().replace(',', '.'));
                        v = Math.max(min, Math.min(max, v));
                        cb.onValue(v);
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EditText buildEditText(int inputType, String initial) {
        EditText et = new EditText(this);
        et.setInputType(inputType);
        et.setText(initial);
        et.setSelectAllOnFocus(true);
        et.setGravity(Gravity.CENTER);
        et.setTextSize(20f);
        return et;
    }

    /** Padding rundherum, damit der EditText nicht am Dialog-Rand klebt. */
    private android.widget.FrameLayout wrap(EditText et) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(this);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int dp24 = (int)(24 * getResources().getDisplayMetrics().density);
        lp.setMargins(dp24, dp24/2, dp24, 0);
        et.setLayoutParams(lp);
        f.addView(et);
        return f;
    }
}