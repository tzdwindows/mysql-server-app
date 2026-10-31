package com.mysqllab;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;
import android.system.OsConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MysqlServerManager {

    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private StateCallback callback;

    private Process mysqldProcess;
    private volatile ServerState currentState = ServerState.STOPPED;
    private final AtomicBoolean isStopping = new AtomicBoolean(false);

    private final File baseDir;
    private final File dataDir;
    private final File tmpDir;
    private final File shareDir;
    private final File configFile;
    private final File pidFile;

    public MysqlServerManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());

        this.baseDir = this.context.getFilesDir();
        this.dataDir = new File(baseDir, "data");
        this.tmpDir = new File(baseDir, "tmp");
        this.shareDir = new File(baseDir, "share");
        this.configFile = new File(baseDir, "my.cnf");
        this.pidFile = new File(tmpDir, "mysqld.pid");

        tmpDir.mkdirs();
        shareDir.mkdirs();
    }

    public void setCallback(StateCallback callback) {
        this.callback = callback;
        probeExistingServer();
    }

    /** Check if mysqld from a previous session is already alive on 127.0.0.1:3306. */
    public void probeExistingServer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (checkPortAvailable("127.0.0.1", 3306, 350)) {
                    if (currentState != ServerState.RUNNING) {
                        log("[Server] 检测到后台已有运行中的 MySQL 实例 (127.0.0.1:3306)，已自动接管。");
                        setState(ServerState.RUNNING, "Server running on 127.0.0.1:3306");
                    }
                }
            }
        });
    }

    public ServerState getCurrentState() {
        return currentState;
    }

    public void setState(ServerState state, String message) {
        this.currentState = state;
        if (state == ServerState.RUNNING) {
            MysqlForegroundService.start(context);
        } else if (state == ServerState.STOPPED || state == ServerState.ERROR) {
            MysqlForegroundService.stop(context);
        }
        if (callback != null) {
            mainHandler.post(new StateChangeNotifier(callback, state, message));
        }
    }

    public void log(String line) {
        if (callback != null) {
            mainHandler.post(new LogNotifier(callback, line));
        }
    }

    public File getMysqldExecutable() {
        File nativeLib = new File(context.getApplicationInfo().nativeLibraryDir, "libmysqld.so");
        if (nativeLib.exists() && nativeLib.canExecute()) {
            return nativeLib;
        }

        File binDir = new File(baseDir, "bin");
        File binFile = new File(binDir, "mysqld");
        if (binFile.exists()) {
            binFile.setExecutable(true, false);
            return binFile;
        }

        if (nativeLib.exists()) {
            return nativeLib;
        }

        return binFile;
    }

    public void extractAssetsIfNeeded() {
        try {
            extractAssetDir("share", shareDir);
            extractAssetDir("bin", new File(baseDir, "bin"));

            File binFile = new File(baseDir, "bin/mysqld");
            if (binFile.exists()) {
                binFile.setExecutable(true, false);
            }
        } catch (Exception e) {
            log("[Setup] Asset extract notice: " + e.getMessage());
        }
    }

    private void extractAssetDir(String assetPath, File targetDir) {
        try {
            String[] list = context.getAssets().list(assetPath);
            if (list == null || list.length == 0) return;
            if (!targetDir.exists()) targetDir.mkdirs();

            for (String file : list) {
                String subAsset = assetPath.isEmpty() ? file : assetPath + "/" + file;
                File targetFile = new File(targetDir, file);
                String[] subList = context.getAssets().list(subAsset);
                if (subList != null && subList.length > 0) {
                    extractAssetDir(subAsset, targetFile);
                } else {
                    if (!targetFile.exists() || targetFile.length() == 0) {
                        try (InputStream in = context.getAssets().open(subAsset);
                             OutputStream out = new FileOutputStream(targetFile)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        }
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    public void writeConfigFile() throws IOException {
        File initSqlFile = new File(baseDir, "init.sql");
        String initSql = "CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '';\n"
                + "GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;\n"
                + "CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '';\n"
                + "GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;\n"
                + "FLUSH PRIVILEGES;\n";
        try (FileWriter writer = new FileWriter(initSqlFile)) {
            writer.write(initSql);
        }

        String cnf = "[mysqld]\n"
                + "basedir = " + baseDir.getAbsolutePath() + "\n"
                + "datadir = " + dataDir.getAbsolutePath() + "\n"
                + "tmpdir = " + tmpDir.getAbsolutePath() + "\n"
                + "lc-messages-dir = " + shareDir.getAbsolutePath() + "\n"
                + "lc-messages = en_US\n"
                + "character-sets-dir = " + new File(shareDir, "charsets").getAbsolutePath() + "\n"
                + "socket = " + new File(tmpDir, "mysql.sock").getAbsolutePath() + "\n"
                + "pid-file = " + pidFile.getAbsolutePath() + "\n"
                + "port = 3306\n"
                + "bind-address = 127.0.0.1\n"
                + "mysqlx = OFF\n"
                + "init-file = " + initSqlFile.getAbsolutePath() + "\n"
                + "default_storage_engine = InnoDB\n"
                + "innodb_buffer_pool_size = 32M\n"
                + "innodb_redo_log_capacity = 16M\n"
                + "max_connections = 20\n"
                + "table_open_cache = 64\n"
                + "skip-name-resolve\n"
                + "default_authentication_plugin = mysql_native_password\n";

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(cnf);
        }
    }

    public void reinitializeDatabase() {
        executor.execute(new ReinitTask(this));
    }

    void reinitializeDatabaseSync() {
        stopServerSync();
        setState(ServerState.INITIALIZING, "Cleaning and re-initializing database…");
        log("[Init] Removing existing data directory…");
        deleteRecursive(dataDir);
        initializeDatabaseSync();
    }

    private boolean initializeDatabaseSync() {
        try {
            killOrphanedMysqld(true);
            extractAssetsIfNeeded();
            writeConfigFile();
            dataDir.mkdirs();

            File mysqld = getMysqldExecutable();
            if (!mysqld.exists()) {
                String err = "mysqld binary not found at " + mysqld.getAbsolutePath();
                log("[Error] " + err);
                setState(ServerState.ERROR, err);
                return false;
            }

            log("[Init] Executing: " + mysqld.getAbsolutePath() + " --initialize-insecure");
            ProcessBuilder pb = new ProcessBuilder(
                    mysqld.getAbsolutePath(),
                    "--defaults-file=" + configFile.getAbsolutePath(),
                    "--initialize-insecure"
            );
            String nativeDir = context.getApplicationInfo().nativeLibraryDir;
            pb.environment().put("LD_LIBRARY_PATH", nativeDir + ":/system/lib64:/vendor/lib64");
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log("[Init] " + line);
                }
            }

            int code = p.waitFor();
            if (code == 0) {
                log("[Init] Database initialization complete.");
                setState(ServerState.STOPPED, "Database initialized successfully.");
                return true;
            } else {
                String err = "Initialization failed with exit code: " + code;
                log("[Init] " + err);
                setState(ServerState.ERROR, err);
                return false;
            }
        } catch (Exception e) {
            String err = "Initialization error: " + e.getMessage();
            log("[Init] " + err);
            setState(ServerState.ERROR, err);
            return false;
        }
    }

    public void startServer() {
        if (currentState == ServerState.RUNNING || currentState == ServerState.STARTING) {
            return;
        }
        executor.execute(new StartServerTask(this));
    }

    void startServerSync() {
        try {
            isStopping.set(false);
            setState(ServerState.STARTING, "Starting MySQL Server…");

            // 1. If an existing mysqld instance is already listening on 3306, adopt it!
            if (checkPortAvailable("127.0.0.1", 3306, 400)) {
                log("[Server] 端口 3306 已有正在运行的 MySQL 实例，已直接连接复用 (避免 ibdata1 锁冲突)。");
                setState(ServerState.RUNNING, "Server running on 127.0.0.1:3306");
                return;
            }

            // 2. Otherwise clean up any stale/orphaned mysqld processes holding ibdata1 lock
            killOrphanedMysqld(false);
            cleanStaleSocketFiles();

            File ibdata = new File(dataDir, "ibdata1");
            if (!dataDir.exists() || !ibdata.exists()) {
                log("[Server] Data directory not initialized. Initializing first…");
                deleteRecursive(dataDir);
                if (!initializeDatabaseSync()) {
                    return;
                }
                setState(ServerState.STARTING, "Starting MySQL Server…");
            }

            extractAssetsIfNeeded();
            writeConfigFile();
            File mysqld = getMysqldExecutable();
            if (!mysqld.exists()) {
                String err = "Cannot start: mysqld not found at " + mysqld.getAbsolutePath();
                log("[Error] " + err);
                setState(ServerState.ERROR, err);
                return;
            }

            log("[Server] Launching: " + mysqld.getAbsolutePath() + " --defaults-file=" + configFile.getAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(
                    mysqld.getAbsolutePath(),
                    "--defaults-file=" + configFile.getAbsolutePath()
            );
            String nativeDir = context.getApplicationInfo().nativeLibraryDir;
            pb.environment().put("LD_LIBRARY_PATH", nativeDir + ":/system/lib64:/vendor/lib64");
            pb.redirectErrorStream(true);
            mysqldProcess = pb.start();

            executor.execute(new LogReaderTask(this, mysqldProcess.getInputStream()));

            boolean ready = false;
            for (int i = 0; i < 40; i++) {
                if (isStopping.get()) break;
                try {
                    int exit = mysqldProcess.exitValue();
                    log("[Server] Process exited prematurely with code " + exit);
                    setState(ServerState.ERROR, "mysqld terminated with code " + exit);
                    return;
                } catch (IllegalThreadStateException notExited) {
                }

                if (checkPortAvailable("127.0.0.1", 3306, 500)) {
                    ready = true;
                    break;
                }
                Thread.sleep(500);
            }

            if (ready) {
                log("[Server] Ready for connections on 127.0.0.1:3306");
                setState(ServerState.RUNNING, "Server running on 127.0.0.1:3306");
            } else if (!isStopping.get()) {
                log("[Server] Server did not open port 3306 in time.");
                setState(ServerState.ERROR, "Startup timed out waiting for port 3306");
            }

        } catch (Exception e) {
            String err = "Startup error: " + e.getMessage();
            log("[Server] " + err);
            setState(ServerState.ERROR, err);
        }
    }

    public void stopServer() {
        executor.execute(new StopServerTask(this));
    }

    void stopServerSync() {
        isStopping.set(true);
        log("[Server] Stopping MySQL Server…");

        // Try SQL SHUTDOWN first if port 3306 is open
        if (checkPortAvailable("127.0.0.1", 3306, 250)) {
            try {
                MysqlClient c = new MysqlClient();
                c.connect("127.0.0.1", 3306, "root", "", "", 1500);
                c.executeQuery("SHUTDOWN");
                c.close();
            } catch (Exception ignored) {}
        }

        if (mysqldProcess != null) {
            try {
                mysqldProcess.destroy();
                mysqldProcess.waitFor();
            } catch (Exception ignored) {}
            mysqldProcess = null;
        }

        killOrphanedMysqld(true);
        cleanStaleSocketFiles();

        setState(ServerState.STOPPED, "Server stopped.");
        log("[Server] Server stopped.");
    }

    private void cleanStaleSocketFiles() {
        try {
            new File(tmpDir, "mysql.sock").delete();
            new File(tmpDir, "mysql.sock.lock").delete();
            pidFile.delete();
        } catch (Exception ignored) {}
    }

    /**
     * Find and terminate any leftover libmysqld.so / mysqld processes belonging to this app UID
     * so InnoDB releases the fcntl write lock on ./ibdata1 (fixes error 11).
     */
    private void killOrphanedMysqld(boolean quiet) {
        int myPid = android.os.Process.myPid();
        List<Integer> pids = findMysqldPids(myPid);
        if (pids.isEmpty()) return;

        if (!quiet) {
            log("[Server] 清理残留的 mysqld 进程以释放 ibdata1 文件锁: PID " + pids);
        }

        // First send SIGTERM for clean InnoDB flush
        for (int pid : pids) {
            try {
                Os.kill(pid, OsConstants.SIGTERM);
            } catch (Throwable ignored) {}
        }

        long deadline = System.currentTimeMillis() + 1800;
        while (System.currentTimeMillis() < deadline) {
            if (findMysqldPids(myPid).isEmpty()) return;
            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
                break;
            }
        }

        // Force kill any remaining stuck processes
        for (int pid : findMysqldPids(myPid)) {
            try {
                Os.kill(pid, OsConstants.SIGKILL);
            } catch (Throwable ignored) {}
            try {
                android.os.Process.killProcess(pid);
            } catch (Throwable ignored) {}
        }
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {}
    }

    private List<Integer> findMysqldPids(int excludePid) {
        List<Integer> result = new ArrayList<>();
        // 1. Check pidFile if present
        if (pidFile.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(pidFile)))) {
                String line = br.readLine();
                if (line != null) {
                    int pid = Integer.parseInt(line.trim());
                    if (pid > 0 && pid != excludePid && new File("/proc/" + pid).exists()) {
                        result.add(pid);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Scan /proc for any process whose cmdline references libmysqld.so or mysqld
        File proc = new File("/proc");
        String[] entries = proc.list();
        if (entries != null) {
            byte[] buf = new byte[512];
            for (String name : entries) {
                if (name.length() == 0 || name.charAt(0) < '0' || name.charAt(0) > '9') continue;
                try {
                    int pid = Integer.parseInt(name);
                    if (pid == excludePid || result.contains(pid)) continue;
                    File cmdFile = new File(proc, name + "/cmdline");
                    if (!cmdFile.canRead()) continue;
                    try (FileInputStream in = new FileInputStream(cmdFile)) {
                        int n = in.read(buf);
                        if (n > 0) {
                            String cmd = new String(buf, 0, n);
                            if (cmd.contains("libmysqld") || cmd.contains("mysqld")) {
                                result.add(pid);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private boolean checkPortAvailable(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursive(c);
                }
            }
        }
        f.delete();
    }
}
