package com.thaiger.h2racing.util;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Haptisches und akustisches Feedback bei kritischen Alerts.
 *
 * Beide Wege sind durch Settings gegated: nur wenn der User Vibrate /
 * Alert Sound eingeschaltet hat, wird tatsächlich ausgelöst.
 */
public final class AlertFx {

    private final Context ctx;
    private final Prefs   prefs;
    private ToneGenerator tone;
    private long lastFiredAtMs = 0;

    /** Re-trigger-Schutz: zwischen zwei Pulsen mindestens dieser Abstand. */
    private static final long MIN_INTERVAL_MS = 2500L;

    public AlertFx(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = new Prefs(ctx);
    }

    /** Einmal feuern (Vibration + optional Ton), wenn Prefs es erlauben und Cooldown vorbei. */
    public void fire() {
        long now = System.currentTimeMillis();
        if (now - lastFiredAtMs < MIN_INTERVAL_MS) return;
        lastFiredAtMs = now;

        if (prefs.isVibrate())    vibrate();
        if (prefs.isAlertSound()) beep();
    }

    /** Direkt Resourcen freigeben — vom Dashboard onDestroy aus. */
    public void release() {
        try {
            if (tone != null) { tone.release(); tone = null; }
        } catch (Throwable ignored) {}
    }

    private void vibrate() {
        try {
            Vibrator vib;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vib = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vib == null || !vib.hasVibrator()) return;
            // 250ms-Puls reicht — der Fahrer trägt Handschuhe, kurz und kräftig.
            vib.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Throwable ignored) {}
    }

    private void beep() {
        try {
            if (tone == null) {
                tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            }
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
        } catch (Throwable ignored) {}
    }
}
