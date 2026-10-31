package com.mysqllab;

public class ServerState {
    public static final ServerState STOPPED = new ServerState("STOPPED");
    public static final ServerState INITIALIZING = new ServerState("INITIALIZING");
    public static final ServerState STARTING = new ServerState("STARTING");
    public static final ServerState RUNNING = new ServerState("RUNNING");
    public static final ServerState ERROR = new ServerState("ERROR");

    private final String name;

    private ServerState(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
