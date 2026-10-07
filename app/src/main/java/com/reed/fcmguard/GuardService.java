package com.reed.fcmguard;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class GuardService extends Service {
    private static final String CHANNEL_ID = "fcm_guard_coloros_v1";
    private static final int NOTIFICATION_ID = 426;

    private static final long FALLBACK_INTERVAL_MS = 30L * 60L * 1000L;
    private static final long NETWORK_RECONNECT_MIN_GAP_MS = 20L * 1000L;
    private static final long USER_PRESENT_MIN_GAP_MS = 15L * 60L * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private BroadcastReceiver userPresentReceiver;
    private boolean foreground;

    private final Runnable fallbackCheck = new Runnable() {
        @Override public void run() {
            long age = System.currentTimeMillis() - GuardPrefs.lastReconnectMs(GuardService.this);
            if (age >= FALLBACK_INTERVAL_MS - 60_000L) {
                reconnect("fallback_30m");
            }
            handler.postDelayed(this, FALLBACK_INTERVAL_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        applyExecutionMode();
        registerNetworkMonitor();
        registerUserPresentReceiver();
        handler.postDelayed(fallbackCheck, FALLBACK_INTERVAL_MS);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        GuardPrefs.setEnabled(this, true);
        applyExecutionMode();

        long age = System.currentTimeMillis() - GuardPrefs.lastReconnectMs(this);
        if (age > 5_000L) {
            reconnect("service_start");
        }

        handler.removeCallbacks(fallbackCheck);
        handler.postDelayed(fallbackCheck, FALLBACK_INTERVAL_MS);
        return START_STICKY;
    }

    private void registerNetworkMonitor() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null || networkCallback != null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                scheduleReconnect("network_available", 3_000L, NETWORK_RECONNECT_MIN_GAP_MS);
            }

            @Override public void onCapabilitiesChanged(
                    Network network, NetworkCapabilities capabilities) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    scheduleReconnect("network_changed", 3_000L, NETWORK_RECONNECT_MIN_GAP_MS);
                }
            }
        };

        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
        } catch (Throwable ignored) {
            networkCallback = null;
        }
    }

    private void registerUserPresentReceiver() {
        if (userPresentReceiver != null) return;

        userPresentReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                    long age = System.currentTimeMillis() - GuardPrefs.lastReconnectMs(context);
                    if (age >= USER_PRESENT_MIN_GAP_MS) {
                        scheduleReconnect("wake_after_sleep", 1_000L, USER_PRESENT_MIN_GAP_MS);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(userPresentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(userPresentReceiver, filter);
            }
        } catch (Throwable ignored) {
            userPresentReceiver = null;
        }
    }

    private void scheduleReconnect(
            String reason, long delayMs, long minimumGapMs) {
        handler.postDelayed(() -> {
            long age = System.currentTimeMillis() - GuardPrefs.lastReconnectMs(GuardService.this);
            if (age >= minimumGapMs) {
                reconnect(reason);
            }
        }, delayMs);
    }

    private void reconnect(String reason) {
        boolean sent = FcmReconnect.kick(this, reason);
        if (sent && foreground) {
            refreshNotification(getString(
                    R.string.notification_reconnected,
                    prettyReason(reason)));
        }
    }

    private String prettyReason(String reason) {
        if ("network_available".equals(reason) || "network_changed".equals(reason)) {
            return getString(R.string.reason_network);
        }
        if ("wake_after_sleep".equals(reason)) {
            return getString(R.string.reason_wake);
        }
        if ("fallback_30m".equals(reason)) {
            return getString(R.string.reason_fallback);
        }
        return getString(R.string.reason_start);
    }

    private void applyExecutionMode() {
        if (GuardPrefs.usePersistentNotification(this)) {
            ensureNotificationChannel(this);
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification(getString(R.string.notification_active)));
            foreground = true;
        } else {
            if (foreground) stopForeground(true);
            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFICATION_ID);
            foreground = false;
        }
    }

    public static boolean ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;

        boolean created = nm.getNotificationChannel(CHANNEL_ID) == null;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(
                context.getString(R.string.notification_channel_description));
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.enableLights(false);
        channel.setSound(null, null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        nm.createNotificationChannel(channel);
        return created;
    }

    public static boolean canShowPersistentNotification(Context context) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !nm.areNotificationsEnabled()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
            return channel != null &&
                    channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }

        return true;
    }

    private void refreshNotification(String text) {
        if (!foreground) return;
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this)
                    .setPriority(Notification.PRIORITY_LOW);
        }

        return builder
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Throwable ignored) {
            }
        }

        if (userPresentReceiver != null) {
            try {
                unregisterReceiver(userPresentReceiver);
            } catch (Throwable ignored) {
            }
        }

        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
