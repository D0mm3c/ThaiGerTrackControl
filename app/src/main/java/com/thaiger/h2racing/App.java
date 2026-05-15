package com.thaiger.h2racing;

import android.app.Application;

import com.thaiger.h2racing.bt.BluetoothService;
import com.thaiger.h2racing.model.CarProfile;
import com.thaiger.h2racing.model.RunStats;

/**
 * Process-globaler Halter für Dienste, die über mehrere Activities leben.
 *
 * Ein Bound Service wäre der "lehrbuchsaubere" Weg, aber für einen Cockpit-
 * Prototyp ist die Application-Klasse pragmatischer:
 *   - kein Binder-Boilerplate
 *   - Activities greifen direkt mit {@code (App)getApplication()} drauf zu
 *   - BluetoothService lebt vom Connecting-Screen bis zum Post-Run-Screen
 */
public class App extends Application {

    /** Singleton-BluetoothService. Wird von ConnectingActivity erzeugt. */
    private BluetoothService bluetoothService;

    /** Aktuell gewählter Wagen — gesetzt von CarSelectActivity. */
    private CarProfile carProfile = CarProfile.THAIGER_7;

    /** Aktuelle Run-Statistik — gesetzt von ConnectingActivity bei Session-Start. */
    private RunStats runStats;

    public BluetoothService getBluetoothService()              { return bluetoothService; }
    public void setBluetoothService(BluetoothService service)  { this.bluetoothService = service; }

    public CarProfile getCarProfile()                          { return carProfile; }
    public void setCarProfile(CarProfile profile)              { this.carProfile = profile; }

    public RunStats getRunStats()                              { return runStats; }
    public void setRunStats(RunStats stats)                    { this.runStats = stats; }
}