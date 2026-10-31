package com.mysqllab;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Clean Claude-style minimalist card: crisp white surface (#FFFFFF) with 1dp warm stone border (#E4E1DA). */
public class Card extends LinearLayout {

    public Card(Context c) {
        super(c);
        setOrientation(VERTICAL);
        int p = Ui.dp(c, 16);
        setPadding(p, p, p, p);
        setBackground(Ui.rect(Ui.CARD, Ui.dpf(c, 12), Ui.STROKE, Math.max(1, Ui.dp(c, 1))));
        Ui.clipRound(this, 12);
    }

    /** Card header row: icon + title (+ optional trailing views added by the caller). */
    public static LinearLayout headerRow(Context c, int icon, int iconColor, String title) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        IconView iv = new IconView(c, icon, iconColor);
        row.addView(iv, Ui.lp(Ui.dp(c, 18), Ui.dp(c, 18)));
        TextView t = Ui.label(c, title, 14.5f, Ui.TEXT, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(c, 8);
        row.addView(t, lp);
        return row;
    }

    public static View spacer(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(c, dp)));
        return v;
    }
}
