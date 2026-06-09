package com.thaiger.h2racing.relay;

import com.thaiger.h2racing.model.TelemetryModel;

/**
 * Entkopplungs-Interface zwischen {@link com.thaiger.h2racing.bt.BluetoothService}
 * und {@link MqttRelayService}.
 *
 * BluetoothService ruft {@link #onFrame(TelemetryModel)} auf dem BT-Binder-Thread
 * auf. Die Implementierung (MqttRelayService) enqueued das Frame sofort und kehrt
 * zurück — kein Blocking, kein UI-Zugriff.
 */
public interface FrameRelay {
    void onFrame(TelemetryModel m);
}