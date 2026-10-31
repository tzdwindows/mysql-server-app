package com.mysqllab;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LogReaderTask implements Runnable {
    private final MysqlServerManager manager;
    private final InputStream stream;

    public LogReaderTask(MysqlServerManager manager, InputStream stream) {
        this.manager = manager;
        this.stream = stream;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                manager.log("[mysqld] " + line);
            }
        } catch (IOException ignored) {}
    }
}
