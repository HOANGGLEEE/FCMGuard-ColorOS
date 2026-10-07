package com.reed.fcmguard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** ColorOS/OPlus settings entry points discovered on current OPlus Battery builds. */
public final class ColorOsSettings {
    private static final String OPLUS_BATTERY = "com.oplus.battery";

    private ColorOsSettings() {}

    public static boolean openAutoLaunch(Context context) {
        Intent byAction = new Intent("com.oplus.battery.permission.startup.StartupAppListActivity");
        byAction.setPackage(OPLUS_BATTERY);
        if (launch(context, byAction)) return true;

        Intent explicit = component(
                "com.oplus.startupapp.view.StartupAppListActivity");
        if (launch(context, explicit)) return true;

        Intent optimized = new Intent("safe.intent.action.OPT_AUTO_APP_ACTIVITY");
        optimized.setPackage(OPLUS_BATTERY);
        if (launch(context, optimized)) return true;

        return openAppDetails(context);
    }

    public static boolean openAssociatedLaunch(Context context) {
        Intent byAction = new Intent("com.oplus.battery.permission.startup.AssociateStartActivity");
        byAction.setPackage(OPLUS_BATTERY);
        if (launch(context, byAction)) return true;

        if (launch(context, component(
                "com.oplus.startupapp.view.AssociateStartActivity"))) return true;

        return openAppDetails(context);
    }

    public static boolean openPowerManager(Context context) {
        Intent byAction = new Intent("com.oplus.action.powermanager");
        byAction.setPackage(OPLUS_BATTERY);
        if (launch(context, byAction)) return true;

        if (launch(context, component(
                "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity"))) return true;

        try {
            return launch(context, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Throwable ignored) {
            return openAppDetails(context);
        }
    }

    public static boolean openAppDetails(Context context) {
        return launch(context, new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.getPackageName())));
    }

    private static Intent component(String activityName) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(OPLUS_BATTERY, activityName));
        return i;
    }

    private static boolean launch(Context context, Intent intent) {
        try {
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
