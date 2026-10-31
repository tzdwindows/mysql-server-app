package com.mysqllab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Vector icons drawn with canvas primitives (no image assets needed). */
public class IconView extends View {

    public static final int SERVER = 0, TERMINAL = 1, DATABASE = 2, INFO = 3, PLAY = 4, STOP = 5,
            REFRESH = 6, TRASH = 7, TABLE = 8, HISTORY = 9, COPY = 10, BOLT = 11, CHEVRON_RIGHT = 12,
            CHEVRON_DOWN = 13, CLOSE = 14, USER = 15, CHIP = 16, POWER = 17, CHECK = 18, ALERT = 19,
            PLUS = 20, KEYBOARD = 21, MOUSE = 22, VIEW = 23, CODE = 24;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private int type;
    private int color;

    public IconView(Context c, int type, int color) {
        super(c);
        this.type = type;
        this.color = color;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(2f);
        fill.setStyle(Paint.Style.FILL);
        applyColor();
    }

    public void setIcon(int type) {
        this.type = type;
        invalidate();
    }

    public void setColor(int color) {
        this.color = color;
        applyColor();
        invalidate();
    }

    private void applyColor() {
        stroke.setColor(color);
        fill.setColor(color);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int def = Ui.dp(getContext(), 24);
        int w = resolveSize(def, wSpec);
        int h = resolveSize(def, hSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        c.scale(s, s);
        draw24(c);
        c.restore();
    }

    private void draw24(Canvas c) {
        Path p = path;
        p.reset();
        switch (type) {
            case SERVER:
                rf.set(3, 4, 21, 10);
                c.drawRoundRect(rf, 2, 2, stroke);
                rf.set(3, 14, 21, 20);
                c.drawRoundRect(rf, 2, 2, stroke);
                c.drawCircle(7, 7, 1f, fill);
                c.drawCircle(7, 17, 1f, fill);
                c.drawLine(11, 7, 17, 7, stroke);
                c.drawLine(11, 17, 17, 17, stroke);
                break;
            case TERMINAL:
                rf.set(3, 4, 21, 20);
                c.drawRoundRect(rf, 3, 3, stroke);
                p.moveTo(7, 9); p.lineTo(11, 12); p.lineTo(7, 15);
                c.drawPath(p, stroke);
                c.drawLine(13, 15.5f, 17, 15.5f, stroke);
                break;
            case DATABASE:
                rf.set(5, 3, 19, 8);
                c.drawOval(rf, stroke);
                p.moveTo(5, 5.5f); p.lineTo(5, 18.5f);
                rf.set(5, 16, 19, 21);
                p.arcTo(rf, 180, -180);
                p.lineTo(19, 5.5f);
                c.drawPath(p, stroke);
                p.reset();
                rf.set(5, 8.5f, 19, 13.5f);
                p.arcTo(rf, 180, -180);
                c.drawPath(p, stroke);
                p.reset();
                rf.set(5, 13, 19, 18);
                p.arcTo(rf, 180, -180);
                c.drawPath(p, stroke);
                break;
            case INFO:
                c.drawCircle(12, 12, 9, stroke);
                c.drawLine(12, 11, 12, 16.5f, stroke);
                c.drawCircle(12, 7.6f, 1.1f, fill);
                break;
            case PLAY:
                p.moveTo(8, 5.5f); p.lineTo(18.5f, 12); p.lineTo(8, 18.5f); p.close();
                c.drawPath(p, fill);
                c.drawPath(p, stroke);
                break;
            case STOP:
                rf.set(6.5f, 6.5f, 17.5f, 17.5f);
                c.drawRoundRect(rf, 2.5f, 2.5f, fill);
                break;
            case REFRESH:
                rf.set(4.5f, 4.5f, 19.5f, 19.5f);
                c.drawArc(rf, -60, 300, false, stroke);
                p.moveTo(19.5f, 3.5f); p.lineTo(19.5f, 8.5f); p.lineTo(14.5f, 8.5f);
                c.drawPath(p, stroke);
                break;
            case TRASH:
                c.drawLine(4, 7, 20, 7, stroke);
                p.moveTo(9, 7); p.lineTo(9, 4); p.lineTo(15, 4); p.lineTo(15, 7);
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(6, 7); p.lineTo(7, 20); p.lineTo(17, 20); p.lineTo(18, 7);
                c.drawPath(p, stroke);
                c.drawLine(10, 11, 10, 16, stroke);
                c.drawLine(14, 11, 14, 16, stroke);
                break;
            case TABLE:
                rf.set(3, 5, 21, 19);
                c.drawRoundRect(rf, 2.5f, 2.5f, stroke);
                c.drawLine(3, 10, 21, 10, stroke);
                c.drawLine(3, 14.5f, 21, 14.5f, stroke);
                c.drawLine(10, 5, 10, 19, stroke);
                break;
            case HISTORY:
                c.drawCircle(12, 12, 9, stroke);
                p.moveTo(12, 7); p.lineTo(12, 12); p.lineTo(15.5f, 14);
                c.drawPath(p, stroke);
                break;
            case COPY:
                rf.set(8.5f, 8.5f, 20, 20);
                c.drawRoundRect(rf, 2.5f, 2.5f, stroke);
                p.moveTo(15.5f, 8.5f); p.lineTo(15.5f, 6.5f); p.quadTo(15.5f, 4, 13, 4);
                p.lineTo(6.5f, 4); p.quadTo(4, 4, 4, 6.5f); p.lineTo(4, 13); p.quadTo(4, 15.5f, 6.5f, 15.5f);
                p.lineTo(8.5f, 15.5f);
                c.drawPath(p, stroke);
                break;
            case BOLT:
                p.moveTo(13.5f, 2.5f); p.lineTo(5, 13.5f); p.lineTo(11, 13.5f); p.lineTo(10, 21.5f);
                p.lineTo(19, 10); p.lineTo(13, 10); p.close();
                c.drawPath(p, stroke);
                break;
            case CHEVRON_RIGHT:
                p.moveTo(9, 5.5f); p.lineTo(15.5f, 12); p.lineTo(9, 18.5f);
                c.drawPath(p, stroke);
                break;
            case CHEVRON_DOWN:
                p.moveTo(5.5f, 9); p.lineTo(12, 15.5f); p.lineTo(18.5f, 9);
                c.drawPath(p, stroke);
                break;
            case CLOSE:
                c.drawLine(6, 6, 18, 18, stroke);
                c.drawLine(18, 6, 6, 18, stroke);
                break;
            case USER:
                c.drawCircle(12, 8, 4, stroke);
                rf.set(4.5f, 14, 19.5f, 27);
                c.drawArc(rf, 200, 140, false, stroke);
                break;
            case CHIP:
                rf.set(6.5f, 6.5f, 17.5f, 17.5f);
                c.drawRoundRect(rf, 2, 2, stroke);
                rf.set(10, 10, 14, 14);
                c.drawRect(rf, fill);
                for (int i = 0; i < 3; i++) {
                    float o = 9.5f + i * 2.5f;
                    c.drawLine(o, 3, o, 6.5f, stroke);
                    c.drawLine(o, 17.5f, o, 21, stroke);
                    c.drawLine(3, o, 6.5f, o, stroke);
                    c.drawLine(17.5f, o, 21, o, stroke);
                }
                break;
            case POWER:
                rf.set(5.5f, 5.5f, 18.5f, 18.5f);
                c.drawArc(rf, -55, 290, false, stroke);
                c.drawLine(12, 3, 12, 11.5f, stroke);
                break;
            case CHECK:
                p.moveTo(5, 12.5f); p.lineTo(10, 17.5f); p.lineTo(19, 7);
                c.drawPath(p, stroke);
                break;
            case ALERT:
                p.moveTo(12, 3.5f); p.lineTo(21.5f, 20); p.lineTo(2.5f, 20); p.close();
                c.drawPath(p, stroke);
                c.drawLine(12, 9.5f, 12, 14.5f, stroke);
                c.drawCircle(12, 17.2f, 1f, fill);
                break;
            case PLUS:
                c.drawLine(12, 5, 12, 19, stroke);
                c.drawLine(5, 12, 19, 12, stroke);
                break;
            case KEYBOARD:
                rf.set(2.5f, 6, 21.5f, 18);
                c.drawRoundRect(rf, 2.5f, 2.5f, stroke);
                for (int i = 0; i < 4; i++) c.drawCircle(6.5f + i * 3.7f, 10, 0.9f, fill);
                for (int i = 0; i < 3; i++) c.drawCircle(8 + i * 4f, 13.3f, 0.9f, fill);
                c.drawLine(8, 16, 16, 16, stroke);
                break;
            case MOUSE:
                rf.set(6.5f, 3, 17.5f, 21);
                c.drawRoundRect(rf, 5.5f, 5.5f, stroke);
                c.drawLine(12, 3.5f, 12, 10, stroke);
                break;
            case VIEW:
                rf.set(3, 5, 21, 19);
                c.drawRoundRect(rf, 2.5f, 2.5f, stroke);
                c.drawLine(3, 10, 21, 10, stroke);
                break;
            case CODE:
                p.moveTo(8, 7); p.lineTo(3, 12); p.lineTo(8, 17);
                c.drawPath(p, stroke);
                p.reset();
                p.moveTo(16, 7); p.lineTo(21, 12); p.lineTo(16, 17);
                c.drawPath(p, stroke);
                c.drawLine(13.5f, 5, 10.5f, 19, stroke);
                break;
            default:
                break;
        }
    }
}
