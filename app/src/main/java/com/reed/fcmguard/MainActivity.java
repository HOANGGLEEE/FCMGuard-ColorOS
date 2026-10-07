package com.reed.fcmguard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 41;

    private TextView statusHeadline;
    private TextView statusText;
    private TextView currentValueText;
    private TextView fcmAppsStatusText;
    private LinearLayout fcmAppsContainer;
    private Button scanFcmAppsBtn;
    private Switch protectionSwitch;
    private Switch notificationSwitch;
    private RadioGroup appearanceGroup;
    private boolean suppressSwitchCallbacks;
    private boolean suppressAppearanceCallbacks;
    private boolean fcmListExpanded;
    private List<FcmAppScanner.AppEntry> scannedFcmApps;

    @Override protected void attachBaseContext(Context newBase) {
        Context localized = LocaleHelper.apply(newBase);
        super.attachBaseContext(ThemeHelper.apply(localized));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocaleHelper.migrateLegacyPreference(this);
        setContentView(R.layout.activity_main);

        bindViews();
        setupLanguageButton();
        setupAppearance();
        setupSwitches();
        bindActions();
        refreshStatus(null);
    }

    @Override protected void onResume() {
        super.onResume();
        if (GuardPrefs.isEnabled(this)) {
            startProtectionService();
        }
        refreshStatus(null);

        if (fcmListExpanded) {
            scanFcmApps();
        }
    }

    private void bindViews() {
        statusHeadline = findViewById(R.id.statusHeadline);
        statusText = findViewById(R.id.statusText);
        currentValueText = findViewById(R.id.currentValueText);
        fcmAppsStatusText = findViewById(R.id.fcmAppsStatusText);
        fcmAppsContainer = findViewById(R.id.fcmAppsContainer);
        scanFcmAppsBtn = findViewById(R.id.scanFcmAppsBtn);
        protectionSwitch = findViewById(R.id.protectionSwitch);
        notificationSwitch = findViewById(R.id.notificationSwitch);
        appearanceGroup = findViewById(R.id.appearanceGroup);
    }

    private void setupLanguageButton() {
        Button language = findViewById(R.id.languageButton);
        language.setText(R.string.language);
        language.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    Intent intent = new Intent(
                            Settings.ACTION_APP_LOCALE_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                    return;
                } catch (Throwable ignored) {
                }
            }
            toast(getString(R.string.language_system_hint));
        });
    }

    private void setupAppearance() {
        String mode = ThemeHelper.getMode(this);
        suppressAppearanceCallbacks = true;
        if (ThemeHelper.MODE_DARK.equals(mode)) {
            appearanceGroup.check(R.id.themeDark);
        } else if (ThemeHelper.MODE_LIGHT.equals(mode)) {
            appearanceGroup.check(R.id.themeLight);
        } else {
            appearanceGroup.check(R.id.themeSystem);
        }
        suppressAppearanceCallbacks = false;

        appearanceGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (suppressAppearanceCallbacks) return;

            String next;
            if (checkedId == R.id.themeDark) {
                next = ThemeHelper.MODE_DARK;
            } else if (checkedId == R.id.themeLight) {
                next = ThemeHelper.MODE_LIGHT;
            } else {
                next = ThemeHelper.MODE_SYSTEM;
            }

            if (!next.equals(ThemeHelper.getMode(this))) {
                ThemeHelper.setMode(this, next);
                recreate();
            }
        });
    }

    private void setupSwitches() {
        suppressSwitchCallbacks = true;
        protectionSwitch.setChecked(GuardPrefs.isEnabled(this));
        notificationSwitch.setChecked(GuardPrefs.usePersistentNotification(this));
        suppressSwitchCallbacks = false;

        protectionSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (suppressSwitchCallbacks) return;

            GuardPrefs.setEnabled(this, checked);
            if (checked) {
                if (GuardPrefs.usePersistentNotification(this)) {
                    requestNotificationAccessIfNeeded();
                }
                startProtectionService();
                FcmReconnect.kick(this, "manual_enable");
                toast(getString(R.string.service_started));
            } else {
                stopService(new Intent(this, GuardService.class));
                toast(getString(R.string.service_stopped));
            }
            refreshStatus(null);
        });

        notificationSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (suppressSwitchCallbacks) return;

            GuardPrefs.setPersistentNotification(this, checked);
            if (checked) requestNotificationAccessIfNeeded();
            if (GuardPrefs.isEnabled(this)) startProtectionService();
            refreshStatus(null);
        });
    }

    private void bindActions() {
        findViewById(R.id.wakeBtn).setOnClickListener(v -> {
            boolean sent = FcmReconnect.kick(this, "manual");
            toast(sent ? getString(R.string.wake_sent) : getString(R.string.wake_failed));
            refreshStatus(sent ? getString(R.string.wake_sent) : null);
        });

        findViewById(R.id.diagBtn).setOnClickListener(v -> openFcmDiagnostics());

        findViewById(R.id.openAutostartBtn).setOnClickListener(v -> {
            if (!ColorOsSettings.openAutoLaunch(this)) {
                toast(getString(R.string.coloros_settings_unavailable));
            }
        });

        findViewById(R.id.openAssociateBtn).setOnClickListener(v -> {
            if (!ColorOsSettings.openAssociatedLaunch(this)) {
                toast(getString(R.string.coloros_settings_unavailable));
            }
        });

        findViewById(R.id.openBatteryBtn).setOnClickListener(v -> {
            if (!ColorOsSettings.openPowerManager(this)) {
                toast(getString(R.string.coloros_settings_unavailable));
            }
        });

        scanFcmAppsBtn.setOnClickListener(v -> {
            if (fcmListExpanded) {
                fcmListExpanded = false;
                fcmAppsContainer.removeAllViews();
                fcmAppsContainer.setVisibility(View.GONE);
                fcmAppsStatusText.setText(R.string.fcm_apps_not_scanned);
                scanFcmAppsBtn.setText(R.string.scan_fcm_apps);
            } else {
                scanFcmApps();
            }
        });
    }

    private void startProtectionService() {
        Intent service = new Intent(this, GuardService.class);
        try {
            boolean persistent = GuardPrefs.usePersistentNotification(this);
            if (persistent) GuardService.ensureNotificationChannel(this);

            if (persistent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            } else {
                startService(service);
            }
        } catch (Throwable t) {
            toast(t.getClass().getSimpleName());
        }
    }

    private void requestNotificationAccessIfNeeded() {
        GuardService.ensureNotificationChannel(this);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    private void refreshStatus(String firstLine) {
        boolean gms = isInstalled("com.google.android.gms");
        boolean enabled = GuardPrefs.isEnabled(this);
        boolean foregroundVisible =
                enabled &&
                GuardPrefs.usePersistentNotification(this) &&
                GuardService.canShowPersistentNotification(this);

        suppressSwitchCallbacks = true;
        protectionSwitch.setChecked(enabled);
        notificationSwitch.setChecked(GuardPrefs.usePersistentNotification(this));
        suppressSwitchCallbacks = false;

        if (gms && enabled) {
            statusHeadline.setText(R.string.status_protected);
            statusHeadline.setTextColor(getResources().getColor(R.color.green));
        } else if (gms) {
            statusHeadline.setText(R.string.status_ready);
            statusHeadline.setTextColor(getResources().getColor(R.color.blue));
        } else {
            statusHeadline.setText(R.string.status_attention);
            statusHeadline.setTextColor(getResources().getColor(R.color.red));
        }

        StringBuilder s = new StringBuilder();
        if (firstLine != null && !firstLine.isEmpty()) {
            s.append("✓ ").append(firstLine).append('\n');
        }
        s.append(gms ? "✓ " : "✗ ")
                .append(getString(gms ? R.string.gms_installed : R.string.gms_missing))
                .append('\n');

        Boolean doze = isDozeExempt("com.google.android.gms");
        if (doze == null) {
            s.append("? ").append(getString(R.string.gms_doze_unknown)).append('\n');
        } else {
            s.append(doze ? "✓ " : "⚠ ")
                    .append(getString(doze
                            ? R.string.gms_doze_exempt
                            : R.string.gms_doze_not_exempt))
                    .append('\n');
        }

        s.append(enabled ? "✓ " : "○ ")
                .append(getString(enabled
                        ? R.string.status_enabled
                        : R.string.status_disabled))
                .append('\n');

        s.append(foregroundVisible ? "✓ " : "○ ")
                .append(getString(foregroundVisible
                        ? R.string.notification_mode_foreground
                        : R.string.notification_mode_quiet))
                .append('\n');

        s.append("? ").append(getString(R.string.fcm_connection_unverified));
        statusText.setText(s.toString());

        long last = GuardPrefs.lastReconnectMs(this);
        if (last <= 0) {
            currentValueText.setText(R.string.last_reconnect_never);
        } else {
            String when = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.MEDIUM).format(new Date(last));
            currentValueText.setText(getString(
                    R.string.last_reconnect_value,
                    when,
                    GuardPrefs.lastReconnectReason(this),
                    GuardPrefs.reconnectCount(this)));
        }
    }

    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Boolean isDozeExempt(String packageName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(packageName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void scanFcmApps() {
        scannedFcmApps = FcmAppScanner.scan(this);
        fcmListExpanded = true;
        fcmAppsContainer.removeAllViews();
        fcmAppsContainer.setVisibility(View.VISIBLE);
        scanFcmAppsBtn.setText(R.string.collapse_fcm_apps);

        if (scannedFcmApps == null || scannedFcmApps.isEmpty()) {
            fcmAppsStatusText.setText(R.string.no_fcm_apps);
            return;
        }

        fcmAppsStatusText.setText(getString(
                R.string.fcm_apps_found,
                scannedFcmApps.size()));

        for (FcmAppScanner.AppEntry app : scannedFcmApps) {
            addFcmAppRow(app);
        }
    }

    private void addFcmAppRow(FcmAppScanner.AppEntry app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        row.setClickable(true);
        row.setOnClickListener(v -> openPackageDetails(app.packageName));

        TextView name = new TextView(this);
        name.setText(app.label);
        name.setTextColor(getResources().getColor(R.color.text_primary));
        name.setTextSize(14f);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView pkg = new TextView(this);
        pkg.setText(app.packageName);
        pkg.setTextColor(getResources().getColor(R.color.text_secondary));
        pkg.setTextSize(10.5f);
        pkg.setPadding(0, dp(2), 0, 0);

        TextView details = new TextView(this);
        details.setText(buildAppStatus(app.packageName));
        details.setTextColor(getResources().getColor(R.color.text_secondary));
        details.setTextSize(11.5f);
        details.setPadding(0, dp(5), 0, 0);

        row.addView(name);
        row.addView(pkg);
        row.addView(details);
        fcmAppsContainer.addView(row);

        View divider = new View(this);
        divider.setBackgroundColor(getResources().getColor(R.color.divider));
        fcmAppsContainer.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private String buildAppStatus(String packageName) {
        StringBuilder out = new StringBuilder();

        if (Build.VERSION.SDK_INT >= 33) {
            boolean notificationGranted = getPackageManager().checkPermission(
                    Manifest.permission.POST_NOTIFICATIONS,
                    packageName) == PackageManager.PERMISSION_GRANTED;
            out.append(notificationGranted ? "✓ " : "⚠ ")
                    .append(getString(notificationGranted
                            ? R.string.notification_permission_ok
                            : R.string.notification_permission_missing));
        } else {
            out.append("✓ ").append(getString(R.string.notification_permission_legacy));
        }

        Boolean doze = isDozeExempt(packageName);
        if (doze != null) {
            out.append("  ·  ")
                    .append(doze ? "✓ " : "○ ")
                    .append(getString(doze
                            ? R.string.app_doze_exempt
                            : R.string.app_doze_normal));
        }
        return out.toString();
    }

    private void openPackageDetails(String packageName) {
        try {
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)));
        } catch (Throwable ignored) {
            toast(getString(R.string.app_settings_unavailable));
        }
    }

    private void openFcmDiagnostics() {
        String[] knownActivities = {
                "com.google.android.gms.gcm.GcmDiagnostics",
                "com.google.android.gms.gtalkservice.diagnostics.GTalkServiceDiagnostics"
        };

        for (String className : knownActivities) {
            if (startGooglePlayServicesActivity(className)) return;
        }

        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    "com.google.android.gms",
                    PackageManager.GET_ACTIVITIES);
            if (info.activities != null) {
                for (ActivityInfo activity : info.activities) {
                    String n = activity.name == null ? "" : activity.name.toLowerCase();
                    if (n.contains("diagnostic") && n.contains("gcm")) {
                        if (startGooglePlayServicesActivity(activity.name)) return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        toast(getString(R.string.diagnostics_unavailable));
    }

    private boolean startGooglePlayServicesActivity(String className) {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.google.android.gms", className);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
