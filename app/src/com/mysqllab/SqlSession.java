package com.mysqllab;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One persistent MySQL connection (so transactions, SET variables, temporary tables and USE
 * survive between runs) executed on a single background thread.
 */
public class SqlSession {

    public interface Callback<T> {
        void done(T value);
    }

    public static class Batch {
        public final List<String> statements = new ArrayList<>();
        public final List<QueryResult> results = new ArrayList<>();
        public String database = "";
        public long totalMs;
    }

    public static final String HOST = "127.0.0.1";
    public static final int PORT = 3306;

    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private MysqlClient client;
    private String lastDb = "";
    private volatile int threadId = 0;
    private volatile boolean busy;

    public boolean isBusy() {
        return busy;
    }

    public void runScript(final String script, final Callback<Batch> cb) {
        busy = true;
        ex.execute(new Runnable() {
            @Override
            public void run() {
                final Batch b = new Batch();
                long t0 = System.currentTimeMillis();
                try {
                    b.statements.addAll(SqlSplitter.split(script));
                    boolean anyOk = false;
                    for (String stmt : b.statements) {
                        QueryResult r;
                        try {
                            ensure();
                            r = client.executeQuery(stmt);
                        } catch (Exception e) {
                            dropClient();
                            r = new QueryResult();
                            r.errorMessage = "无法连接到 MySQL (" + HOST + ":" + PORT + "): " + e.getMessage();
                        }
                        if (!r.isSuccess() && r.errorMessage != null
                                && (r.errorMessage.startsWith("Not connected")
                                || r.errorMessage.startsWith("Execution failed"))) {
                            dropClient();
                        }
                        b.results.add(r);
                        if (!r.isSuccess()) break;
                        anyOk = true;
                    }
                    if (anyOk && client != null) {
                        QueryResult d = client.executeQuery("SELECT DATABASE()");
                        if (d.isSuccess() && d.rows.size() > 0 && d.rows.get(0).size() > 0) {
                            String v = d.rows.get(0).get(0);
                            lastDb = "NULL".equals(v) ? "" : v;
                        }
                    }
                } finally {
                    b.database = lastDb;
                    b.totalMs = System.currentTimeMillis() - t0;
                    busy = false;
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.done(b);
                    }
                });
            }
        });
    }

    /** Run one statement on the shared connection (used by the explorer). */
    public void query(final String sql, final Callback<QueryResult> cb) {
        ex.execute(new Runnable() {
            @Override
            public void run() {
                QueryResult r;
                try {
                    ensure();
                    r = client.executeQuery(sql);
                    if (!r.isSuccess() && r.errorMessage != null
                            && (r.errorMessage.startsWith("Not connected")
                            || r.errorMessage.startsWith("Execution failed"))) {
                        dropClient();
                    }
                } catch (Exception e) {
                    dropClient();
                    r = new QueryResult();
                    r.errorMessage = "无法连接到 MySQL (" + HOST + ":" + PORT + "): " + e.getMessage();
                }
                final QueryResult fr = r;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.done(fr);
                    }
                });
            }
        });
    }

    /** KILL QUERY from a second connection. */
    public void cancel() {
        final int id = threadId;
        if (id <= 0) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                oneShot("KILL QUERY " + id);
            }
        }).start();
    }

    public void reset() {
        ex.execute(new Runnable() {
            @Override
            public void run() {
                dropClient();
                lastDb = "";
            }
        });
    }

    private void ensure() throws java.io.IOException {
        if (client != null && client.isOpen()) return;
        dropClient();
        MysqlClient c = new MysqlClient();
        c.connect(HOST, PORT, "root", "", "", 5000);
        c.setReadTimeout(0);
        client = c;
        threadId = c.getThreadId();
        if (lastDb.length() > 0) {
            c.executeQuery("USE `" + lastDb.replace("`", "``") + "`");
        }
    }

    private void dropClient() {
        if (client != null) {
            client.close();
            client = null;
        }
        threadId = 0;
    }

    /** Short-lived separate connection; blocking, call off the main thread. */
    public static List<QueryResult> oneShot(String... sqls) {
        List<QueryResult> out = new ArrayList<>();
        MysqlClient c = new MysqlClient();
        try {
            c.connect(HOST, PORT, "root", "", "", 2500);
            c.setReadTimeout(4000);
            for (String s : sqls) out.add(c.executeQuery(s));
        } catch (Exception e) {
            QueryResult r = new QueryResult();
            r.errorMessage = "连接失败: " + e.getMessage();
            out.add(r);
        } finally {
            c.close();
        }
        return out;
    }
}
