package com.mysqllab;

public class StartServerTask implements Runnable {
    private final MysqlServerManager manager;

    public StartServerTask(MysqlServerManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        manager.startServerSync();
    }
}
