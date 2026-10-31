package com.mysqllab;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Foreground service that displays a persistent notification in the status bar
 * so the MySQL 8.0 server continues running in the background without being killed.
 */
public class MysqlForegroundService extends Service {

    private static final String TAG = "MysqlFGS";
    public static final String ACTION_START = "com.mysqllab.action.START";
    public static final String ACTION_STOP = "com.mysqllab.action.STOP";
    private static final String CHANNEL_ID = "mysql_server_channel_v2";
    private static final int NOTIF_ID = 3306;

    public static void start(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, MysqlForegroundService.class);
            intent.setAction(ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Log.i(TAG, "Requested MysqlForegroundService to start");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start foreground service: " + t.getMessage(), t);
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, MysqlForegroundService.class);
            intent.setAction(ACTION_STOP);
            context.startService(intent);
            Log.i(TAG, "Requested MysqlForegroundService to stop");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to stop foreground service: " + t.getMessage(), t);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            Log.i(TAG, "Stopping foreground service notification");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            createChannelIfNeeded();
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notification, 0);
            } else {
                startForeground(NOTIF_ID, notification);
            }
            Log.i(TAG, "startForeground succeeded with NOTIF_ID=" + NOTIF_ID);
        } catch (Throwable t) {
            Log.e(TAG, "Error in onStartCommand startForeground: " + t.getMessage(), t);
        }

        return START_STICKY;
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "MySQL Server 运行状态",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("保持 MySQL 8.0 服务在后台稳定运行");
                channel.setShowBadge(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent clickIntent = new Intent(this, MainActivity.class);
        clickIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                clickIntent,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("MySQL 8.0 服务器")
                .setContentText("mysql服务器已启动，点击管理")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= 21) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
            builder.setCategory(Notification.CATEGORY_SERVICE);
        }

        return builder.build();
    }
}
