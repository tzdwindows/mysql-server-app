package com.mysqllab;

public interface StateCallback {
    void onStateChanged(ServerState state, String message);
    void onLog(String line);
}
