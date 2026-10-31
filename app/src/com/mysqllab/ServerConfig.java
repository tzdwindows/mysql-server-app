package com.mysqllab;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted server connection and startup arguments configuration.
 * Configurable host address, port, username, password, and custom mysqld startup flags.
 * Author: tzdwindows 7
 */
public class ServerConfig {

    private static final String PREF_NAME = "mysql_server_config";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USER = "user";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_EXTRA_ARGS = "extra_args";

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 3306;
    public static final String DEFAULT_USER = "root";
    public static final String DEFAULT_PASSWORD = "";
    public static final String DEFAULT_EXTRA_ARGS = "";

    private static ServerConfig instance;

    public static synchronized ServerConfig get(Context ctx) {
        if (instance == null) {
            instance = new ServerConfig();
            if (ctx != null) {
                instance.load(ctx.getApplicationContext());
            }
        }
        return instance;
    }

    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private String user = DEFAULT_USER;
    private String password = DEFAULT_PASSWORD;
    private String extraArgs = DEFAULT_EXTRA_ARGS;

    public String getHost() {
        return (host != null && !host.trim().isEmpty()) ? host.trim() : DEFAULT_HOST;
    }

    public void setHost(String host) {
        this.host = (host != null) ? host.trim() : DEFAULT_HOST;
    }

    public int getPort() {
        return (port > 0 && port <= 65535) ? port : DEFAULT_PORT;
    }

    public void setPort(int port) {
        this.port = (port > 0 && port <= 65535) ? port : DEFAULT_PORT;
    }

    public String getUser() {
        return (user != null && !user.trim().isEmpty()) ? user.trim() : DEFAULT_USER;
    }

    public void setUser(String user) {
        this.user = (user != null) ? user.trim() : DEFAULT_USER;
    }

    public String getPassword() {
        return password != null ? password : "";
    }

    public void setPassword(String password) {
        this.password = password != null ? password : "";
    }

    public String getExtraArgs() {
        return extraArgs != null ? extraArgs : "";
    }

    public void setExtraArgs(String extraArgs) {
        this.extraArgs = extraArgs != null ? extraArgs : "";
    }

    /** Tokenized command-line argument list for ProcessBuilder. */
    public List<String> getExtraArgsList() {
        List<String> list = new ArrayList<>();
        if (extraArgs == null || extraArgs.trim().isEmpty()) {
            return list;
        }
        String[] tokens = extraArgs.trim().split("\\s+");
        for (String t : tokens) {
            String clean = t.trim();
            if (!clean.isEmpty()) {
                list.add(clean);
            }
        }
        return list;
    }

    public void load(Context ctx) {
        if (ctx == null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.host = sp.getString(KEY_HOST, DEFAULT_HOST);
        this.port = sp.getInt(KEY_PORT, DEFAULT_PORT);
        this.user = sp.getString(KEY_USER, DEFAULT_USER);
        this.password = sp.getString(KEY_PASSWORD, DEFAULT_PASSWORD);
        this.extraArgs = sp.getString(KEY_EXTRA_ARGS, DEFAULT_EXTRA_ARGS);
    }

    public void save(Context ctx) {
        if (ctx == null) return;
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_HOST, getHost())
                .putInt(KEY_PORT, getPort())
                .putString(KEY_USER, getUser())
                .putString(KEY_PASSWORD, getPassword())
                .putString(KEY_EXTRA_ARGS, getExtraArgs())
                .apply();
    }

    public void resetToDefaults(Context ctx) {
        this.host = DEFAULT_HOST;
        this.port = DEFAULT_PORT;
        this.user = DEFAULT_USER;
        this.password = DEFAULT_PASSWORD;
        this.extraArgs = DEFAULT_EXTRA_ARGS;
        save(ctx);
    }
}
