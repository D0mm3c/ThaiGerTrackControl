package com.thaiger.h2racing.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.thaiger.h2racing.App;
import com.thaiger.h2racing.R;
import com.thaiger.h2racing.model.CarProfile;

/**
 * Screen 1 — Fahrzeugauswahl.
 *
 * Tap auf eine Karte selektiert das Auto. Tap auf CONNECT geht zum
 * Connecting-Screen. Thaiger 7 ist Default.
 */
public class CarSelectActivity extends AppCompatActivity {

    private CarProfile selected = CarProfile.THAIGER_7;
    private CardView cardThaiger;
    private CardView cardBengalo;
    private TextView tvHint;
    private android.widget.Button btnConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_car_select);

        cardThaiger = findViewById(R.id.card_thaiger7);
        cardBengalo = findViewById(R.id.card_bengalo);
        tvHint      = findViewById(R.id.tv_selected_hint);
        btnConnect  = findViewById(R.id.btn_connect);

        cardThaiger.setOnClickListener(v -> select(CarProfile.THAIGER_7));
        cardBengalo.setOnClickListener(v -> select(CarProfile.BENGALO));

        btnConnect.setOnClickListener(v -> proceedToConnect());
        findViewById(R.id.tv_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Default-Auswahl visuell anzeigen
        select(CarProfile.THAIGER_7);
    }

    private void select(CarProfile car) {
        selected = car;
        boolean t7 = car == CarProfile.THAIGER_7;
        // Stroke-only foreground (transparenter Fill, blauer Rand). Nicht-selektierte
        // Karte bekommt null als Foreground, damit der Inhalt voll sichtbar bleibt.
        cardThaiger.setForeground(t7
                ? ContextCompat.getDrawable(this, R.drawable.bg_card_selected_stroke) : null);
        cardBengalo.setForeground(!t7
                ? ContextCompat.getDrawable(this, R.drawable.bg_card_selected_stroke) : null);

        tvHint.setText(car.displayName + " selected · tap CONNECT to pair");
        btnConnect.setText("Connect to " + car.displayName);
    }

    private void proceedToConnect() {
        ((App) getApplication()).setCarProfile(selected);
        startActivity(new Intent(this, ConnectingActivity.class));
    }
}