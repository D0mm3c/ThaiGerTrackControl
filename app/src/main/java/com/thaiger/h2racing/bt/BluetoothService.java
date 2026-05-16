package com.thaiger.h2racing.bt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.thaiger.h2racing.model.TelemetryModel;
import com.thaiger.h2racing.parser.TelemetryParser;
import com.thaiger.h2racing.relay.FrameRelay;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * BLE-Schicht für das HM-10-Modul (Bluetooth 4.0, GATT).
 *
 * HM-10 UUIDs:
 *   Service:        0000FFE0-0000-1000-8000-00805F9B34FB
 *   Characteristic: 0000FFE1-0000-1000-8000-00805F9B34FB  (TX + RX, NOTIFY + WRITE)
 *   CCCD Descriptor:00002902-0000-1000-8000-00805F9B34FB
 *
 * Datenfluss:
 *   ESP32 → UART → HM-10 → BLE-Notifications (max 20 Bytes/Chunk) → Android
 *   → Akkumulator → TelemetryParser → applyFrame → UI-Listener + MQTT-Relay
 *
 * BLE-Callbacks laufen auf einem Binder-Thread (NICHT dem UI-Thread).
 * Alle UI-Updates werden per uiHandler in den Main-Thread gepostet.
 * Alle Verbindungssteuerungsoperationen (connect, reconnect-Timer) laufen
 * auf dem Main-Thread via ctrlHandler.
 *
 * Reconnect-Strategie: Exponential-Backoff 1 → 2 → 4 → 8 → 16s.
 * Connection-Timeout: nach 15s wird ein fehlgeschlagener Connect abgebrochen.
 *
 * Wichtig für Samsung-Geräte: 100ms Verzögerung zwischen
 * setCharacteristicNotification() und CCCD-Descriptor-Write verhindert
 * sporadische GATT-Fehler (Code 133).
 */
public class BluetoothService {

    private static final String TAG = "BleService";

    // HM-10 BLE UUIDs
    private static final UUID SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB");
    private static final UUID CHAR_UUID    = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB");
    private static final UUID CCCD_UUID    = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    private static final long CONNECT_TIMEOUT_MS = 15_000;
    private static final long BACKOFF_INIT_MS    = 1_000;
    private static final long BACKOFF_MAX_MS     = 16_000;

    // ─── Public API types ───

    public enum State { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED, STOPPED }

    /** Callbacks landen IMMER auf dem UI-Thread. */
    public interface Listener {
        void onState(State state, String detail);
        void onTelemetry(TelemetryModel model);
    }

    // ─── Fields ───

    private final Context ctx;
    private volatile Listener   listener;
    private volatile FrameRelay relay;

    private final Handler ctrlHandler = new Handler(Looper.getMainLooper());
    private final Handler uiHandler   = new Handler(Looper.getMainLooper());

    private volatile BluetoothGatt gatt;
    private volatile BluetoothGattCharacteristic txrxChar;

    private volatile boolean running  = false;
    private volatile boolean mockMode = false;
    private volatile String  deviceAddress;
    private volatile String  deviceName = "—";

    private long backoff = BACKOFF_INIT_MS;

    /** Akkumulator für BLE-Chunks (max 20 Bytes each). Zugriff synchronized. */
    private final StringBuilder accumulator = new StringBuilder(512);

    private long  lastPacketAtMs = 0;
    private float distanceKm    = 0f;
    private final TelemetryModel rollingFrame = new TelemetryModel();

    // ─── Constructor ───

    public BluetoothService(Context ctx, Listener listener) {
        this.ctx      = ctx.getApplicationContext();
        this.listener = listener;
    }

    // ─── Public API ───

    public void setListener(Listener l) { this.listener = l; }
    public void setRelay(FrameRelay r)  { this.relay = r; }
    public String getDeviceName()       { return deviceName; }

    /**
     * Verbindet mit einem gepairten HM-10 per MAC-Adresse.
     * Nicht-blockierend. Reconnect läuft automatisch im Hintergrund.
     */
    public void connect(String mac, String name) {
        this.deviceAddress = mac;
        this.deviceName    = name != null ? name : "—";
        this.mockMode      = false;
        this.running       = true;
        this.backoff       = BACKOFF_INIT_MS;
        ctrlHandler.post(this::doConnect);
    }

    /** Synthetischer Stream — kein BLE nötig. Gleicher Daten-Pfad wie bei echtem HM-10. */
    public void startMock() {
        this.mockMode   = true;
        this.deviceName = "Mock-Stream";
        this.running    = true;
        emitState(State.CONNECTED, "Mock telemetry running");
        new Thread(this::mockLoop, "BLE-Mock").start();
    }

    /** Teardown. Sicher vom UI-Thread aufrufbar. */
    public void stop() {
        running = false;
        ctrlHandler.removeCallbacksAndMessages(null);
        closeGattQuietly();
        emitState(State.STOPPED, "Stopped");
    }

    public long staleMs() {
        if (lastPacketAtMs == 0) return Long.MAX_VALUE;
        return System.currentTimeMillis() - lastPacketAtMs;
    }

    // ─── BLE Connect (always on ctrlHandler / main thread) ───

    private void doConnect() {
        if (!running) return;
        emitState(State.CONNECTING, "Connecting to " + deviceName + "…");

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            emitState(State.FAILED, "Bluetooth nicht verfügbar oder deaktiviert");
            return;
        }

        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(deviceAddress);
        } catch (IllegalArgumentException e) {
            emitState(State.FAILED, "Ungültige MAC-Adresse: " + deviceAddress);
            return;
        }

        closeGattQuietly();
        synchronized (accumulator) { accumulator.setLength(0); }

        try {
            gatt = device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            emitState(State.FAILED, "Bluetooth-Berechtigung fehlt: " + e.getMessage());
            return;
        }

        // Connection-Timeout
        ctrlHandler.postDelayed(() -> {
            if (running && gatt != null) {
                Log.w(TAG, "BLE connection timeout → retry");
                closeGattQuietly();
                scheduleReconnect();
            }
        }, CONNECT_TIMEOUT_MS);
    }

    private void scheduleReconnect() {
        if (!running) return;
        emitState(State.RECONNECTING, "Reconnect in " + (backoff / 1000) + "s…");
        ctrlHandler.postDelayed(this::doConnect, backoff);
        backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
    }

    // ─── GATT Callback (runs on Android Binder thread) ───

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT connected — discovering services…");
                ctrlHandler.removeCallbacksAndMessages(null); // cancel timeout
                backoff = BACKOFF_INIT_MS;
                try { g.discoverServices(); } catch (SecurityException ignored) {}

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "GATT disconnected, status=" + status);
                txrxChar = null;
                if (running) ctrlHandler.post(() -> scheduleReconnect());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed: " + status);
                if (running) ctrlHandler.post(() -> scheduleReconnect());
                return;
            }

            BluetoothGattService svc = g.getService(SERVICE_UUID);
            if (svc == null) {
                Log.w(TAG, "HM-10 service FFE0 nicht gefunden — ist das Gerät ein HM-10?");
                if (running) ctrlHandler.post(() -> scheduleReconnect());
                return;
            }

            txrxChar = svc.getCharacteristic(CHAR_UUID);
            if (txrxChar == null) {
                Log.w(TAG, "HM-10 characteristic FFE1 nicht gefunden");
                if (running) ctrlHandler.post(() -> scheduleReconnect());
                return;
            }

            // Schritt 1: Android-Stack informieren, Notifications weiterzuleiten
            try { g.setCharacteristicNotification(txrxChar, true); }
            catch (SecurityException ignored) {}

            // Schritt 2: Remote CCCD-Descriptor schreiben — 100ms Verzögerung für Samsung
            ctrlHandler.postDelayed(() -> enableRemoteNotifications(g), 100);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor desc, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Notifications aktiviert — HM-10 bereit");
                emitState(State.CONNECTED, "Connected to " + deviceName);
            } else {
                Log.w(TAG, "CCCD write failed: " + status);
                if (running) ctrlHandler.post(() -> scheduleReconnect());
            }
        }

        /**
         * Android ≤ 12 (API ≤ 32) — feuert auf Android 11.
         * Deprecated in API 33, aber weiterhin notwendig für unser Zielgerät.
         */
        @Override
        @SuppressWarnings("deprecation")
        public void onCharacteristicChanged(BluetoothGatt g,
                                            BluetoothGattCharacteristic characteristic) {
            handleChunk(characteristic.getValue());
        }

        /**
         * API 33+ — deklariert für Zukunftssicherheit.
         * Auf Android 11 wird stattdessen obige deprecated Methode aufgerufen.
         */
        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt g,
                                            @NonNull BluetoothGattCharacteristic characteristic,
                                            @NonNull byte[] value) {
            handleChunk(value);
        }
    };

    /** Schreibt den CCCD-Descriptor, um Remote-Notifications auf dem HM-10 zu aktivieren. */
    private void enableRemoteNotifications(BluetoothGatt g) {
        if (txrxChar == null) return;
        BluetoothGattDescriptor desc = txrxChar.getDescriptor(CCCD_UUID);
        if (desc == null) {
            // Manche HM-10-Clones haben keinen CCCD — senden Notifications trotzdem implizit
            Log.w(TAG, "CCCD nicht vorhanden — nehme implizite Notifications an (Clone-Modul)");
            emitState(State.CONNECTED, "Connected to " + deviceName + " (no CCCD)");
            return;
        }
        desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        try { g.writeDescriptor(desc); } catch (SecurityException ignored) {}
    }

    // ─── Data processing (called on Binder thread) ───

    private void handleChunk(byte[] raw) {
        if (raw == null || raw.length == 0) return;
        String chunk = new String(raw, StandardCharsets.US_ASCII);

        synchronized (accumulator) {
            accumulator.append(chunk);

            // Parser extrahiert alle vollständigen Felder aus dem Akkumulator.
            // Stream-tolerant: Felder können über mehrere Chunks verteilt sein.
            TelemetryModel parsed = TelemetryParser.parsePacket(accumulator.toString());
            if (parsed != null) {
                applyFrame(parsed);
            }

            // Akkumulator kürzen: alles bis inkl. letztem '*' verarbeitet;
            // dahinter könnte ein unvollständiges Feld stehen → behalten.
            int lastStar = accumulator.lastIndexOf("*");
            if (lastStar >= 0) {
                accumulator.delete(0, lastStar + 1);
            }
            if (accumulator.length() > 4096) accumulator.setLength(0);
        }
    }

    private void applyFrame(TelemetryModel parsed) {
        long now = System.currentTimeMillis();
        long dtMs = (lastPacketAtMs == 0) ? 0 : (now - lastPacketAtMs);
        lastPacketAtMs = now;

        // Distanz: ∫ speed dt (Phone-seitig, ESP sendet kein Distance-Feld)
        if (!Float.isNaN(parsed.speedKmh) && dtMs > 0 && dtMs < 5000) {
            distanceKm += parsed.speedKmh * (dtMs / 3_600_000f);
        }
        rollingFrame.mergeFrom(parsed);
        rollingFrame.distanceKm  = distanceKm;
        rollingFrame.timestampMs = now;

        TelemetryModel snapshot = snapshotOf(rollingFrame);
        emitTelemetry(snapshot);

        // MQTT-Relay: ruft onFrame() auf dem Binder-Thread auf
        FrameRelay r = relay;
        if (r != null) r.onFrame(snapshot);
    }

    private void closeGattQuietly() {
        BluetoothGatt g = gatt;
        gatt     = null;
        txrxChar = null;
        if (g != null) {
            try { g.disconnect(); } catch (Exception ignored) {}
            try { g.close();      } catch (Exception ignored) {}
        }
    }

    private TelemetryModel snapshotOf(TelemetryModel src) {
        TelemetryModel s = new TelemetryModel();
        s.mergeFrom(src);
        return s;
    }

    // ─── Mock Loop ───

    private void mockLoop() {
        final long startedAt = System.currentTimeMillis();
        float fcEnergyWs = 0f, motorEnergyWs = 0f;
        int laps = 0;
        double lapStartT = 0;

        while (running && mockMode) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            double t = elapsedMs / 1000.0;

            float speed   = (float)(27.0 + 3.0 * Math.sin(t / 4.0) + 0.4 * Math.sin(t * 3));
            float avgSpd  = (float) Math.max(15, Math.min(speed * 0.95, 30));
            int totalSec  = (int) t;
            float fcV     = (float)(28.0 + 0.5 * Math.sin(t / 8.0));
            float scV     = (float)(27.0 + 0.4 * Math.sin(t / 5.0));
            float motorV  = (float)(27.0 + 0.4 * Math.sin(t / 5.0));
            float fcA     = (float)(5.0  + 1.5 * Math.sin(t / 4.0));
            float scA     = (float)(2.0  + 0.7 * Math.sin(t * 0.5));
            float motorA  = (float)(8.0  + 2.0 * Math.sin(t / 4.0));
            float fcTemp  = (float)(45   + Math.min(30, t * 0.18));
            float cellDiff= (float)(30   + 25  * Math.sin(t / 7.0));
            fcEnergyWs    += fcV * fcA * 0.1f;
            motorEnergyWs += motorV * motorA * 0.1f;
            float fcEff   = 70f;
            float sysEff  = (float)(68 + 4 * Math.sin(t / 9.0));

            if (t - lapStartT > 20) { laps++; lapStartT = t; }
            int targetLap = 20;

            String packet = String.format(java.util.Locale.US,
                    "*A%.1f**B%.1f**C%d**D%d:%02d**E%d:%02d**F25.0*" +
                            "*G%.1f**H%.1f**I%.1f**J%.1f**K%.1f**L%.1f**M0.7*" +
                            "*N%.1f**O63**P65**Q%.0f**R%.0f**S%.0f**T%.0f**U%.0f*",
                    speed, avgSpd, laps,
                    totalSec / 60, totalSec % 60,
                    targetLap / 60, targetLap % 60,
                    fcV, scV, motorV, fcA, scA, motorA,
                    fcTemp, cellDiff,
                    fcEnergyWs, motorEnergyWs, fcEff, sysEff);

            TelemetryModel parsed = TelemetryParser.parsePacket(packet);
            if (parsed != null) applyFrame(parsed);

            try { Thread.sleep(100); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    // ─── UI-thread bridges ───

    private void emitState(State state, String detail) {
        Listener l = listener;
        if (l == null) return;
        uiHandler.post(() -> { Listener l2 = listener; if (l2 != null) l2.onState(state, detail); });
    }

    private void emitTelemetry(TelemetryModel m) {
        Listener l = listener;
        if (l == null) return;
        uiHandler.post(() -> { Listener l2 = listener; if (l2 != null) l2.onTelemetry(m); });
    }
}