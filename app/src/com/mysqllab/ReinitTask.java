package com.mysqllab;

public class ReinitTask implements Runnable {
    private final MysqlServerManager manager;

    public ReinitTask(MysqlServerManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        manager.reinitializeDatabaseSync();
    }
}
