package com.thaiger.h2racing.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

/**
 * Simple Linien-/Flächen-Chart für die Power-History im Post-Run-Screen.
 *
 * Setzt sich rein aus Canvas-Primitives zusammen — keine Lib. Erwartet eine
 * Sample-Liste beliebiger Länge und downsampled bei Bedarf auf ~die Pixel-
 * Breite des Views, damit es nicht zu Overdraw kommt.
 */
public class PowerGraphView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private float[] data = null;
    private float dataMax = 1f;

    public PowerGraphView(Context ctx)                    { super(ctx); init(); }
    public PowerGraphView(Context ctx, AttributeSet a)    { super(ctx, a); init(); }
    public PowerGraphView(Context ctx, AttributeSet a, int s) { super(ctx, a, s); init(); }

    private void init() {
        linePaint.setColor(0xFF3D6FFF);
        linePaint.setStrokeWidth(dp(2.2f));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        fillPaint.setColor(0x333D6FFF);   // semi-transparent blue
        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(0xFF1E2530);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);

        emptyPaint.setColor(0xFF3D4F60);
        emptyPaint.setTextSize(dp(12f));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /**
     * Nimmt alle Samples entgegen und runtersampelt sie auf max. 600 Punkte.
     * Verändert den Eingangs-List nicht.
     */
    public void setData(List<Float> samples) {
        if (samples == null || samples.isEmpty()) {
            data = null;
        } else {
            int target = Math.min(samples.size(), 600);
            data = new float[target];
            float step = (float) samples.size() / target;
            float max = 0f;
            for (int i = 0; i < target; i++) {
                int srcIdx = Math.min((int)(i * step), samples.size() - 1);
                float v = samples.get(srcIdx);
                data[i] = v;
                if (v > max) max = v;
            }
            dataMax = max <= 0 ? 1 : max;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();

        // Horizontale Grid-Lines (Quartile)
        for (int i = 1; i < 4; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        if (data == null || data.length < 2) {
            canvas.drawText("no data", w / 2f, h / 2f + dp(4), emptyPaint);
            return;
        }

        // Path zeichnen: 4% Top-Padding, 4% Bottom-Padding
        linePath.reset();
        fillPath.reset();
        float padTop = h * 0.06f;
        float plotH  = h - padTop - h * 0.02f;

        for (int i = 0; i < data.length; i++) {
            float x = (float) i / (data.length - 1) * w;
            float y = padTop + plotH - (data[i] / dataMax) * plotH;
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, h);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(w, h);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }
}