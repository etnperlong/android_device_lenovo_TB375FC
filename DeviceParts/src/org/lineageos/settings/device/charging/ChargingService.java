/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.charging;

import android.app.ActivityTaskManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.HardwarePropertiesManager;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.lineageos.settings.device.R;
import org.lineageos.settings.device.perf.PerformanceAppActivity;

import java.util.Objects;
import java.util.Set;

/**
 * Bypass charging: stops charging the battery while the adapter keeps powering the tablet
 * (ChargingToggle), for two reasons.
 *
 * - Apps chosen in the performance profiles: while such an app is in front (screen on and
 *   unlocked, as for the profiles) and the charger is connected. Stock ZUI's game assistant
 *   offers this per game ("Bypass charging", manual mode). It stops when the battery falls to
 *   ChargingSettings.BYPASS_MIN_LEVEL, with a toast and a warning, and stays off for that app
 *   until it leaves the front or the charger is disconnected; stock has no such floor. The
 *   same applies to the game of a DerpFest GameSpace session if that is turned on
 *   (GameSpaceReceiver).
 * - Charging heat protection: while charging, at or above the minimum level and at or above the
 *   threshold temperature. Stock's game assistant does this for games only ("Smart bypass
 *   charging"): the same defaults (40%, 39/41/43 °C), the temperature sampled every 5 s, and a
 *   change at most every HEAT_SWITCH_INTERVAL_MS. Here it applies to everything, as charging
 *   protection.
 *
 * The battery temperature comes from the thermal HAL (HardwarePropertiesManager), or from the
 * battery broadcast if the HAL has none.
 */
public class ChargingService extends Service {
    private static final String TAG = "ChargingService";

    private static final String CHANNEL_BYPASS = "bypass_charging";
    private static final String CHANNEL_WARNINGS = "charging_warnings";
    // PenService uses 1 and KeyboardService 2.
    private static final int NOTIFICATION_BYPASS = 3;
    private static final int NOTIFICATION_LOW_BATTERY = 4;
    private static final int NOTIFICATION_HEAT = 5;
    private static final long BYPASS_NOTIFICATION_TIMEOUT_MS = 10_000;

    /** Coalesces the burst of task stack callbacks of one transition. */
    private static final long UPDATE_DELAY_MS = 50;
    private static final long HEAT_SAMPLE_INTERVAL_MS = 5_000;
    private static final long HEAT_SWITCH_INTERVAL_MS = 5 * 60_000;

    private HandlerThread mThread;
    private Handler mHandler;
    private Handler mMainHandler;
    private ChargingToggle mToggle;
    private KeyguardManager mKeyguardManager;
    private PowerManager mPowerManager;
    private HardwarePropertiesManager mHardwareProperties;
    private NotificationManager mNotificationManager;
    private ContentObserver mSettingsObserver;
    private BroadcastReceiver mReceiver;
    private TaskStackListener mTaskStackListener;

    // Handler thread only.
    private Set<String> mBypassApps;
    private boolean mGameSpaceEnabled;
    /** The game of the running GameSpace session, null if none. */
    private String mGameSpaceGame;
    private boolean mHeatEnabled;
    private int mHeatMinLevel;
    private int mHeatThresholdC;
    private boolean mPlugged;
    private int mLevel = -1;
    /** From the battery broadcast, in °C; NaN if unknown. */
    private float mBroadcastTemperature = Float.NaN;

    /** The app bypass charging is on for, null if none. */
    private String mBypassApp;
    /** The app whose bypass charging stopped at the minimum level. */
    private String mLowBatteryApp;
    private boolean mHeatActive;
    /** Uptime of the last heat protection change, 0 if none since the charger was connected. */
    private long mHeatSwitchTime;
    /** What the HAL was told: charging off (bypass) or on; null if unknown. */
    private Boolean mAppliedBypass;

    private final Runnable mUpdate = this::update;

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mMainHandler = new Handler(Looper.getMainLooper());
        mHandler.post(this::start);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent != null ? intent.getAction() : null;
        if (GameSpaceReceiver.ACTION_GAME_START.equals(action)) {
            final String game = intent.getStringExtra(GameSpaceReceiver.EXTRA_PACKAGE_NAME);
            mHandler.post(() -> onGameSpaceSession(game));
        } else if (GameSpaceReceiver.ACTION_GAME_STOP.equals(action)) {
            mHandler.post(() -> onGameSpaceSession(null));
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mHandler.post(this::stop);
        mThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void start() {
        mToggle = new ChargingToggle(() -> mHandler.post(this::onHalDied));
        mKeyguardManager = getSystemService(KeyguardManager.class);
        mPowerManager = getSystemService(PowerManager.class);
        mHardwareProperties = getSystemService(HardwarePropertiesManager.class);
        mNotificationManager = getSystemService(NotificationManager.class);
        createChannels();

        final ContentResolver cr = getContentResolver();
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                readSettings();
                scheduleUpdate();
            }
        };
        for (String key : new String[] {ChargingSettings.KEY_BYPASS_APPS,
                ChargingSettings.KEY_BYPASS_GAMESPACE,
                ChargingSettings.KEY_HEAT_PROTECTION, ChargingSettings.KEY_HEAT_MIN_LEVEL,
                ChargingSettings.KEY_HEAT_THRESHOLD}) {
            cr.registerContentObserver(Settings.Secure.getUriFor(key), false, mSettingsObserver);
        }
        readSettings();

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    readBattery(intent);
                }
                scheduleUpdate();
            }
        };
        final IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        // The battery broadcast is sticky: this also reads the current state.
        final Intent battery = registerReceiver(mReceiver, filter, null, mHandler,
                Context.RECEIVER_NOT_EXPORTED);
        if (battery != null) {
            readBattery(battery);
        }

        // Called on binder threads.
        mTaskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                mHandler.post(ChargingService.this::scheduleUpdate);
            }

            @Override
            public void onTaskFocusChanged(int taskId, boolean focused) {
                mHandler.post(ChargingService.this::scheduleUpdate);
            }
        };
        try {
            ActivityTaskManager.getService().registerTaskStackListener(mTaskStackListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot follow the task stack", e);
        }

        update();
    }

    private void stop() {
        if (mTaskStackListener != null) {
            try {
                ActivityTaskManager.getService().unregisterTaskStackListener(mTaskStackListener);
            } catch (RemoteException e) {
                // system_server is gone, and this process with it.
            }
            mTaskStackListener = null;
        }
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        if (mSettingsObserver != null) {
            getContentResolver().unregisterContentObserver(mSettingsObserver);
            mSettingsObserver = null;
        }
        mHandler.removeCallbacksAndMessages(null);
        mNotificationManager.cancel(NOTIFICATION_BYPASS);
        mNotificationManager.cancel(NOTIFICATION_HEAT);
        mBypassApp = null;
        mHeatActive = false;
        apply(false);
    }

    private void createChannels() {
        final NotificationChannel bypass = new NotificationChannel(CHANNEL_BYPASS,
                getString(R.string.bypass_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT);
        bypass.setSound(null, null);
        bypass.enableVibration(false);
        bypass.setShowBadge(false);
        final NotificationChannel warnings = new NotificationChannel(CHANNEL_WARNINGS,
                getString(R.string.charging_warning_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        warnings.setShowBadge(false);
        mNotificationManager.createNotificationChannel(bypass);
        mNotificationManager.createNotificationChannel(warnings);
    }

    private void readSettings() {
        final ContentResolver cr = getContentResolver();
        mBypassApps = ChargingSettings.getBypassApps(cr);
        mGameSpaceEnabled = ChargingSettings.isGameSpaceBypassEnabled(cr);
        mHeatEnabled = ChargingSettings.isHeatProtectionEnabled(cr);
        mHeatMinLevel = ChargingSettings.getHeatMinLevel(cr);
        mHeatThresholdC =
                ChargingSettings.HEAT_THRESHOLDS_C[ChargingSettings.getHeatThresholdIndex(cr)];
    }

    private void readBattery(Intent intent) {
        mPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        mLevel = level >= 0 && scale > 0 ? level * 100 / scale : -1;
        final int tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        mBroadcastTemperature = tenths != Integer.MIN_VALUE ? tenths / 10f : Float.NaN;
    }

    private void scheduleUpdate() {
        mHandler.removeCallbacks(mUpdate);
        mHandler.postDelayed(mUpdate, UPDATE_DELAY_MS);
    }

    private void update() {
        mHandler.removeCallbacks(mUpdate);
        updateAppBypass();
        updateHeatProtection();
        apply(mBypassApp != null || mHeatActive);
        // While the heat protection can act, sample the temperature; with the screen off the
        // battery broadcasts still wake this up.
        if (mPlugged && mHeatEnabled) {
            mHandler.postDelayed(mUpdate, HEAT_SAMPLE_INTERVAL_MS);
        }
    }

    private void onGameSpaceSession(String game) {
        mGameSpaceGame = game;
        scheduleUpdate();
    }

    /** Whether the app uses bypass charging: chosen for it, or the game of a GameSpace session. */
    private boolean wantsBypass(String app) {
        return mBypassApps.contains(app) || (mGameSpaceEnabled && app.equals(mGameSpaceGame));
    }

    private void updateAppBypass() {
        final String app = mPlugged ? foregroundApp() : null;
        if (!Objects.equals(app, mLowBatteryApp)) {
            mLowBatteryApp = null;
        }
        String bypassApp = null;
        if (app != null && wantsBypass(app) && !app.equals(mLowBatteryApp) && mLevel >= 0) {
            if (mLevel > ChargingSettings.BYPASS_MIN_LEVEL) {
                bypassApp = app;
            } else if (app.equals(mBypassApp)) {
                mLowBatteryApp = app;
                Log.i(TAG, app + ": battery at " + mLevel + "%, bypass charging off");
                warnLowBattery(app);
            }
        }
        if (Objects.equals(bypassApp, mBypassApp)) {
            return;
        }
        mBypassApp = bypassApp;
        if (bypassApp != null) {
            Log.i(TAG, bypassApp + ": bypass charging on");
            announceAppBypass(bypassApp);
        } else {
            mNotificationManager.cancel(NOTIFICATION_BYPASS);
        }
    }

    /** The app of the focused task while the screen is on and unlocked, or null. */
    private String foregroundApp() {
        if (!mPowerManager.isInteractive() || mKeyguardManager.isKeyguardLocked()) {
            return null;
        }
        final ActivityTaskManager.RootTaskInfo task;
        try {
            task = ActivityTaskManager.getService().getFocusedRootTaskInfo();
        } catch (RemoteException e) {
            return null;
        }
        return task != null && task.topActivity != null
                ? task.topActivity.getPackageName() : null;
    }

    private void updateHeatProtection() {
        if (!mHeatEnabled || !mPlugged) {
            mHeatSwitchTime = 0;
            setHeatActive(false, Float.NaN);
            return;
        }
        final float temperature = readTemperature();
        final boolean hot = mLevel >= mHeatMinLevel && temperature >= mHeatThresholdC;
        if (hot == mHeatActive) {
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        if (mHeatSwitchTime != 0 && now - mHeatSwitchTime < HEAT_SWITCH_INTERVAL_MS) {
            return;
        }
        mHeatSwitchTime = now;
        Log.i(TAG, "Heat protection " + (hot ? "on" : "off") + ": " + temperature + " °C, "
                + mLevel + "%");
        setHeatActive(hot, temperature);
    }

    private void setHeatActive(boolean active, float temperature) {
        if (active == mHeatActive) {
            return;
        }
        mHeatActive = active;
        if (active) {
            warnHeat(temperature);
        } else {
            mNotificationManager.cancel(NOTIFICATION_HEAT);
        }
    }

    /** The battery temperature in °C, the highest the thermal HAL reports, NaN if unknown. */
    private float readTemperature() {
        float highest = Float.NaN;
        try {
            for (float value : mHardwareProperties.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_BATTERY,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT)) {
                if (!Float.isNaN(value)
                        && value != HardwarePropertiesManager.UNDEFINED_TEMPERATURE
                        && (Float.isNaN(highest) || value > highest)) {
                    highest = value;
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "No battery temperature from the thermal HAL", e);
        }
        return Float.isNaN(highest) ? mBroadcastTemperature : highest;
    }

    private void apply(boolean bypass) {
        if (mAppliedBypass != null && mAppliedBypass == bypass) {
            return;
        }
        if (mToggle.setChargingEnabled(!bypass)) {
            mAppliedBypass = bypass;
        } else {
            mAppliedBypass = null;
        }
    }

    /** A restarted HAL keeps the driver state; forget the binder and apply everything again. */
    private void onHalDied() {
        Log.w(TAG, "Health HAL died");
        mToggle.reset();
        mAppliedBypass = null;
        scheduleUpdate();
    }

    private CharSequence appLabel(String pkg) {
        final PackageManager pm = getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private PendingIntent appSettings(String pkg, CharSequence label) {
        return PendingIntent.getActivity(this, 0,
                new Intent(this, PerformanceAppActivity.class)
                        .putExtra(PerformanceAppActivity.EXTRA_PACKAGE, pkg)
                        .putExtra(PerformanceAppActivity.EXTRA_LABEL, label.toString())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void announceAppBypass(String pkg) {
        final CharSequence label = appLabel(pkg);
        showToast(getString(R.string.bypass_on_toast));
        mNotificationManager.notify(NOTIFICATION_BYPASS,
                new Notification.Builder(this, CHANNEL_BYPASS)
                        .setSmallIcon(R.drawable.ic_bypass_charging)
                        .setContentTitle(getString(mBypassApps.contains(pkg)
                                ? R.string.bypass_on_title : R.string.bypass_gamespace_on_title,
                                label))
                        .setContentText(getString(R.string.bypass_on_text,
                                ChargingSettings.BYPASS_MIN_LEVEL))
                        .setCategory(Notification.CATEGORY_STATUS)
                        .setContentIntent(appSettings(pkg, label))
                        .setAutoCancel(true)
                        .setTimeoutAfter(BYPASS_NOTIFICATION_TIMEOUT_MS)
                        .build());
    }

    private void warnLowBattery(String pkg) {
        showToast(getString(R.string.bypass_low_battery_toast));
        mNotificationManager.cancel(NOTIFICATION_BYPASS);
        final String text = getString(R.string.bypass_low_battery_text,
                ChargingSettings.BYPASS_MIN_LEVEL);
        mNotificationManager.notify(NOTIFICATION_LOW_BATTERY,
                new Notification.Builder(this, CHANNEL_WARNINGS)
                        .setSmallIcon(R.drawable.ic_charging_warning)
                        .setContentTitle(getString(R.string.bypass_low_battery_title))
                        .setContentText(text)
                        .setStyle(new Notification.BigTextStyle().bigText(text))
                        .setCategory(Notification.CATEGORY_STATUS)
                        .setContentIntent(appSettings(pkg, appLabel(pkg)))
                        .setAutoCancel(true)
                        .build());
    }

    private void warnHeat(float temperature) {
        final String text = getString(R.string.heat_protection_notification_text,
                getString(R.string.heat_protection_temperature, temperature));
        mNotificationManager.notify(NOTIFICATION_HEAT,
                new Notification.Builder(this, CHANNEL_WARNINGS)
                        .setSmallIcon(R.drawable.ic_charging_warning)
                        .setContentTitle(getString(R.string.heat_protection_notification_title))
                        .setContentText(text)
                        .setStyle(new Notification.BigTextStyle().bigText(text))
                        .setCategory(Notification.CATEGORY_STATUS)
                        .setContentIntent(PendingIntent.getActivity(this, 0,
                                new Intent(this, HeatProtectionActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                PendingIntent.FLAG_IMMUTABLE))
                        .build());
    }

    private void showToast(String text) {
        mMainHandler.post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }
}
