package com.thaiger.h2racing.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.thaiger.h2racing.R;

/**
 * Screen 4 — Full-Screen-Alert (Overlay).
 *
 * Wird derzeit nicht standardmäßig aufgerufen — das eingebettete Alert-Banner
 * in DashboardActivity ist die Default-UX. Diese Activity bleibt als Variante
 * für besonders kritische Alerts erhalten, wenn der Banner unzureichend wirkt.
 *
 * Intent-Extras:
 *   - "alert_type"      String,  z.B. "FC TEMPERATURE"
 *   - "alert_value"     String,  z.B. "71"
 *   - "alert_unit"      String,  z.B. "°C"
 *   - "alert_threshold" String,  z.B. "Threshold: 70°C"
 */
public class AlertActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE      = "alert_type";
    public static final String EXTRA_VALUE     = "alert_value";
    public static final String EXTRA_UNIT      = "alert_unit";
    public static final String EXTRA_THRESHOLD = "alert_threshold";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert);

        TextView type      = findViewById(R.id.tv_alert_type);
        TextView value     = findViewById(R.id.tv_alert_value);
        TextView unit      = findViewById(R.id.tv_alert_unit);
        TextView threshold = findViewById(R.id.tv_alert_threshold);

        type.setText(getIntent().getStringExtra(EXTRA_TYPE));
        value.setText(getIntent().getStringExtra(EXTRA_VALUE));
        unit.setText(getIntent().getStringExtra(EXTRA_UNIT));
        threshold.setText(getIntent().getStringExtra(EXTRA_THRESHOLD));

        findViewById(R.id.btn_alert_acknowledge).setOnClickListener(v -> finish());
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
