package com.mysqllab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Interactive data-grid row supporting individual cell click-to-copy,
 * row long-press copy, and smooth scrolling through the ListView.
 */
public class RowView extends View {

    public interface RowInteractionListener {
        void onCellClick(int rowIndex, int colIndex, String value);
        void onRowLongClick(int rowIndex);
    }

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint();
    private final Paint line = new Paint();
    private final float pad;
    private final int rowH;
    private final int headH;
    private String[] cells = new String[0];
    private int[] widths = new int[0];
    private boolean[] numeric = new boolean[0];
    private boolean header;
    private int index;
    private boolean hovered;
    private float hoverX;

    private RowInteractionListener interactionListener;
    private final GestureDetector gestureDetector;
    private float downX, downY;
    private final int touchSlop;

    public RowView(final Context c) {
        super(c);
        pad = Ui.dpf(c, 14);
        rowH = Ui.dp(c, 42);
        headH = Ui.dp(c, 44);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();

        float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 15f, c.getResources().getDisplayMetrics());
        text.setTextSize(textSizePx);
        text.setTypeface(Typeface.MONOSPACE);
        line.setColor(0x24FFFFFF);
        line.setStrokeWidth(Math.max(1, Ui.dp(c, 1)));
        setClickable(true);
        setFocusable(false);

        gestureDetector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                // Must return true, otherwise Android discards all subsequent tap/long-press events!
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (header) return false;
                int col = columnAt(e.getX());
                if (col >= 0 && col < cells.length && interactionListener != null) {
                    interactionListener.onCellClick(index, col, cells[col]);
                    return true;
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (header) return;
                if (interactionListener != null) {
                    interactionListener.onRowLongClick(index);
                }
            }
        });
    }

    public static Paint measurePaint(Context c, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 15f, c.getResources().getDisplayMetrics());
        p.setTextSize(textSizePx);
        p.setTypeface(bold ? Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) : Typeface.MONOSPACE);
        return p;
    }

    public void setInteractionListener(RowInteractionListener listener) {
        this.interactionListener = listener;
    }

    public void bind(String[] cells, int[] widths, boolean[] numeric, boolean header, int index) {
        this.cells = cells;
        this.widths = widths;
        this.numeric = numeric;
        this.header = header;
        this.index = index;
        this.hovered = false;
        requestLayout();
        invalidate();
    }

    public int getRowIndex() {
        return index;
    }

    private int columnAt(float x) {
        float acc = 0;
        for (int i = 0; i < widths.length; i++) {
            acc += widths[i];
            if (x < acc) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (header) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                gestureDetector.onTouchEvent(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (dy > touchSlop || dx > touchSlop) {
                    // Hand over to parent (ListView or HorizontalScrollView) for scrolling!
                    return false;
                }
                break;
        }

        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = 0;
        for (int x : widths) w += x;
        setMeasuredDimension(w, header ? headH : rowH);
    }

    @Override
    public boolean onHoverEvent(MotionEvent e) {
        if (header) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                hoverX = e.getX();
                if (!hovered) {
                    hovered = true;
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                hovered = false;
                invalidate();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (header) {
            bg.setColor(0x4D1E293B);
        } else if (hovered) {
            bg.setColor(0x3322D3EE);
        } else {
            bg.setColor(index % 2 == 0 ? 0x00000000 : 0x14FFFFFF);
        }
        c.drawRect(0, 0, w, h, bg);

        if (header) {
            line.setColor(0x6606B6D4);
            line.setStrokeWidth(Math.max(1, Ui.dp(getContext(), 1.5f)));
            c.drawLine(0, h - 1, w, h - 1, line);
            line.setColor(0x24FFFFFF);
            line.setStrokeWidth(Math.max(1, Ui.dp(getContext(), 1)));
        } else {
            c.drawLine(0, h - 1, w, h - 1, line);
        }

        Paint.FontMetrics fm = text.getFontMetrics();
        float base = (h - (fm.descent + fm.ascent)) / 2f;
        float x0 = 0;

        for (int i = 0; i < widths.length && i < cells.length; i++) {
            int cw = widths[i];
            String s = cells[i];
            c.save();
            c.clipRect(x0, 0, x0 + cw - 2, h);

            if (header) {
                text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                text.setColor(Ui.CYAN);
            } else if ("NULL".equals(s)) {
                text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC));
                text.setColor(0xFF94A3B8);
            } else {
                text.setTypeface(Typeface.MONOSPACE);
                text.setColor(numeric.length > i && numeric[i] ? 0xFFFDE68A : 0xFFF8FAFC);
            }

            float tx = x0 + pad;
            c.drawText(s, tx, base, text);
            c.restore();

            x0 += cw;
            c.drawLine(x0 - 1, h * 0.15f, x0 - 1, h * 0.85f, line);
        }
    }
}
