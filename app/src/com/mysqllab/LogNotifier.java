package com.mysqllab;

public class LogNotifier implements Runnable {
    private final StateCallback callback;
    private final String line;

    public LogNotifier(StateCallback callback, String line) {
        this.callback = callback;
        this.line = line;
    }

    @Override
    public void run() {
        if (callback != null) {
            callback.onLog(line);
        }
    }
}
