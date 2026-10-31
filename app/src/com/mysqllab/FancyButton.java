package com.mysqllab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Minimalist Claude-style tactile button:
 * - PRIMARY: Terracotta coral (#D97757) with crisp white text
 * - SUCCESS: Calm sage green (#2E7D5B) with crisp white text
 * - DANGER: Subtle warm rose surface with brick red text & border (#C94A4A)
 * - SECONDARY: Warm stone surface (#F2EFE9) with 1dp border (#DDD9D0) and charcoal ink text (#141413)
 * - GHOST: Clean transparent button with muted slate text (#66655F)
 */
public class FancyButton extends LinearLayout {

    public static final int PRIMARY = 0, SECONDARY = 1, DANGER = 2, GHOST = 3, SUCCESS = 4;

    private final IconView icon;
    private final TextView text;
    private int style;

    public FancyButton(Context c, String label, int iconType, int style) {
        super(c);
        this.style = style;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setClickable(true);
        setFocusable(true);
        int padH = Ui.dp(c, label == null || label.length() == 0 ? 10 : 14);
        int padV = Ui.dp(c, 8);
        setPadding(padH, padV, padH, padV);

        int fg = resolveForeground(style);

        icon = new IconView(c, iconType < 0 ? IconView.INFO : iconType, fg);
        if (iconType >= 0) {
            addView(icon, Ui.lp(Ui.dp(c, 16), Ui.dp(c, 16)));
        }
        text = Ui.label(c, label == null ? "" : label, 13.5f, fg, true);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        if (label != null && label.length() > 0) {
            LinearLayout.LayoutParams lp = Ui.lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            if (iconType >= 0) lp.leftMargin = Ui.dp(c, 6);
            addView(text, lp);
        }
        setBackground(buildBackground(c));
        Ui.hand(this);
        setMinimumHeight(Ui.dp(c, 38));
    }

    private static int resolveForeground(int st) {
        switch (st) {
            case PRIMARY:
            case SUCCESS:
                return 0xFFFFFFFF;
            case DANGER:
                return Ui.RED;
            case GHOST:
                return Ui.TEXT2;
            default:
                return Ui.TEXT;
        }
    }

    public void setLabel(String s) {
        text.setText(s);
    }

    public void setIcon(int type) {
        icon.setIcon(type);
    }

    public void setTooltip(String s) {
        setTooltipText(s);
    }

    public void setCompact(boolean compact) {
        Context c = getContext();
        String lbl = text.getText().toString();
        int padH = Ui.dp(c, lbl.length() == 0 ? 8 : (compact ? 10 : 14));
        int padV = Ui.dp(c, compact ? 5 : 8);
        setPadding(padH, padV, padH, padV);
        text.setTextSize(compact ? 12.5f : 13.5f);
        setMinimumHeight(Ui.dp(c, compact ? 32 : 38));
    }

    public void setStyle(int newStyle) {
        this.style = newStyle;
        int fg = resolveForeground(style);
        icon.setColor(fg);
        text.setTextColor(fg);
        setBackground(buildBackground(getContext()));
    }

    private android.graphics.drawable.Drawable buildBackground(Context c) {
        float r = Ui.dpf(c, 10);
        int sw1 = Math.max(1, Ui.dp(c, 1));
        int sw2 = Math.max(2, Ui.dp(c, 2));
        GradientDrawable normal;
        GradientDrawable pressed;
        GradientDrawable focused;
        int rippleColor = 0x1F141413;
        switch (style) {
            case PRIMARY:
                normal = Ui.rect(Ui.ACCENT, r, 0xFFC46647, sw1);
                pressed = Ui.rect(0xFFC46647, r, 0xFFB05638, sw1);
                focused = Ui.rect(Ui.ACCENT, r, Ui.TEXT, sw2);
                rippleColor = 0x33FFFFFF;
                break;
            case SUCCESS:
                normal = Ui.rect(Ui.GREEN, r, 0xFF246649, sw1);
                pressed = Ui.rect(0xFF246649, r, 0xFF1B523A, sw1);
                focused = Ui.rect(Ui.GREEN, r, Ui.TEXT, sw2);
                rippleColor = 0x33FFFFFF;
                break;
            case DANGER:
                normal = Ui.rect(0x16C94A4A, r, 0x55C94A4A, sw1);
                pressed = Ui.rect(0x28C94A4A, r, 0x88C94A4A, sw1);
                focused = Ui.rect(0x20C94A4A, r, Ui.RED, sw2);
                rippleColor = 0x22C94A4A;
                break;
            case GHOST:
                normal = Ui.rect(0x00000000, r, 0, 0);
                pressed = Ui.rect(0x14141413, r, 0, 0);
                focused = Ui.rect(0x0F141413, r, Ui.ACCENT, sw2);
                break;
            default:
                normal = Ui.rect(Ui.BG2, r, Ui.STROKE, sw1);
                pressed = Ui.rect(0xFFE6E2D8, r, 0xFFD0CCC2, sw1);
                focused = Ui.rect(Ui.BG2, r, Ui.ACCENT, sw2);
                break;
        }
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{android.R.attr.state_focused}, focused);
        sld.addState(new int[]{}, normal);
        GradientDrawable mask = Ui.rect(0xFFFFFFFF, r, 0, 0);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), sld, mask);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        updateScale();
    }

    @Override
    public void setHovered(boolean hovered) {
        super.setHovered(hovered);
        updateScale();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.42f);
    }

    private void updateScale() {
        float s = isPressed() ? 0.97f : (isHovered() && isEnabled() ? 1.02f : 1f);
        animate().scaleX(s).scaleY(s).setDuration(90).start();
    }
}
