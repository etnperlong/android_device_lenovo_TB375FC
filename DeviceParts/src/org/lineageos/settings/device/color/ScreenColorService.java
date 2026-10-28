/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.app.ActivityTaskManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;

import java.util.Arrays;

/**
 * Applies the screen color settings through the MediaTek picture quality service, as stock
 * ZUI's ZuiDisplayService does in system_server (through com.mediatek.pq.PictureQuality and
 * libjni_pq.so):
 *
 * - Color mode (stock mtk_color_mode, setPictureMode for peridot): Vivid is gamma table 7, an
 *   identity CCORR matrix and the skin/grass tone and partial Y indexes at 0; Standard is gamma
 *   table 8 and peridot's own gamut matrix; Natural is gamma table 8 and the natural matrix
 *   (stock's persist.sys.colormode_modes offers only Vivid and Standard on this tablet).
 * - Color temperature (stock mtk_color_temperature): RGB gains.
 * - Adapt to ambient light (stock auto_color_temperature): enableChameleon.
 * - Saturation, contrast and sharpness: MediaTek picture indexes, as MiraVision sets them.
 *
 * While an app is in front (screen on and unlocked), its app color setting replaces the mode
 * or turns the enhancements off. Only what changed is sent; everything is sent again after the
 * service restarts. Stock's step arguments are kept: libjni_pq.so's default "on" step (0) for
 * gamma and Chameleon, 1 elsewhere.
 */
public class ScreenColorService extends Service {
    private static final String TAG = "ScreenColor";

    /** Coalesces the burst of task stack callbacks of one transition. */
    private static final long UPDATE_DELAY_MS = 50;
    /** The picture quality service may still be starting at boot. */
    private static final long RETRY_DELAY_MS = 2_000;
    private static final int MAX_RETRIES = 30;

    private static final int STEP_ON = 0;
    private static final int STEP = 1;

    private static final int GAMMA_VIVID = 7;
    private static final int GAMMA_STANDARD = 8;

    // ZuiDisplayService matrices, scaled to ColorSettings.UNIT as PictureQuality.setCcorrMatrix
    // does (truncated) on peridot.
    private static final int[] MATRIX_IDENTITY = toUnit(new double[] {
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0});
    private static final int[] MATRIX_STANDARD = toUnit(new double[] {
            0.84082, 0.17871, 0.02441,
            0.02832, 1.0, 0.0,
            0.01562, 0.0708, 0.94921});
    private static final int[] MATRIX_NATURAL = toUnit(new double[] {
            0.87402, 0.08203, 0.03955,
            0.00878, 0.96777, 0.02441,
            0.0, 0.01611, 0.98535});

    /**
     * Indexes Vivid sets to 0: grass tone hue and saturation, skin tone hue, partial Y and skin
     * tone saturation (setGrassToneHIndex ... setSkinToneSIndex in libjni_pq.so).
     */
    private static final int[] VIVID_ZERO_INDEXES = {4, 7, 3, 2, 6};

    private HandlerThread mThread;
    private Handler mHandler;
    private PictureQuality mHal;
    private KeyguardManager mKeyguardManager;
    private PowerManager mPowerManager;
    private ContentObserver mSettingsObserver;
    private BroadcastReceiver mScreenReceiver;
    private TaskStackListener mTaskStackListener;

    /** What the service shows, null if unknown (nothing sent yet, or a call failed). */
    private ColorState mApplied;
    private int mRetries;

    private final Runnable mUpdate = this::update;

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_DISPLAY);
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
        mHal = new PictureQuality(() -> mHandler.post(this::onHalDied));
        mKeyguardManager = getSystemService(KeyguardManager.class);
        mPowerManager = getSystemService(PowerManager.class);

        final ContentResolver cr = getContentResolver();
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                scheduleUpdate();
            }
        };
        for (String key : new String[] {
                ColorSettings.KEY_MODE,
                ColorSettings.KEY_TEMPERATURE,
                ColorSettings.KEY_TEMPERATURE_POINT,
                ColorSettings.KEY_ADAPTIVE,
                ColorSettings.KEY_APP_PROFILES_ENABLED,
                ColorSettings.KEY_APP_PROFILES}) {
            cr.registerContentObserver(Settings.Secure.getUriFor(key), false, mSettingsObserver);
        }
        for (ColorSettings.Adjustment adjustment : ColorSettings.ADJUSTMENTS) {
            cr.registerContentObserver(Settings.Secure.getUriFor(adjustment.key), false,
                    mSettingsObserver);
        }

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
                mHandler.post(ScreenColorService.this::scheduleUpdate);
            }

            @Override
            public void onTaskFocusChanged(int taskId, boolean focused) {
                mHandler.post(ScreenColorService.this::scheduleUpdate);
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
    }

    private void scheduleUpdate() {
        mRetries = 0;
        mHandler.removeCallbacks(mUpdate);
        mHandler.postDelayed(mUpdate, UPDATE_DELAY_MS);
    }

    /** Shows what the settings and the app in front call for. */
    private void update() {
        final ContentResolver cr = getContentResolver();
        final String pkg = ColorSettings.isAppProfilesEnabled(cr) ? queryForeground() : null;
        final int profile = pkg != null
                ? ColorSettings.getAppProfiles(cr).getOrDefault(pkg, ColorSettings.APP_DEFAULT)
                : ColorSettings.APP_DEFAULT;
        final ColorState target = ColorState.fromSettings(cr).forApp(profile);
        if (target.equals(mApplied)) {
            return;
        }
        if (apply(target, mApplied)) {
            mApplied = target;
            mRetries = 0;
            Log.i(TAG, (profile != ColorSettings.APP_DEFAULT ? pkg + ": " : "") + target);
            return;
        }
        mApplied = null;
        if (mRetries++ < MAX_RETRIES) {
            mHandler.removeCallbacks(mUpdate);
            mHandler.postDelayed(mUpdate, RETRY_DELAY_MS);
        }
    }

    /**
     * Sends the parts of target that differ from previous, or all of it if previous is null.
     * The service keeps the picture indexes across its restarts (persist.vendor.sys.pq.*), and
     * the last level sent is always either the setting or the default, so with previous null an
     * index at its default level is not sent.
     */
    private boolean apply(ColorState target, ColorState previous) {
        boolean ok = true;
        if (previous == null || previous.mode != target.mode) {
            ok &= applyMode(target.mode);
        }
        if (previous == null || !Arrays.equals(previous.gains, target.gains)) {
            ok &= mHal.setRgbGain(target.gains, STEP);
        }
        if (previous == null || previous.adaptive != target.adaptive) {
            ok &= mHal.enableChameleon(target.adaptive, STEP_ON);
        }
        for (int i = 0; i < ColorSettings.ADJUSTMENTS.length; i++) {
            final ColorSettings.Adjustment adjustment = ColorSettings.ADJUSTMENTS[i];
            final int level = target.levels[i];
            final int before = previous != null
                    ? previous.levels[i] : ColorSettings.Adjustment.LEVEL_DEFAULT;
            if (level != before) {
                ok &= mHal.setPqIndex(adjustment.pqIndex,
                        level == ColorSettings.Adjustment.LEVEL_DEFAULT
                                ? adjustment.defaultLevel : level,
                        STEP);
            }
        }
        return ok;
    }

    /** ZuiDisplayService.setPictureMode for peridot. */
    private boolean applyMode(int mode) {
        switch (mode) {
            case ColorSettings.MODE_STANDARD:
                return mHal.setGammaIndex(GAMMA_STANDARD, STEP_ON)
                        & mHal.setColorMatrix(MATRIX_STANDARD, STEP);
            case ColorSettings.MODE_NATURAL:
                return mHal.setGammaIndex(GAMMA_STANDARD, STEP_ON)
                        & mHal.setColorMatrix(MATRIX_NATURAL, STEP);
            default:
                boolean ok = mHal.setGammaIndex(GAMMA_VIVID, STEP_ON)
                        & mHal.setColorMatrix(MATRIX_IDENTITY, STEP);
                for (int index : VIVID_ZERO_INDEXES) {
                    ok &= mHal.setPqIndex(index, 0, STEP);
                }
                return ok;
        }
    }

    /** The package of the focused task while the screen is on and unlocked, else null. */
    private String queryForeground() {
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

    /** A restarted picture quality service starts from its own defaults; send everything. */
    private void onHalDied() {
        Log.w(TAG, "MediaTek picture quality service died");
        mHal.reset();
        mApplied = null;
        scheduleUpdate();
    }

    private static int[] toUnit(double[] matrix) {
        final int[] scaled = new int[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            scaled[i] = (int) (matrix[i] * ColorSettings.UNIT);
        }
        return scaled;
    }
}
