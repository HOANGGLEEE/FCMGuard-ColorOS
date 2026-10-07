package com.reed.fcmguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!GuardPrefs.isEnabled(context)) return;

        FcmReconnect.kick(context, Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                ? "boot" : "package_replaced");

        Intent service = new Intent(context, GuardService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    GuardPrefs.usePersistentNotification(context)) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Throwable ignored) {
        }
    }
}
