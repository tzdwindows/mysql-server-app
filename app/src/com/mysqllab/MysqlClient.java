package com.mysqllab;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MysqlClient implements AutoCloseable {

    private static final int CLIENT_LONG_PASSWORD = 1;
    private static final int CLIENT_FOUND_ROWS = 2;
    private static final int CLIENT_LONG_FLAG = 4;
    private static final int CLIENT_CONNECT_WITH_DB = 8;
    private static final int CLIENT_LOCAL_FILES = 128;
    private static final int CLIENT_PROTOCOL_41 = 512;
    private static final int CLIENT_INTERACTIVE = 1024;
    private static final int CLIENT_TRANSACTIONS = 8192;
    private static final int CLIENT_SECURE_CONNECTION = 32768;
    private static final int CLIENT_MULTI_STATEMENTS = 65536;
    private static final int CLIENT_MULTI_RESULTS = 131072;
    private static final int CLIENT_PLUGIN_AUTH = 524288;
    private static final int CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA = 0x00200000;

    private static final byte COM_QUERY = 0x03;
    private static final byte COM_INIT_DB = 0x02;
    private static final byte COM_PING = 0x0E;
    private static final byte COM_QUIT = 0x01;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int sequenceId = 0;
    private String currentDatabase = "";
    private int threadId = 0;
    private String serverVersion = "";

    public int getThreadId() {
        return threadId;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    /** 0 = block forever (needed for long running research queries). */
    public void setReadTimeout(int ms) {
        try {
            if (socket != null) socket.setSoTimeout(ms);
        } catch (Exception ignored) {
        }
    }

    public boolean isOpen() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void connect(String host, int port, String user, String password, String database, int timeoutMs) throws IOException {
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();

        // 1. Read Initial Handshake Packet
        MysqlPacket handshake = readPacket();
        byte[] data = handshake.payload;
        int pos = 0;
        int protoVer = data[pos++] & 0xFF;
        if (protoVer == 0xFF) {
            // Error packet
            int errCode = (data[pos++] & 0xFF) | ((data[pos++] & 0xFF) << 8);
            String errMsg = new String(data, pos, data.length - pos, StandardCharsets.UTF_8);
            throw new IOException("Server error during handshake: [" + errCode + "] " + errMsg);
        }

        // Server version string (null terminated)
        int verStart = pos;
        while (pos < data.length && data[pos] != 0) pos++;
        String serverVersion = new String(data, verStart, pos - verStart, StandardCharsets.UTF_8);
        pos++; // skip null

        // Thread ID (4 bytes)
        int threadId = (data[pos++] & 0xFF) | ((data[pos++] & 0xFF) << 8) | ((data[pos++] & 0xFF) << 16) | ((data[pos++] & 0xFF) << 24);

        this.threadId = threadId;
        this.serverVersion = serverVersion;

        // Auth plugin data part 1 (8 bytes)
        byte[] salt = new byte[20];
        System.arraycopy(data, pos, salt, 0, 8);
        pos += 8;
        pos++; // filter [0x00]

        // Capability flags lower 2 bytes
        int capLower = (data[pos++] & 0xFF) | ((data[pos++] & 0xFF) << 8);
        int charset = data[pos++] & 0xFF;
        int statusFlags = (data[pos++] & 0xFF) | ((data[pos++] & 0xFF) << 8);
        int capUpper = (data[pos++] & 0xFF) | ((data[pos++] & 0xFF) << 8);
        int capabilities = capLower | (capUpper << 16);

        int authPluginDataLen = 0;
        if ((capabilities & CLIENT_PLUGIN_AUTH) != 0) {
            authPluginDataLen = data[pos++] & 0xFF;
        } else {
            pos++;
        }
        pos += 10; // 10 bytes reserved

        // Auth plugin data part 2
        int salt2Len = Math.max(12, authPluginDataLen - 8);
        if (pos + salt2Len <= data.length) {
            System.arraycopy(data, pos, salt, 8, Math.min(12, salt2Len));
            pos += salt2Len;
        }

        // Auth plugin name
        String pluginName = "mysql_native_password";
        if ((capabilities & CLIENT_PLUGIN_AUTH) != 0 && pos < data.length) {
            int pStart = pos;
            while (pos < data.length && data[pos] != 0) pos++;
            pluginName = new String(data, pStart, pos - pStart, StandardCharsets.UTF_8);
        }

        // 2. Build Handshake Response 41
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int clientCaps = CLIENT_PROTOCOL_41 | CLIENT_LONG_PASSWORD | CLIENT_LONG_FLAG | CLIENT_TRANSACTIONS | CLIENT_SECURE_CONNECTION;
        if (database != null && !database.isEmpty()) {
            clientCaps |= CLIENT_CONNECT_WITH_DB;
        }
        clientCaps |= CLIENT_PLUGIN_AUTH;

        write4(buf, clientCaps);
        write4(buf, 16 * 1024 * 1024); // max packet size
        buf.write(45); // utf8mb4 collation
        buf.write(new byte[23]); // 23 bytes zeros

        // User
        buf.write(user.getBytes(StandardCharsets.UTF_8));
        buf.write(0);

        // Password hash
        byte[] authResponse;
        if (password == null || password.isEmpty()) {
            authResponse = new byte[0];
        } else {
            authResponse = scrambleNativePassword(password, salt);
        }
        buf.write((byte) authResponse.length);
        buf.write(authResponse);

        if (database != null && !database.isEmpty()) {
            buf.write(database.getBytes(StandardCharsets.UTF_8));
            buf.write(0);
        }

        // Auth plugin name
        buf.write(pluginName.getBytes(StandardCharsets.UTF_8));
        buf.write(0);

        sequenceId = handshake.sequenceId + 1;
        sendPacket(buf.toByteArray());

        // 3. Read Auth Result
        MysqlPacket authResult = readPacket();
        if ((authResult.payload[0] & 0xFF) == 0xFF) {
            throw new IOException("Auth failed: " + parseErrPacket(authResult.payload));
        } else if ((authResult.payload[0] & 0xFF) == 0xFE) {
            // AuthSwitchRequest
            sequenceId++;
            if (password == null || password.isEmpty()) {
                sendPacket(new byte[0]);
            } else {
                // Read plugin name from payload
                byte[] switchSalt = new byte[20];
                int sp = 1;
                while (sp < authResult.payload.length && authResult.payload[sp] != 0) sp++;
                sp++;
                if (authResult.payload.length - sp >= 20) {
                    System.arraycopy(authResult.payload, sp, switchSalt, 0, 20);
                }
                sendPacket(scrambleNativePassword(password, switchSalt));
            }
            MysqlPacket authSwitchResult = readPacket();
            if ((authSwitchResult.payload[0] & 0xFF) == 0xFF) {
                throw new IOException("Auth switch failed: " + parseErrPacket(authSwitchResult.payload));
            }
        } else if ((authResult.payload[0] & 0xFF) == 0x01) {
            // AuthMoreData (caching_sha2_password fast path or full auth)
            // For empty password on local root, fast path sends OK or more data.
            // Read next packet
            MysqlPacket next = readPacket();
            if ((next.payload[0] & 0xFF) == 0xFF) {
                throw new IOException("Auth failed: " + parseErrPacket(next.payload));
            }
        }

        this.currentDatabase = (database != null) ? database : "";
    }

    public QueryResult executeQuery(String sql) {
        QueryResult result = new QueryResult();
        long startTime = System.currentTimeMillis();

        if (socket == null || !socket.isConnected() || socket.isClosed()) {
            result.errorMessage = "Not connected to MySQL server.";
            return result;
        }

        try {
            sequenceId = 0;
            byte[] sqlBytes = sql.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[1 + sqlBytes.length];
            payload[0] = COM_QUERY;
            System.arraycopy(sqlBytes, 0, payload, 1, sqlBytes.length);
            sendPacket(payload);

            MysqlPacket firstPacket = readPacket();
            int header = firstPacket.payload[0] & 0xFF;

            if (header == 0xFF) {
                // ERR Packet
                result.errorMessage = parseErrPacket(firstPacket.payload);
                result.errorCode = parseErrCode(firstPacket.payload);
            } else if (header == 0x00) {
                // OK Packet
                result.isResultSet = false;
                int pos = 1;
                long[] aff = readLenEnc(firstPacket.payload, pos);
                result.affectedRows = aff[0];
                pos += (int) aff[1];

                long[] lastId = readLenEnc(firstPacket.payload, pos);
                result.lastInsertId = lastId[0];
                pos += (int) lastId[1];

                result.message = "Query OK, " + result.affectedRows + " row(s) affected.";
            } else {
                // Resultset: first byte/packet is column count
                result.isResultSet = true;
                long[] colCountInfo = readLenEnc(firstPacket.payload, 0);
                int columnCount = (int) colCountInfo[0];

                // Read column definitions
                for (int i = 0; i < columnCount; i++) {
                    MysqlPacket colPacket = readPacket();
                    String colName = parseColumnName(colPacket.payload);
                    result.columnNames.add(colName);
                }

                // Read EOF or OK packet following column definitions
                MysqlPacket colEof = readPacket();
                // EOF packet has 0xFE as first byte and length < 9
                // or in MySQL 8.0 deprecate-eof, OK packet (0x00)

                // Read rows until EOF/OK packet
                while (true) {
                    MysqlPacket rowPacket = readPacket();
                    int rowHeader = rowPacket.payload[0] & 0xFF;
                    if (rowHeader == 0xFE && rowPacket.payload.length < 9) {
                        break; // EOF
                    }
                    if (rowHeader == 0xFF) {
                        result.errorMessage = parseErrPacket(rowPacket.payload);
                        break;
                    }

                    // Parse text row columns
                    List<String> row = new ArrayList<>();
                    int pos = 0;
                    for (int c = 0; c < columnCount; c++) {
                        if (pos >= rowPacket.payload.length) {
                            row.add("");
                            continue;
                        }
                        int b = rowPacket.payload[pos] & 0xFF;
                        if (b == 0xFB) {
                            row.add("NULL");
                            pos++;
                        } else {
                            long[] lenInfo = readLenEnc(rowPacket.payload, pos);
                            int len = (int) lenInfo[0];
                            pos += (int) lenInfo[1];
                            String val = new String(rowPacket.payload, pos, len, StandardCharsets.UTF_8);
                            pos += len;
                            row.add(val);
                        }
                    }
                    result.rows.add(row);
                }

                result.message = result.rows.size() + " row(s) in set.";
            }
        } catch (Exception e) {
            result.errorMessage = "Execution failed: " + e.getMessage();
        }

        result.executionTimeMs = System.currentTimeMillis() - startTime;
        return result;
    }

    private String parseColumnName(byte[] data) {
        try {
            int pos = 0;
            // catalog
            long[] len = readLenEnc(data, pos); pos += (int) len[1] + (int) len[0];
            // schema
            len = readLenEnc(data, pos); pos += (int) len[1] + (int) len[0];
            // table
            len = readLenEnc(data, pos); pos += (int) len[1] + (int) len[0];
            // org_table
            len = readLenEnc(data, pos); pos += (int) len[1] + (int) len[0];
            // name
            len = readLenEnc(data, pos); pos += (int) len[1];
            String name = new String(data, pos, (int) len[0], StandardCharsets.UTF_8);
            return name;
        } catch (Exception e) {
            return "col";
        }
    }

    private MysqlPacket readPacket() throws IOException {
        byte[] header = new byte[4];
        readFully(in, header);
        int length = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8) | ((header[2] & 0xFF) << 16);
        int seq = header[3] & 0xFF;
        byte[] payload = new byte[length];
        readFully(in, payload);

        MysqlPacket p = new MysqlPacket();
        p.length = length;
        p.sequenceId = seq;
        p.payload = payload;
        this.sequenceId = seq;
        return p;
    }

    private void sendPacket(byte[] payload) throws IOException {
        byte[] header = new byte[4];
        int len = payload.length;
        header[0] = (byte) (len & 0xFF);
        header[1] = (byte) ((len >> 8) & 0xFF);
        header[2] = (byte) ((len >> 16) & 0xFF);
        header[3] = (byte) (sequenceId & 0xFF);

        out.write(header);
        out.write(payload);
        out.flush();
    }

    private void readFully(InputStream s, byte[] b) throws IOException {
        int total = 0;
        while (total < b.length) {
            int r = s.read(b, total, b.length - total);
            if (r < 0) throw new EOFException("Connection closed by server");
            total += r;
        }
    }

    private static long[] readLenEnc(byte[] data, int offset) {
        if (offset >= data.length) return new long[]{0, 1};
        int first = data[offset] & 0xFF;
        if (first < 0xFB) {
            return new long[]{first, 1};
        } else if (first == 0xFC) {
            long val = (data[offset + 1] & 0xFF) | ((data[offset + 2] & 0xFF) << 8);
            return new long[]{val, 3};
        } else if (first == 0xFD) {
            long val = (data[offset + 1] & 0xFF) | ((data[offset + 2] & 0xFF) << 8) | ((data[offset + 3] & 0xFF) << 16);
            return new long[]{val, 4};
        } else if (first == 0xFE) {
            long val = 0;
            for (int i = 0; i < 8; i++) {
                val |= ((long) (data[offset + 1 + i] & 0xFF)) << (8 * i);
            }
            return new long[]{val, 9};
        }
        return new long[]{0, 1};
    }

    private static String parseErrPacket(byte[] payload) {
        if (payload.length < 3) return "Unknown error";
        int errCode = (payload[1] & 0xFF) | ((payload[2] & 0xFF) << 8);
        int pos = 3;
        String sqlState = "";
        if (pos < payload.length && payload[pos] == '#') {
            pos++;
            if (pos + 5 <= payload.length) {
                sqlState = new String(payload, pos, 5, StandardCharsets.UTF_8);
                pos += 5;
            }
        }
        String msg = new String(payload, pos, payload.length - pos, StandardCharsets.UTF_8);
        return "ERROR " + errCode + " (" + (sqlState.isEmpty() ? "HY000" : sqlState) + "): " + msg;
    }

    private static int parseErrCode(byte[] payload) {
        if (payload.length < 3) return 0;
        return (payload[1] & 0xFF) | ((payload[2] & 0xFF) << 8);
    }

    private static byte[] scrambleNativePassword(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] passBytes = password.getBytes(StandardCharsets.UTF_8);
            byte[] stage1 = md.digest(passBytes);
            md.reset();
            byte[] stage2 = md.digest(stage1);
            md.reset();
            md.update(salt);
            md.update(stage2);
            byte[] stage3 = md.digest();

            byte[] out = new byte[stage1.length];
            for (int i = 0; i < stage1.length; i++) {
                out[i] = (byte) (stage1[i] ^ stage3[i]);
            }
            return out;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static void write4(ByteArrayOutputStream b, int val) {
        b.write(val & 0xFF);
        b.write((val >> 8) & 0xFF);
        b.write((val >> 16) & 0xFF);
        b.write((val >> 24) & 0xFF);
    }

    @Override
    public void close() {
        try {
            if (out != null) {
                out.write(new byte[]{1, 0, 0, (byte) (sequenceId + 1), COM_QUIT});
                out.flush();
            }
        } catch (Exception ignored) {}
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
    }
}
