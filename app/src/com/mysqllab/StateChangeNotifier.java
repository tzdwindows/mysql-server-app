package com.mysqllab;

public class StateChangeNotifier implements Runnable {
    private final StateCallback callback;
    private final ServerState state;
    private final String message;

    public StateChangeNotifier(StateCallback callback, ServerState state, String message) {
        this.callback = callback;
        this.state = state;
        this.message = message;
    }

    @Override
    public void run() {
        if (callback != null) {
            callback.onStateChanged(state, message);
        }
    }
}
