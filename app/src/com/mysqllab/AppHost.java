package com.mysqllab;

import android.content.Context;

/** Services the main activity offers to the pages. */
public interface AppHost extends ResultGrid.Host {
    Context context();

    SqlSession session();

    MysqlServerManager manager();

    ServerState serverState();

    void showPage(int index);

    /** Put SQL in the workbench, switch to it and optionally run it. */
    void runInWorkbench(String sql, boolean execute);

    void startServer();

    void stopServer();

    void reinitServer();
}
