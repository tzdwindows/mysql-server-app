package com.mysqllab;

public class StopServerTask implements Runnable {
    private final MysqlServerManager manager;

    public StopServerTask(MysqlServerManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        manager.stopServerSync();
    }
}
