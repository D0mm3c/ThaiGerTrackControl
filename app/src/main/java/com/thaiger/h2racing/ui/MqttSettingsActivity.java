package com.thaiger.h2racing.ui;

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
import com.thaiger.h2racing.util.Prefs;

import java.util.Locale;

/**
 * Screen 6b — MQTT-/Telemetry-Relay-Einstellungen.
 *
 * Eigenes Untermenü, vom Settings-Hauptscreen aus erreichbar. Sammelt alle
 * Broker-Konfigurationsfelder an einem Ort, damit der Haupt-Settings-Screen
 * nicht zu lang wird.
 *
 * Persistenz: alles in {@link Prefs}. Werte werden beim nächsten Run vom
 * Relay-Service ausgelesen.
 */
public class MqttSettingsActivity extends AppCompatActivity {

    private Prefs prefs;

    private Switch swEnabled;
    private Switch swTls;
    private TextView tvHost;
    private TextView tvPort;
    private TextView tvUser;
    private TextView tvPass;
    private TextView tvRate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_mqtt);

        prefs = new Prefs(this);

        swEnabled = findViewById(R.id.sw_mqtt_enabled);
        swTls     = findViewById(R.id.sw_mqtt_tls);
        tvHost    = findViewById(R.id.tv_mqtt_host_value);
        tvPort    = findViewById(R.id.tv_mqtt_port_value);
        tvUser    = findViewById(R.id.tv_mqtt_user_value);
        tvPass    = findViewById(R.id.tv_mqtt_pass_value);
        tvRate    = findViewById(R.id.tv_mqtt_rate_value);

        renderAll();

        swEnabled.setOnCheckedChangeListener((b, checked) -> prefs.setMqttEnabled(checked));
        swTls.setOnCheckedChangeListener((b, checked) -> prefs.setMqttUseTls(checked));

        findViewById(R.id.row_mqtt_host).setOnClickListener(v -> showStringDialog(
                "Broker host",
                "e.g. abc123.s1.eu.hivemq.cloud",
                prefs.getMqttHost(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                newVal -> { prefs.setMqttHost(newVal); renderHost(); }));

        findViewById(R.id.row_mqtt_port).setOnClickListener(v -> showIntDialog(
                "Port", prefs.getMqttPort(), 1, 65535,
                newVal -> { prefs.setMqttPort(newVal); renderPort(); }));

        findViewById(R.id.row_mqtt_user).setOnClickListener(v -> showStringDialog(
                "Username", "", prefs.getMqttUsername(),
                InputType.TYPE_CLASS_TEXT,
                newVal -> { prefs.setMqttUsername(newVal); renderUser(); }));

        findViewById(R.id.row_mqtt_pass).setOnClickListener(v -> showStringDialog(
                "Password", "", prefs.getMqttPassword(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                newVal -> { prefs.setMqttPassword(newVal); renderPass(); }));

        findViewById(R.id.row_mqtt_rate).setOnClickListener(v -> showIntDialog(
                "Publish rate (Hz)", prefs.getMqttRateHz(), 1, 10,
                newVal -> { prefs.setMqttRateHz(newVal); renderRate(); }));

        findViewById(R.id.tv_mqtt_back).setOnClickListener(v -> finish());
    }

    private void renderAll() {
        swEnabled.setChecked(prefs.isMqttEnabled());
        swTls.setChecked(prefs.isMqttUseTls());
        renderHost();
        renderPort();
        renderUser();
        renderPass();
        renderRate();
    }

    private void renderHost() {
        String h = prefs.getMqttHost();
        tvHost.setText(h.isEmpty() ? "—" : h);
    }
    private void renderPort() { tvPort.setText(String.valueOf(prefs.getMqttPort())); }
    private void renderUser() {
        String u = prefs.getMqttUsername();
        tvUser.setText(u.isEmpty() ? "—" : u);
    }
    private void renderPass() {
        String p = prefs.getMqttPassword();
        tvPass.setText(p.isEmpty() ? "—" : "••••••••");
    }
    private void renderRate() {
        tvRate.setText(String.format(Locale.US, "%d Hz", prefs.getMqttRateHz()));
    }

    // ─── Dialog-Helper ───

    private interface StringCallback { void onValue(String v); }
    private interface IntCallback    { void onValue(int v); }

    private void showStringDialog(String title, String hint, String current,
                                  int inputType, StringCallback cb) {
        EditText input = buildEditText(inputType, current);
        input.setHint(hint);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(wrap(input))
                .setPositiveButton("Save", (d, w) -> cb.onValue(input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

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

    private EditText buildEditText(int inputType, String initial) {
        EditText et = new EditText(this);
        et.setInputType(inputType);
        et.setText(initial);
        et.setSelectAllOnFocus(true);
        et.setGravity(Gravity.CENTER);
        et.setTextSize(16f);
        return et;
    }

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