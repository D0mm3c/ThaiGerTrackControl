package com.thaiger.h2racing.relay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.thaiger.h2racing.model.CarProfile;
import com.thaiger.h2racing.model.TelemetryModel;
import com.thaiger.h2racing.util.Prefs;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Streamt Telemetrie-Frames per MQTT an den Engineer-Laptop.
 *
 * Architektur:
 *   BLE-Callback-Thread → onFrame() → BlockingQueue (50 Slots, drop-oldest)
 *                                          │
 *                                   RelayThread (JSON encode → Paho.publish)
 *
 * Threading:
 *   - {@link #onFrame} wird auf dem BLE-Binder-Thread aufgerufen, enqueued sofort.
 *   - Alles Netzwerk-relevante läuft auf dem "MQTT-Relay"-Thread.
 *   - State-Updates werden auf dem UI-Thread dispatched.
 *
 * Reconnect-Strategie: Exponential-Backoff 1 → 2 → 4 → 8 → 16s (max).
 *
 * TLS: funktioniert out-of-the-box mit dem Android JVM TrustStore
 * (HiveMQ Cloud nutzt DigiCert / Let's Encrypt — sind in Android enthalten).
 */
public class MqttRelayService implements FrameRelay {

    private static final String TAG = "MqttRelay";
    private static final int    QUEUE_CAPACITY   = 50;
    private static final long   BACKOFF_INIT_MS  = 1_000;
    private static final long   BACKOFF_MAX_MS   = 16_000;

    public enum State { IDLE, CONNECTING, CONNECTED, RECONNECTING, DISABLED, FAILED, STOPPED }

    public interface StateListener {
        void onRelayState(State state, String detail);
    }

    private final Prefs       prefs;
    private final CarProfile  car;
    private final Handler     uiHandler = new Handler(Looper.getMainLooper());

    private volatile boolean       running = false;
    private volatile StateListener stateListener;
    private volatile State         currentState = State.IDLE;

    /** Bounded queue — drop-oldest when full (burst protection). */
    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    /** Throttle: minimum interval between enqueued frames. */
    private final long minIntervalMs;
    private long lastEnqueuedAtMs = 0;

    private Thread relayThread;

    public MqttRelayService(Context ctx, CarProfile car) {
        this.prefs       = new Prefs(ctx);
        this.car         = car;
        this.minIntervalMs = 1000L / Math.max(1, prefs.getMqttRateHz());
    }

    // ─── Public API ───

    public void start() {
        if (!prefs.isMqttEnabled()) {
            setState(State.DISABLED, "Relay disabled in settings");
            return;
        }
        if (prefs.getMqttHost().isEmpty()) {
            setState(State.FAILED, "Broker host not configured");
            return;
        }
        if (running) return;
        running = true;
        relayThread = new Thread(this::runLoop, "MQTT-Relay");
        relayThread.setDaemon(true);
        relayThread.start();
    }

    public void stop() {
        running = false;
        if (relayThread != null) {
            relayThread.interrupt();
            relayThread = null;
        }
        setState(State.STOPPED, "Stopped");
    }

    public void setStateListener(StateListener l) {
        stateListener = l;
        // Replay current state so the UI is immediately correct after attach
        StateListener s = stateListener;
        if (s != null) s.onRelayState(currentState, "");
    }

    public State getState() { return currentState; }

    // ─── FrameRelay ───

    @Override
    public void onFrame(TelemetryModel m) {
        if (!running || currentState != State.CONNECTED) return;
        long now = System.currentTimeMillis();
        if (now - lastEnqueuedAtMs < minIntervalMs) return;   // throttle
        lastEnqueuedAtMs = now;

        String json = TelemetryJsonEncoder.encode(m);
        // drop-oldest semantics: if queue is full, remove head first
        while (!queue.offer(json)) {
            queue.poll();
        }
    }

    // ─── Relay thread ───

    private void runLoop() {
        String host     = prefs.getMqttHost();
        int    port     = prefs.getMqttPort();
        boolean useTls  = prefs.isMqttUseTls();
        String user     = prefs.getMqttUsername();
        String pass     = prefs.getMqttPassword();
        String topic    = "thaiger/" + car.id + "/telemetry";
        // Unique client ID — allows two phones to publish simultaneously
        String clientId = "thaiger-" + car.id + "-" + UUID.randomUUID().toString().substring(0, 8);
        String brokerUri = (useTls ? "ssl://" : "tcp://") + host + ":" + port;

        long backoff = BACKOFF_INIT_MS;

        while (running) {
            setState(State.CONNECTING, host);
            MqttClient client = null;
            try {
                client = new MqttClient(brokerUri, clientId, new MemoryPersistence());

                MqttConnectOptions opts = new MqttConnectOptions();
                opts.setCleanSession(true);
                opts.setAutomaticReconnect(false);  // we handle reconnect ourselves
                opts.setConnectionTimeout(10);
                opts.setKeepAliveInterval(30);
                if (!user.isEmpty()) opts.setUserName(user);
                if (!pass.isEmpty()) opts.setPassword(pass.toCharArray());
                // TLS: Android JVM trust store covers HiveMQ Cloud / Let's Encrypt / DigiCert
                // No custom SSLSocketFactory needed.

                client.connect(opts);
                setState(State.CONNECTED, host);
                backoff = BACKOFF_INIT_MS;  // reset on success

                // Publish loop
                while (running && client.isConnected()) {
                    String json = queue.poll(1, TimeUnit.SECONDS);
                    if (json == null) continue;  // timeout, loop
                    MqttMessage msg = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
                    msg.setQos(0);        // fire-and-forget — race telemetry tolerates loss
                    msg.setRetained(false);
                    client.publish(topic, msg);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (MqttException e) {
                Log.w(TAG, "MQTT error: " + e.getReasonCode() + " " + e.getMessage());
            } catch (Throwable t) {
                Log.e(TAG, "Relay error: " + t.getMessage());
            } finally {
                if (client != null) {
                    try { client.disconnect(); } catch (Exception ignored) {}
                    try { client.close(); }      catch (Exception ignored) {}
                }
            }

            if (!running) break;

            setState(State.RECONNECTING, "Reconnect in " + (backoff / 1000) + "s…");
            try { Thread.sleep(backoff); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
        }
        setState(State.STOPPED, "");
    }

    private void setState(State state, String detail) {
        currentState = state;
        StateListener l = stateListener;
        if (l != null) {
            uiHandler.post(() -> {
                StateListener l2 = stateListener;
                if (l2 != null) l2.onRelayState(state, detail);
            });
        }
    }
}