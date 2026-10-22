/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import org.lineageos.settings.device.display.AppRefreshRateLimit;

import java.util.List;
import java.util.Objects;

/**
 * Tells the MediaTek power HAL which app is in front, and applies the app's performance
 * profile.
 *
 * Stock ZUI does the first part in system_server: MTK's AmsExtImpl hook calls
 * libpowerhalwrap's PowerHal_Wrap_notifyAppState on every activity resume and pause, which
 * passes it to the vendor IMtkpower_applist service; that service applies its rule for the app
 * from /vendor/etc/power_app_cfg.xml (thermal policy, FPSGO, CPU limits) and records the app's
 * pid and uid in the power HAL. Here the focused task is followed instead, and:
 *
 * - "follow vendor" (the default): the app is sent to IMtkpower_applist as stock does;
 * - "custom": the pid and uid are recorded the way IMtkpower_applist does (setSysInfo), and a
 *   power HAL lock is acquired with the chosen preset and advanced parameters; the refresh-rate
 *   limit goes to DisplayPolicyService;
 * - "off": only the pid and uid are recorded.
 *
 * The app counts as in front only while the screen is on and unlocked, like the paused
 * activity on stock. Locks are owned by this process; libpowerhal drops a lock whose owner is
 * gone (perfUserScnCheckAll, every five minutes).
 */
public class PerformanceService extends Service {
    private static final String TAG = "Performance";

    /** Coalesces the burst of task stack callbacks of one transition. */
    private static final long UPDATE_DELAY_MS = 50;
    /** The focused activity's process may still be starting; look for it again. */
    private static final long PID_RETRY_DELAY_MS = 200;
    private static final int PID_RETRIES = 10;

    /** The app in front. */
    private static final class Foreground {
        final String pkg;
        final String activity;
        final int pid;
        final int uid;

        Foreground(String pkg, String activity, int pid, int uid) {
            this.pkg = pkg;
            this.activity = activity;
            this.pid = pid;
            this.uid = uid;
        }

        boolean sameApp(Foreground other) {
            return other != null && pkg.equals(other.pkg) && pid == other.pid;
        }
    }

    private HandlerThread mThread;
    private Handler mHandler;
    private MtkPower mHal;
    private VendorAppList mVendor;
    private ActivityManager mActivityManager;
    private KeyguardManager mKeyguardManager;
    private PowerManager mPowerManager;
    private ContentObserver mSettingsObserver;
    private BroadcastReceiver mScreenReceiver;
    private TaskStackListener mTaskStackListener;

    private boolean mEnabled;
    private int mPidRetries;

    /** What is applied now: the app, its profile, and the lock of a custom profile. */
    private Foreground mApplied;
    private PerfProfile mAppliedProfile;
    private int mLockHandle;

    private final Runnable mUpdate = this::update;

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_FOREGROUND);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mHandler.post(this::start);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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
        mHal = new MtkPower(() -> mHandler.post(this::onHalDied));
        mVendor = VendorAppList.get();
        mActivityManager = getSystemService(ActivityManager.class);
        mKeyguardManager = getSystemService(KeyguardManager.class);
        mPowerManager = getSystemService(PowerManager.class);

        final ContentResolver cr = getContentResolver();
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                mEnabled = PerfSettings.isEnabled(getContentResolver());
                scheduleUpdate();
            }
        };
        cr.registerContentObserver(Settings.Secure.getUriFor(PerfSettings.KEY_ENABLED), false,
                mSettingsObserver);
        cr.registerContentObserver(Settings.Secure.getUriFor(PerfSettings.KEY_APP_PROFILES),
                false, mSettingsObserver);
        mEnabled = PerfSettings.isEnabled(cr);

        mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                scheduleUpdate();
            }
        };
        final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mScreenReceiver, filter, null, mHandler, Context.RECEIVER_NOT_EXPORTED);

        // Called on binder threads.
        mTaskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                mHandler.post(PerformanceService.this::scheduleUpdate);
            }

            @Override
            public void onTaskFocusChanged(int taskId, boolean focused) {
                mHandler.post(PerformanceService.this::scheduleUpdate);
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
        if (mScreenReceiver != null) {
            unregisterReceiver(mScreenReceiver);
            mScreenReceiver = null;
        }
        if (mSettingsObserver != null) {
            getContentResolver().unregisterContentObserver(mSettingsObserver);
            mSettingsObserver = null;
        }
        mHandler.removeCallbacksAndMessages(null);
        deactivate();
    }

    private void scheduleUpdate() {
        mPidRetries = 0;
        mHandler.removeCallbacks(mUpdate);
        mHandler.postDelayed(mUpdate, UPDATE_DELAY_MS);
    }

    /** Applies what the current foreground app and settings call for. */
    private void update() {
        final Foreground app = mEnabled && mPowerManager.isInteractive()
                && !mKeyguardManager.isKeyguardLocked() ? queryForeground() : null;
        final PerfProfile profile = app != null
                ? PerfSettings.getAppProfile(getContentResolver(), app.pkg) : null;
        if (app == null ? mApplied == null
                : app.sameApp(mApplied) && profile.equals(mAppliedProfile)) {
            return;
        }
        deactivate();
        if (app != null) {
            activate(app, profile);
        }
    }

    /** The app of the focused task, or null if there is none or its process is not up yet. */
    private Foreground queryForeground() {
        final ActivityTaskManager.RootTaskInfo task;
        try {
            task = ActivityTaskManager.getService().getFocusedRootTaskInfo();
        } catch (RemoteException e) {
            return null;
        }
        if (task == null || task.topActivity == null || task.topActivityInfo == null) {
            return null;
        }
        final ActivityInfo info = task.topActivityInfo;
        final int uid = info.applicationInfo.uid;
        final List<ActivityManager.RunningAppProcessInfo> processes =
                mActivityManager.getRunningAppProcesses();
        if (processes != null) {
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.uid == uid && Objects.equals(process.processName, info.processName)) {
                    return new Foreground(task.topActivity.getPackageName(),
                            task.topActivity.getClassName(), process.pid, uid);
                }
            }
        }
        if (mPidRetries++ < PID_RETRIES) {
            mHandler.postDelayed(mUpdate, PID_RETRY_DELAY_MS);
        }
        return mApplied;
    }

    private void activate(Foreground app, PerfProfile profile) {
        mApplied = app;
        mAppliedProfile = profile;
        switch (profile.mode) {
            case PerfProfile.MODE_CUSTOM:
                recordForeground(app);
                final int[] resources = PerfResources.build(mVendor, profile, CpuClusters.get(),
                        app.pid);
                if (resources.length > 0) {
                    mLockHandle = mHal.perfLockAcquire(resources, Process.myPid());
                    if (mLockHandle <= 0) {
                        Log.e(TAG, "No lock for " + app.pkg);
                        mLockHandle = 0;
                    }
                }
                AppRefreshRateLimit.set(profile.advanced && profile.refreshRate > 0
                        ? profile.refreshRate : AppRefreshRateLimit.NONE);
                Log.i(TAG, app.pkg + ": custom profile, preset " + profile.preset
                        + (profile.advanced ? ", advanced" : "") + ", lock " + mLockHandle);
                break;
            case PerfProfile.MODE_OFF:
                recordForeground(app);
                Log.i(TAG, app.pkg + ": off");
                break;
            default:
                mHal.notifyAppState(app.pkg, app.activity, app.pid, MtkPower.STATE_RESUMED,
                        app.uid);
                Log.i(TAG, app.pkg + ": vendor rules");
                break;
        }
    }

    /** Undoes what activate() did, so nothing stays applied. */
    private void deactivate() {
        if (mApplied == null) {
            return;
        }
        if (mAppliedProfile.mode == PerfProfile.MODE_VENDOR) {
            mHal.notifyAppState(mApplied.pkg, mApplied.activity, mApplied.pid,
                    MtkPower.STATE_PAUSED, mApplied.uid);
        }
        if (mLockHandle > 0) {
            mHal.perfLockRelease(mLockHandle);
            mLockHandle = 0;
        }
        AppRefreshRateLimit.set(AppRefreshRateLimit.NONE);
        mApplied = null;
        mAppliedProfile = null;
    }

    /** What IMtkpower_applist records for every foreground app (notifyNetdUID, notifyPID). */
    private void recordForeground(Foreground app) {
        mHal.setSysInfo(MtkPower.SYS_INFO_FOREGROUND_UID, String.valueOf(app.uid));
        mHal.setSysInfo(MtkPower.SYS_INFO_FOREGROUND_PID, String.valueOf(app.pid));
    }

    /**
     * A restarted power HAL has no locks and a restarted applist service no foreground app.
     * Undo what is applied (calls to the dead service just fail; if only one of the two died,
     * the other one is cleaned up properly), forget the binders, and apply everything again.
     */
    private void onHalDied() {
        Log.w(TAG, "MediaTek power service died");
        deactivate();
        mHal.reset();
        scheduleUpdate();
    }
}
