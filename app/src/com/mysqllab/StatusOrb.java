package com.mysqllab;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Minimalist status indicator emblem with clean concentric ring and crisp power glyph. */
public class StatusOrb extends View {

    public static final int STOPPED = 0, BUSY = 1, RUNNING = 2, ERROR = 3;

    private final Paint outerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private int kind = STOPPED;
    private int color = Ui.MUTED;
    private float phase;
    private ValueAnimator loop;

    public StatusOrb(Context c) {
        super(c);
        outerFill.setStyle(Paint.Style.FILL);
        outerRing.setStyle(Paint.Style.STROKE);
        core.setStyle(Paint.Style.FILL);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setColor(0xFFFFFFFF);
    }

    public void setKind(int newKind, int newColor) {
        this.kind = newKind;
        this.color = newColor;
        updateLoop();
        invalidate();
    }

    private void updateLoop() {
        if (kind == BUSY && isAttachedToWindow()) {
            if (loop == null) {
                loop = ValueAnimator.ofFloat(0f, 1f);
                loop.setDuration(1400);
                loop.setRepeatCount(ValueAnimator.INFINITE);
                loop.setInterpolator(new LinearInterpolator());
                loop.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        phase = (Float) a.getAnimatedValue();
                        invalidate();
                    }
                });
                loop.start();
            }
        } else {
            if (loop != null) {
                loop.cancel();
                loop = null;
            }
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        int d = Ui.dp(getContext(), 96);
        setMeasuredDimension(resolveSize(d, w), resolveSize(d, h));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float R = Math.min(w, h) / 2f;
        float dp1 = Ui.dpf(getContext(), 1);

        outerFill.setColor(Ui.alpha(color, 26));
        c.drawCircle(cx, cy, R * 0.86f, outerFill);

        outerRing.setColor(Ui.alpha(color, 72));
        outerRing.setStrokeWidth(dp1 * 1.5f);
        c.drawCircle(cx, cy, R * 0.86f, outerRing);

        float coreR = R * 0.56f;
        core.setColor(color);
        c.drawCircle(cx, cy, coreR, core);

        if (kind == BUSY) {
            arc.setStrokeWidth(dp1 * 3f);
            arc.setColor(color);
            c.save();
            c.rotate(phase * 360f, cx, cy);
            float ar = R * 0.86f;
            rf.set(cx - ar, cy - ar, cx + ar, cy + ar);
            c.drawArc(rf, 0, 90, false, arc);
            c.restore();
        }

        // Power glyph
        icon.setStrokeWidth(dp1 * 2.5f);
        float ir = coreR * 0.44f;
        rf.set(cx - ir, cy - ir + dp1, cx + ir, cy + ir + dp1);
        c.drawArc(rf, -55, 290, false, icon);
        c.drawLine(cx, cy - ir - dp1 * 1.5f, cx, cy - ir * 0.05f, icon);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
        super.onDetachedFromWindow();
    }
}
