package com.mysqllab;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Minimalist Claude-style navigation rail / bottom bar with warm terracotta selection pill. */
public class NavBar extends LinearLayout {

    public interface Listener {
        void onSelect(int index);
    }

    private static class Item extends LinearLayout {
        final IconView icon;
        final TextView label;

        Item(Context c, int iconType, String text) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setClickable(true);
            setFocusable(true);
            icon = new IconView(c, iconType, Ui.TEXT2);
            addView(icon, Ui.lp(Ui.dp(c, 22), Ui.dp(c, 22)));
            label = Ui.label(c, text, 11f, Ui.TEXT2, true);
            LinearLayout.LayoutParams lp = Ui.lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(c, 3);
            addView(label, lp);
            float r = Ui.dpf(c, 10);
            StateListDrawable sld = new StateListDrawable();
            sld.addState(new int[]{android.R.attr.state_focused},
                    Ui.rect(0x14D97757, r, Ui.ACCENT, Math.max(2, Ui.dp(c, 2))));
            sld.addState(new int[]{}, Ui.rect(0x00000000, r, 0, 0));
            setBackground(new RippleDrawable(ColorStateList.valueOf(0x18141413), sld, Ui.rect(0xFFFFFFFF, r, 0, 0)));
            Ui.hand(this);
            setTooltipText(text);
        }

        void setFraction(float f) {
            icon.setColor(Ui.mix(Ui.TEXT2, Ui.ACCENT, f));
            label.setTextColor(Ui.mix(Ui.TEXT2, Ui.TEXT, f));
        }
    }

    private final boolean vertical;
    private final Item[] items;
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private float pos;
    private int selected;
    private ValueAnimator anim;
    private Listener listener;

    public NavBar(Context c, boolean vertical, String[] labels, int[] icons) {
        super(c);
        this.vertical = vertical;
        setOrientation(vertical ? VERTICAL : HORIZONTAL);
        setWillNotDraw(false);
        setGravity(vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        pill.setStyle(Paint.Style.FILL);
        pill.setColor(0x1FD97757);
        pillStroke.setStyle(Paint.Style.STROKE);
        pillStroke.setStrokeWidth(Math.max(1, Ui.dp(c, 1)));
        pillStroke.setColor(0x4DD97757);
        items = new Item[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Item it = new Item(c, icons[i], labels[i]);
            it.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx, true);
                    if (listener != null) listener.onSelect(idx);
                }
            });
            items[i] = it;
            LinearLayout.LayoutParams lp;
            if (vertical) {
                lp = Ui.lp(Ui.dp(c, 62), Ui.dp(c, 48));
                lp.topMargin = Ui.dp(c, 2);
                lp.bottomMargin = Ui.dp(c, 2);
            } else {
                lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 50), 1f);
                lp.leftMargin = Ui.dp(c, 3);
                lp.rightMargin = Ui.dp(c, 3);
            }
            addView(it, lp);
        }
        applyFractions();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public int getSelected() {
        return selected;
    }

    public void select(int index, boolean animate) {
        if (index < 0 || index >= items.length) return;
        selected = index;
        if (anim != null) anim.cancel();
        if (!animate || getWidth() == 0) {
            pos = index;
            applyFractions();
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(pos, index);
        anim.setDuration(240);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                pos = (Float) a.getAnimatedValue();
                applyFractions();
                invalidate();
            }
        });
        anim.start();
    }

    private void applyFractions() {
        for (int i = 0; i < items.length; i++) {
            float f = Math.max(0f, 1f - Math.abs(pos - i));
            items[i].setFraction(f);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && (anim == null || !anim.isRunning())) {
            pos = selected;
            applyFractions();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (items.length > 0 && items[0].getWidth() > 0) {
            int i0 = Math.max(0, Math.min(items.length - 1, (int) Math.floor(pos)));
            int i1 = Math.min(items.length - 1, i0 + 1);
            float f = pos - i0;
            View a = items[i0];
            View b = items[i1];
            float left = a.getLeft() + (b.getLeft() - a.getLeft()) * f;
            float top = a.getTop() + (b.getTop() - a.getTop()) * f;
            float right = a.getRight() + (b.getRight() - a.getRight()) * f;
            float bottom = a.getBottom() + (b.getBottom() - a.getBottom()) * f;
            rf.set(left, top, right, bottom);
            float rad = Ui.dpf(getContext(), 10);
            canvas.drawRoundRect(rf, rad, rad, pill);
            canvas.drawRoundRect(rf, rad, rad, pillStroke);
        }
        super.dispatchDraw(canvas);
    }
}
