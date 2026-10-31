package com.mysqllab;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Claude-inspired warm minimalist design tokens and view helpers.
 * Warm ivory paper background, crisp 1dp stone borders, deep charcoal ink, and terracotta accents.
 * Author: tzdwindows 7
 */
public final class Ui {
    private Ui() {}

    // Claude Warm Minimalist Palette
    public static final int BG = 0xFFFAF9F5;           // Warm Ivory Paper
    public static final int BG0 = 0xFFFAF9F5;
    public static final int BG2 = 0xFFF2EFE9;          // Warm Stone Surface (code / subtle fills)
    public static final int CARD = 0xFFFFFFFF;         // Crisp White Card
    public static final int GLASS = 0xFFFFFFFF;
    public static final int GLASS_STRONG = 0xFFF2EFE9;
    public static final int STROKE = 0xFFE4E1DA;       // Subtle Warm Border
    public static final int STROKE_STRONG = 0xFFD1CEC5;

    public static final int TEXT = 0xFF141413;         // Deep Charcoal Ink
    public static final int TEXT2 = 0xFF66655F;        // Muted Warm Slate
    public static final int MUTED = 0xFF9B9991;        // Subtle Stone Gray

    public static final int ACCENT = 0xFFD97757;       // Claude Terracotta Coral
    public static final int CYAN = 0xFFD97757;         // Mapped to Claude Terracotta
    public static final int VIOLET = 0xFF7C6F64;       // Warm Umber
    public static final int PINK = 0xFFC86D51;
    public static final int GREEN = 0xFF2E7D5B;        // Calm Sage Green
    public static final int RED = 0xFFC94A4A;          // Brick Red
    public static final int AMBER = 0xFFB8732A;        // Warm Ochre
    public static final int BLUE = 0xFF3B6E8C;         // Editorial Slate Blue

    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static float dpf(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static float sp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().scaledDensity;
    }

    public static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    public static int mix(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        return (Integer) ARGB.evaluate(t, a, b);
    }

    public static GradientDrawable rect(int fill, float radiusPx, int stroke, int strokePx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0) d.setStroke(strokePx, stroke);
        return d;
    }

    public static GradientDrawable grad(int c1, int c2, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(c1);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable grad(int[] colors, float radiusPx, int stroke, int strokePx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(colors != null && colors.length > 0 ? colors[0] : CARD);
        d.setCornerRadius(radiusPx);
        if (strokePx > 0) d.setStroke(strokePx, stroke);
        return d;
    }

    public static GradientDrawable oval(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    /** Hardware-enforced rounded corner clipping so child views never poke square corners out. */
    public static void clipRound(View v, final float radiusDp) {
        if (Build.VERSION.SDK_INT >= 21) {
            final float r = dpf(v.getContext(), radiusDp);
            v.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    int w = view.getWidth();
                    int h = view.getHeight();
                    if (w > 0 && h > 0) {
                        outline.setRoundRect(0, 0, w, h, r);
                    }
                }
            });
            v.setClipToOutline(true);
        }
    }

    public static TextView label(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(true);
        return t;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpw(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
    }

    public static LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    public static void hand(View v) {
        if (Build.VERSION.SDK_INT >= 24) {
            v.setPointerIcon(PointerIcon.getSystemIcon(v.getContext(), PointerIcon.TYPE_HAND));
        }
    }

    public static void reveal(ViewGroup g, long baseDelay) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            v.animate().cancel();
            v.setAlpha(1f);
            v.setTranslationY(0f);
        }
    }

    public static void pop(View v) {
        v.animate().cancel();
        v.setAlpha(1f);
        v.setScaleX(1f);
        v.setScaleY(1f);
    }

    public static void shake(View v) {
        float d = dpf(v.getContext(), 5);
        v.animate().cancel();
        v.setTranslationX(0f);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(300);
        final View target = v;
        final float amp = d;
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator an) {
                float f = (Float) an.getAnimatedValue();
                target.setTranslationX((float) (Math.sin(f * Math.PI * 4) * amp * (1f - f)));
            }
        });
        a.start();
    }

    public static void expand(final View v) {
        v.setVisibility(View.VISIBLE);
        v.setAlpha(1f);
    }

    public static void collapse(final View v) {
        v.setVisibility(View.GONE);
    }
}
