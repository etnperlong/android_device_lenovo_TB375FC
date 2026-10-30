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
 * Applies screen color settings through the MediaTek picture quality service, as stock ZUI's
 * ZuiDisplayService does in system_server (through com.mediatek.pq.PictureQuality and
 * libjni_pq.so):
 *
 * - Color mode (stock mtk_color_mode, setPictureMode for peridot): Vivid is gamma table 7, an
 *   identity CCORR matrix and the skin/grass tone and partial Y indexes at 0; Standard is gamma
 *   table 8 and peridot's own gamut matrix (stock's persist.sys.colormode_modes offers only
 *   these two on this tablet). Disabled restores the vendor defaults: gamma table 7 (the PQ
 *   service's getDefaultGammaIndex), the identity matrix and those five indexes at the panel PQ
 *   XML values, with a neutral white point, no Chameleon and the default adjustment levels, so
 *   that the system's Display > Colors mode (the panel XML's native/sRGB/P3 modes) decides.
 * - Color temperature (stock mtk_color_temperature) sets RGB gains; adaptation to ambient light
 *   (stock auto_color_temperature) uses enableChameleon.
 * - Saturation, contrast and sharpness use MediaTek picture indexes, as MiraVision sets them.
 * - Video picture enhancements (MDP/MML processing of hardware-decoded video) use setFeatureSwitch,
 *   as the enable methods of com.mediatek.pq.PictureQuality do. They are independent of the color
 *   mode. Stock turns only ultra resolution off (ZuiDisplayService, "video quality enhance" off).
 *
 * While an app is in front (screen on and unlocked), its app color setting replaces the mode
 * (Disabled with its vendor defaults); video enhancements stay as set. Only what changed is sent;
 * everything is sent again after the service restarts. Stock's step arguments are kept:
 * libjni_pq.so's default "on" step (0) for gamma and Chameleon, 1 elsewhere.
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
    /**
     * Indexes Vivid sets to 0: grass tone hue and saturation, skin tone hue, partial Y and skin
     * tone saturation (setGrassToneHIndex ... setSkinToneSIndex in libjni_pq.so).
     */
    private static final int[] VIVID_ZERO_INDEXES = {4, 7, 3, 2, 6};
    /**
     * Their defaults: GRASS_TONE_H, GRASS_TONE_S, SKIN_TONE_H, PartialY and SKIN_TONE_S of
     * Feature 0x0500 in stock /vendor/etc/cust_pq.xml and panel-*-nt36532-dsi-vdo_pq.xml.
     */
    private static final int[] PANEL_DEFAULT_INDEXES = {12, 10, 9, 0, 6};

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
        cr.registerContentObserver(Settings.Secure.getUriFor(ColorSettings.KEY_VIDEO_ENHANCE),
                false, mSettingsObserver);
        for (ColorSettings.VideoFeature feature : ColorSettings.VIDEO_FEATURES) {
            cr.registerContentObserver(Settings.Secure.getUriFor(feature.key), false,
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
        for (int i = 0; i < ColorSettings.VIDEO_FEATURES.length; i++) {
            if (previous == null || previous.video[i] != target.video[i]) {
                for (int feature : ColorSettings.VIDEO_FEATURES[i].featureIds) {
                    ok &= mHal.setFeatureSwitch(feature, target.video[i] ? 1 : 0);
                }
            }
        }
        return ok;
    }

    /** ZuiDisplayService.setPictureMode for peridot; Disabled restores the vendor defaults. */
    private boolean applyMode(int mode) {
        if (mode == ColorSettings.MODE_STANDARD) {
            return mHal.setGammaIndex(GAMMA_STANDARD, STEP_ON)
                    & mHal.setColorMatrix(MATRIX_STANDARD, STEP);
        }
        final boolean vivid = mode != ColorSettings.MODE_DISABLED;
        boolean ok = mHal.setGammaIndex(GAMMA_VIVID, STEP_ON)
                & mHal.setColorMatrix(MATRIX_IDENTITY, STEP);
        for (int i = 0; i < VIVID_ZERO_INDEXES.length; i++) {
            ok &= mHal.setPqIndex(VIVID_ZERO_INDEXES[i], vivid ? 0 : PANEL_DEFAULT_INDEXES[i],
                    STEP);
        }
        return ok;
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
