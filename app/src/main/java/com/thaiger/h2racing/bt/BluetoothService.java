package com.thaiger.h2racing.bt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.thaiger.h2racing.model.TelemetryModel;
import com.thaiger.h2racing.parser.TelemetryParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Resilienter Bluetooth-Classic-SPP-Layer.
 *
 * Architektur:
 *   ┌─────────────────────┐
 *   │ HandlerThread BT-IO │ ──▶ Parse ──▶ Listener.onTelemetry()
 *   │  BluetoothSocket    │              (über UI-Handler dispatched)
 *   │  InputStream        │
 *   └─────────────────────┘
 *
 * Pillar 1 des Designs: UI-Thread blockiert NIE auf Socket-I/O. Reads laufen
 * auf dem "BT-IO"-HandlerThread; Ergebnisse werden via UI-Handler in den
 * Main-Thread gepostet.
 *
 * Auto-Reconnect: bei IOException springt der Thread zurück an den connect()-
 * Anfang mit Exponential-Backoff (1s → 2s → 4s → 8s, max 8s). State-Updates
 * werden für die UI ausgelöst, damit der Fahrer sieht: "BT ● reconnecting…".
 *
 * Mock-Mode: {@link #startMock()} generiert das echte XLSX-Frame-Format, damit
 * das gesamte UI auch ohne ESP32 oder BT-Gegenstelle getestet werden kann.
 */
public class BluetoothService {

    private static final String TAG = "BluetoothService";

    /** Standard SerialPortService SPP UUID. HC-05, ESP32 BT-Classic nutzen genau diese. */
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final long BACKOFF_INITIAL_MS = 1000L;
    private static final long BACKOFF_MAX_MS     = 8000L;

    /** Lese-Puffergröße. ESP-Pakete sind <300 Bytes, 1024 ist großzügig. */
    private static final int READ_BUFFER_BYTES = 1024;

    public enum State { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED, STOPPED }

    /** Listener-Callbacks landen IMMER auf dem UI-Thread. */
    public interface Listener {
        void onState(State state, String detail);
        void onTelemetry(TelemetryModel model);
    }

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    /** Volatile: ConnectingActivity setzt, DashboardActivity übernimmt. */
    private volatile Listener listener;

    private HandlerThread ioThread;
    private Handler       ioHandler;

    private volatile boolean running        = false;
    private volatile boolean mockMode       = false;
    private volatile BluetoothSocket socket;
    private volatile String  deviceAddress;
    private volatile String  deviceName     = "—";

    /** Akkumulierende "Roll-Frame" — die jeweils letzte bekannte Sicht aller Felder. */
    private final TelemetryModel rollingFrame = new TelemetryModel();

    /** Für Distanz-Integration. */
    private long  lastPacketAtMs   = 0;
    private float distanceKm       = 0f;

    public BluetoothService(Listener listener) {
        this.listener = listener;
    }

    /** Tauscht den UI-Listener (Activity-Wechsel). */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ───────────────────────── Public API ─────────────────────────

    /** Verbindet mit einem gepairten Gerät (MAC-Adresse). Non-blocking. */
    public void connect(String macAddress, String displayName) {
        this.deviceAddress = macAddress;
        this.deviceName    = displayName != null ? displayName : "—";
        this.mockMode      = false;
        startIoThread();
        running = true;
        ioHandler.post(this::connectLoop);
    }

    /** Synthetischer Stream — ohne Hardware. Format identisch mit ESP-XLSX-Protokoll. */
    public void startMock() {
        this.mockMode   = true;
        this.deviceName = "Mock-Stream";
        startIoThread();
        running = true;
        emitState(State.CONNECTED, "Mock telemetry running");
        ioHandler.post(this::mockLoop);
    }

    /** Tear-down. Sicher von UI-Thread aus aufrufbar. */
    public void stop() {
        running = false;
        closeSocketQuietly();
        if (ioThread != null) {
            ioThread.quitSafely();
            ioThread  = null;
            ioHandler = null;
        }
        emitState(State.STOPPED, "Stopped");
    }

    /** Wie lange ist der letzte erfolgreiche Frame her? */
    public long staleMs() {
        if (lastPacketAtMs == 0) return Long.MAX_VALUE;
        return System.currentTimeMillis() - lastPacketAtMs;
    }

    public String getDeviceName() { return deviceName; }

    // ───────────────────────── Internals ─────────────────────────

    private void startIoThread() {
        if (ioThread != null) return;
        ioThread = new HandlerThread("BT-IO");
        ioThread.start();
        ioHandler = new Handler(ioThread.getLooper());
    }

    /**
     * Connect-und-Read-Loop mit Exponential-Backoff.
     * Läuft komplett auf dem BT-IO-Thread. Nichts UI-bezogenes.
     */
    private void connectLoop() {
        long backoff = BACKOFF_INITIAL_MS;

        while (running) {
            emitState(State.CONNECTING, "Connecting to " + deviceName + "…");

            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                emitState(State.FAILED, "Gerät hat keinen Bluetooth-Adapter");
                return;
            }
            if (!adapter.isEnabled()) {
                emitState(State.FAILED, "Bluetooth ist aus — in System-Einstellungen aktivieren");
                return;
            }
            // Discovery abbrechen — verlangsamt Socket-Connects deutlich.
            try { adapter.cancelDiscovery(); } catch (SecurityException ignored) {}

            BluetoothDevice device;
            try {
                device = adapter.getRemoteDevice(deviceAddress);
            } catch (IllegalArgumentException e) {
                emitState(State.FAILED, "Ungültige MAC-Adresse: " + deviceAddress);
                return;
            }

            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();              // blockiert bis Connect oder IOException
                emitState(State.CONNECTED, "Connected to " + deviceName);
                backoff = BACKOFF_INITIAL_MS;

                readUntilDisconnect(socket);   // läuft bis Verbindung abbricht
            } catch (IOException | SecurityException e) {
                Log.w(TAG, "BT connect/read error: " + e.getMessage());
            } finally {
                closeSocketQuietly();
            }

            if (!running) break;

            emitState(State.RECONNECTING,
                    "Reconnect in " + (backoff / 1000) + "s…");
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
        }
        emitState(State.STOPPED, "Loop exited");
    }

    /**
     * Liest fortlaufend vom Socket. Das ESP-Protokoll garantiert keine Newlines,
     * deswegen lesen wir Bytes batchweise und füttern jedes Chunk an den
     * feldweise arbeitenden Parser. Das funktioniert mit und ohne Paket-Delimiter.
     */
    private void readUntilDisconnect(BluetoothSocket sock) throws IOException {
        InputStream in = sock.getInputStream();
        byte[] buf = new byte[READ_BUFFER_BYTES];
        StringBuilder accumulator = new StringBuilder();

        while (running) {
            int read = in.read(buf);
            if (read < 0) break;                // remote closed
            if (read == 0) continue;

            // ASCII-Daten — direkt String draus machen.
            accumulator.append(new String(buf, 0, read, java.nio.charset.StandardCharsets.US_ASCII));

            // Bei jedem Asterisk-Ende (`*X<value>*`) hat der Parser was zu tun.
            // Wir lassen den Parser am vollen Akkumulator laufen, schneiden danach
            // alles vor dem letzten `*` weg (potenziell unvollständiges Feld am Ende).
            String chunk = accumulator.toString();
            TelemetryModel parsed = TelemetryParser.parsePacket(chunk);
            if (parsed != null) {
                applyFrame(parsed);
            }

            // Truncation: alles vor dem letzten `*` ist verarbeitet, danach könnte
            // ein angefangenes Feld stehen.
            int lastStar = chunk.lastIndexOf('*');
            if (lastStar >= 0) {
                accumulator.delete(0, lastStar + 1);
            }
            // Schutz gegen Pathological-Wachstum
            if (accumulator.length() > 4096) {
                accumulator.setLength(0);
            }
        }
    }

    /** Merged ein frisch geparsetes Frame in den Roll-Frame, integriert Distanz, sendet ans UI. */
    private void applyFrame(TelemetryModel parsed) {
        long now = System.currentTimeMillis();
        long dtMs = (lastPacketAtMs == 0) ? 0 : (now - lastPacketAtMs);
        lastPacketAtMs = now;

        // Distanz: ∫ speed dt. Speed in km/h, dt in ms → km/h * (ms/3600000) = km
        if (!Float.isNaN(parsed.speedKmh) && dtMs > 0 && dtMs < 5000) {
            distanceKm += parsed.speedKmh * (dtMs / 3_600_000f);
        }
        rollingFrame.mergeFrom(parsed);
        rollingFrame.distanceKm = distanceKm;
        rollingFrame.timestampMs = now;

        // Snapshot rauspushen, damit der UI-Thread nicht auf einem Live-Objekt arbeitet,
        // das parallel weiter mutiert wird.
        TelemetryModel snapshot = snapshotOf(rollingFrame);
        emitTelemetry(snapshot);
    }

    /** Generiert ESP-Format-Pakete bei ~10 Hz. Speist sich selbst durch den Parser. */
    private void mockLoop() {
        final long startedAt = System.currentTimeMillis();
        float fcEnergyWs    = 0f;
        float motorEnergyWs = 0f;
        int   laps          = 0;
        double lapStartT    = 0;

        while (running && mockMode) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            double t = elapsedMs / 1000.0;

            float speed       = (float)(27.0 + 3.0 * Math.sin(t / 4.0) + 0.4 * Math.sin(t * 3));
            float avgSpeed    = (float) Math.max(15, Math.min(speed * 0.95, 30));
            int   totalSec    = (int) t;
            float optSpeed    = 26.0f;
            float fcV         = (float)(28.0 + 0.5 * Math.sin(t / 8.0));
            float scV         = (float)(27.0 + 0.4 * Math.sin(t / 5.0));
            float motorV      = (float)(27.0 + 0.4 * Math.sin(t / 5.0));
            float fcA         = (float)(5.0 + 1.5 * Math.sin(t / 4.0));
            float scA         = (float)(2.0 + 0.7 * Math.sin(t * 0.5));
            float motorA      = (float)(8.0 + 2.0 * Math.sin(t / 4.0));
            float ownA        = 0.7f;
            // FC-Temp steigt langsam — kreuzt 70° irgendwann, damit Alert getriggert wird
            float fcTemp      = (float)(45 + Math.min(30, t * 0.35));
            float pumpDuty    = 63f;
            float drivingHint = (float)(50 + 15 * Math.sin(t / 6.0));
            float cellDiff    = (float)(30 + 25 * Math.sin(t / 7.0));
            fcEnergyWs    += fcV * fcA * 0.1f;          // 100ms Tick
            motorEnergyWs += motorV * motorA * 0.1f;
            float fcEff       = 70f;
            float sysEff      = (float)(68 + 4 * Math.sin(t / 9.0));

            // Rundenlogik: alle 20s eine Runde (verkürzt fürs Demo — real wäre's ~2min)
            if (t - lapStartT > 20) { laps++; lapStartT = t; }
            int targetLap = 20;

            String packet = String.format(java.util.Locale.US,
                    "*A%.1f**B%.1f**C%d**D%d:%02d**E%d:%02d**F%.1f*" +
                            "*G%.1f**H%.1f**I%.1f**J%.1f**K%.1f**L%.1f**M%.1f*" +
                            "*N%.1f**O%.0f**P%.0f**Q%.0f**R%.0f**S%.0f**T%.0f**U%.0f*",
                    speed, avgSpeed, laps,
                    totalSec / 60, totalSec % 60,
                    targetLap / 60, targetLap % 60,
                    optSpeed,
                    fcV, scV, motorV, fcA, scA, motorA, ownA,
                    fcTemp, pumpDuty, drivingHint, cellDiff,
                    fcEnergyWs, motorEnergyWs, fcEff, sysEff);

            // Durch den echten Parser — gleicher Pfad wie bei echtem BT.
            TelemetryModel parsed = TelemetryParser.parsePacket(packet);
            if (parsed != null) applyFrame(parsed);

            try { Thread.sleep(100); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
    }

    /** Erstellt einen Snapshot für den UI-Thread (damit kein gleichzeitiger Zugriff auf rollingFrame). */
    private TelemetryModel snapshotOf(TelemetryModel src) {
        TelemetryModel s = new TelemetryModel();
        s.mergeFrom(src);
        return s;
    }

    private void closeSocketQuietly() {
        BluetoothSocket s = socket;
        socket = null;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    // ───────────── UI-thread bridges ─────────────

    private void emitState(final State state, final String detail) {
        if (listener == null) return;
        uiHandler.post(() -> listener.onState(state, detail));
    }

    private void emitTelemetry(final TelemetryModel model) {
        if (listener == null) return;
        uiHandler.post(() -> listener.onTelemetry(model));
    }
}