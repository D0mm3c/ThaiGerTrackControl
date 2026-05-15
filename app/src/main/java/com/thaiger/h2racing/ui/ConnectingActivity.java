package com.thaiger.h2racing.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.thaiger.h2racing.App;
import com.thaiger.h2racing.R;
import com.thaiger.h2racing.bt.BluetoothService;
import com.thaiger.h2racing.model.RunStats;
import com.thaiger.h2racing.model.TelemetryModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Screen 2 — Bluetooth Connecting.
 *
 * Workflow:
 *   1) Permissions prüfen / anfordern
 *   2) BT-Adapter prüfen (vorhanden + an)
 *   3) Liste gepairter Geräte zeigen + Demo-Mode-Option
 *   4) Nach Auswahl: BluetoothService starten und an DashboardActivity übergeben
 *
 * "Pair New Device" öffnet die System-Bluetooth-Settings.
 * "Cancel" geht zurück zum Car-Select.
 *
 * Auto-Advance: sobald der Service State.CONNECTED meldet, wechseln wir
 * automatisch in den Dashboard-Screen.
 */
public class ConnectingActivity extends AppCompatActivity {

    private static final int REQ_BT_PERMISSIONS = 101;

    private ProgressBar pbConnecting;
    private TextView    tvTitle;
    private TextView    tvDevice;
    private TextView    tvReconnect;

    private BluetoothService service;
    private boolean advancedToDashboard = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connecting);

        pbConnecting = findViewById(R.id.progress_connecting);
        tvTitle      = findViewById(R.id.tv_connecting_title);
        tvDevice     = findViewById(R.id.tv_connecting_device);
        tvReconnect  = findViewById(R.id.tv_reconnect_note);

        findViewById(R.id.btn_cancel).setOnClickListener(v -> finishToCarSelect());
        findViewById(R.id.btn_pair_new).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));

        tvTitle.setText("Preparing…");
        tvDevice.setText("Checking Bluetooth permissions");

        if (ensurePermissions()) {
            startFlow();
        }
    }

    /** Permission-Check: BT_CONNECT/SCAN auf 12+, LOCATION auf 9-11. */
    private boolean ensurePermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.BLUETOOTH_SCAN);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (needed.isEmpty()) return true;
        ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_BT_PERMISSIONS);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_BT_PERMISSIONS) return;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        "Bluetooth-Berechtigung benötigt. Demo-Mode bleibt verfügbar.",
                        Toast.LENGTH_LONG).show();
                showDevicePicker(/* paired = */ null, /* allowReal = */ false);
                return;
            }
        }
        startFlow();
    }

    private void startFlow() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            tvTitle.setText("Kein Bluetooth verfügbar");
            tvDevice.setText("Gerät unterstützt kein Classic Bluetooth");
            showDevicePicker(null, false);
            return;
        }
        if (!adapter.isEnabled()) {
            tvTitle.setText("Bluetooth ist aus");
            tvDevice.setText("In System-Einstellungen aktivieren");
            showDevicePicker(null, false);
            return;
        }

        Set<BluetoothDevice> paired;
        try {
            paired = adapter.getBondedDevices();
        } catch (SecurityException e) {
            paired = null;
        }
        showDevicePicker(paired, paired != null && !paired.isEmpty());
    }

    /**
     * Zeigt Auswahldialog: alle gepairten Geräte + "Demo Mode" als letzten Eintrag.
     * Wenn ein bekannter Name (HC-05, THAIGER, ESP32) dabei ist, springt der Cursor
     * dort hin — die Liste lässt sich aber immer noch frei wählen.
     */
    @SuppressWarnings("MissingPermission")
    private void showDevicePicker(Set<BluetoothDevice> paired, boolean allowReal) {
        List<String> labels = new ArrayList<>();
        List<BluetoothDevice> devices = new ArrayList<>();

        if (allowReal && paired != null) {
            for (BluetoothDevice d : paired) {
                String name;
                try { name = d.getName(); }
                catch (SecurityException e) { name = null; }
                if (name == null) name = "(unbekannt)";
                labels.add(name + "\n" + d.getAddress());
                devices.add(d);
            }
        }
        labels.add("⚙  Demo Mode (synthetischer Stream)");

        new AlertDialog.Builder(this)
                .setTitle("Gerät wählen")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which == labels.size() - 1) {
                        startMockSession();
                    } else {
                        startRealSession(devices.get(which));
                    }
                })
                .setNegativeButton("Pair Neu", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                .setNeutralButton("Cancel", (d, w) -> finishToCarSelect())
                .setCancelable(false)
                .show();
    }

    @SuppressWarnings("MissingPermission")
    private void startRealSession(BluetoothDevice device) {
        String name;
        try { name = device.getName(); }
        catch (SecurityException e) { name = device.getAddress(); }
        if (name == null) name = device.getAddress();

        tvTitle.setText("Connecting to " + name + "…");
        tvDevice.setText(device.getAddress());

        service = new BluetoothService(new BluetoothService.Listener() {
            @Override public void onState(BluetoothService.State s, String detail) { applyState(s, detail); }
            @Override public void onTelemetry(TelemetryModel m) { /* not used here */ }
        });
        ((App) getApplication()).setBluetoothService(service);
        ((App) getApplication()).setRunStats(new RunStats());
        service.connect(device.getAddress(), name);
    }

    private void startMockSession() {
        tvTitle.setText("Demo Mode");
        tvDevice.setText("Synthetic stream — kein BT benötigt");

        service = new BluetoothService(new BluetoothService.Listener() {
            @Override public void onState(BluetoothService.State s, String detail) { applyState(s, detail); }
            @Override public void onTelemetry(TelemetryModel m) { /* not used here */ }
        });
        ((App) getApplication()).setBluetoothService(service);
        ((App) getApplication()).setRunStats(new RunStats());
        service.startMock();
    }

    private void applyState(BluetoothService.State state, String detail) {
        if (advancedToDashboard) return;
        switch (state) {
            case CONNECTING:
                tvTitle.setText("Connecting…");
                tvDevice.setText(detail);
                break;
            case CONNECTED:
                tvTitle.setText("Connected");
                tvDevice.setText(detail);
                advancedToDashboard = true;
                startActivity(new Intent(this, DashboardActivity.class));
                finish();
                break;
            case RECONNECTING:
                tvTitle.setText("Reconnecting…");
                tvDevice.setText(detail);
                break;
            case FAILED:
                tvTitle.setText("Verbindung fehlgeschlagen");
                tvDevice.setText(detail);
                tvReconnect.setText("Tap 'Cancel' und neu versuchen, oder Pair Neu");
                break;
            case STOPPED:
                // ignore
                break;
        }
    }

    private void finishToCarSelect() {
        if (service != null) service.stop();
        ((App) getApplication()).setBluetoothService(null);
        finish();
    }

    @Override
    public void onBackPressed() {
        finishToCarSelect();
    }
}