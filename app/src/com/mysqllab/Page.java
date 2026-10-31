package com.mysqllab;

import android.content.Context;
import android.widget.FrameLayout;

/** Base class for the four main screens. */
public abstract class Page extends FrameLayout {

    protected final AppHost host;
    protected boolean wide;

    protected Page(Context c, AppHost host) {
        super(c);
        this.host = host;
    }

    /** Called when the layout class changes (phone portrait vs landscape / desktop). */
    public void setWide(boolean wide) {
        this.wide = wide;
    }

    /** Called every time the page becomes the visible one. */
    public void onShown() {
    }

    public void onServerState(ServerState state, String message) {
    }

    public void onLog(String line) {
    }

    public void onPauseSave() {
    }
}
