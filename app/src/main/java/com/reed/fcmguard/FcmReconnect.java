package com.reed.fcmguard;

import android.content.Context;
import android.content.Intent;

/**
 * Best-effort FCM/MCS reconnect kick without ADB, Shizuku or root.
 *
 * This does not start Google Play services private components directly. It sends the
 * heartbeat intents that current FCM Guard already uses and lets Google Play services
 * decide whether its long-lived transport needs to reconnect.
 */
public final class FcmReconnect {
    private static final String ACTION_GTALK_HEARTBEAT =
            "com.google.android.intent.action.GTALK_HEARTBEAT";
    private static final String ACTION_MCS_HEARTBEAT =
            "com.google.android.intent.action.MCS_HEARTBEAT";

    private static final String[] TARGET_PACKAGES = {
            "com.google.android.gms",
            "com.google.android.gsf"
    };

    private FcmReconnect() {}

    public static boolean kick(Context context) {
        return kick(context, "manual");
    }

    public static boolean kick(Context context, String reason) {
        boolean sent = false;
        for (String target : TARGET_PACKAGES) {
            try {
                context.sendBroadcast(new Intent(ACTION_GTALK_HEARTBEAT).setPackage(target));
                context.sendBroadcast(new Intent(ACTION_MCS_HEARTBEAT).setPackage(target));
                sent = true;
            } catch (Throwable ignored) {
            }
        }
        if (sent) GuardPrefs.recordReconnect(context, reason);
        return sent;
    }
}
