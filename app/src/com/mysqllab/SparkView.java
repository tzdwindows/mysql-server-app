package com.mysqllab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Minimalist smooth area sparkline for live metrics. */
public class SparkView extends View {

    private static final int MAX = 40;
    private final float[] data = new float[MAX];
    private int count;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path area = new Path();
    private final int color;

    public SparkView(Context c, int color) {
        super(c);
        this.color = color;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dpf(c, 1.8f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(color);
        fill.setStyle(Paint.Style.FILL);
    }

    public void push(float v) {
        if (count == MAX) {
            System.arraycopy(data, 1, data, 0, MAX - 1);
            data[MAX - 1] = v;
        } else {
            data[count++] = v;
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fill.setShader(new LinearGradient(0, 0, 0, h, Ui.alpha(color, 55), Ui.alpha(color, 0), Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float pad = Ui.dpf(getContext(), 3);
        if (count < 2) {
            line.setAlpha(60);
            c.drawLine(pad, h - pad, w - pad, h - pad, line);
            line.setAlpha(255);
            return;
        }
        float max = 1f;
        for (int i = 0; i < count; i++) max = Math.max(max, data[i]);
        float stepX = (w - pad * 2) / (MAX - 1);
        float startX = w - pad - stepX * (count - 1);
        path.reset();
        area.reset();
        float px = 0, py = 0;
        for (int i = 0; i < count; i++) {
            float x = startX + stepX * i;
            float y = h - pad - (h - pad * 2) * (data[i] / max);
            if (i == 0) {
                path.moveTo(x, y);
                area.moveTo(x, h);
                area.lineTo(x, y);
            } else {
                float mx = (px + x) / 2f;
                path.cubicTo(mx, py, mx, y, x, y);
                area.cubicTo(mx, py, mx, y, x, y);
            }
            px = x;
            py = y;
        }
        area.lineTo(px, h);
        area.close();
        c.drawPath(area, fill);
        c.drawPath(path, line);
        c.drawCircle(px, py, Ui.dpf(getContext(), 2.8f), line);
    }
}
