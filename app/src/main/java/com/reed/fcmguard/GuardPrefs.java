package com.reed.fcmguard;

import android.content.Context;
import android.content.SharedPreferences;

public final class GuardPrefs {
    private static final String PREFS = "guard_state";
    private static final String ENABLED = "enabled";
    private static final String PERSISTENT = "persistent_notification";
    private static final String LAST_RECONNECT = "last_reconnect_ms";
    private static final String LAST_REASON = "last_reconnect_reason";
    private static final String RECONNECT_COUNT = "reconnect_count";

    private GuardPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(ENABLED, enabled).apply();
    }

    public static boolean usePersistentNotification(Context context) {
        return prefs(context).getBoolean(PERSISTENT, true);
    }

    public static void setPersistentNotification(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(PERSISTENT, enabled).apply();
    }

    public static long lastReconnectMs(Context context) {
        return prefs(context).getLong(LAST_RECONNECT, 0L);
    }

    public static String lastReconnectReason(Context context) {
        return prefs(context).getString(LAST_REASON, "never");
    }

    public static int reconnectCount(Context context) {
        return prefs(context).getInt(RECONNECT_COUNT, 0);
    }

    public static void recordReconnect(Context context, String reason) {
        SharedPreferences p = prefs(context);
        p.edit()
                .putLong(LAST_RECONNECT, System.currentTimeMillis())
                .putString(LAST_REASON, reason == null ? "unknown" : reason)
                .putInt(RECONNECT_COUNT, p.getInt(RECONNECT_COUNT, 0) + 1)
                .apply();
    }
}
