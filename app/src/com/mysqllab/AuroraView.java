package com.mysqllab;

import android.content.Context;
import android.view.View;

/**
 * Clean Claude-style warm paper background (#FAF9F5).
 * Pure, distraction-free minimalist surface with zero heavy overdraw.
 */
public class AuroraView extends View {

    public AuroraView(Context c) {
        super(c);
        setBackgroundColor(Ui.BG);
    }
}
