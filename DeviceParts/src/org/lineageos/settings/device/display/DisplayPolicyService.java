/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.display;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManagerGlobal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;

import lineageos.hardware.LineageHardwareManager;

/**
 * Refresh-rate policy replacing two stock ZUI mechanisms with AOSP/LineageOS pieces:
 *
 * - Stylus: stock MediaTek SurfaceFlinger runs a 10 s "PenTimer" fed by every stylus
 *   MotionEvent (hover included) and steers to 120 Hz while it runs. Here a gesture-monitor
 *   spy window observes the same events and caps the display at 120 Hz.
 * - Reading mode: stock caps at max(min_refresh_rate, 30 Hz) while ZUI reading mode is active
 *   and the user enabled "low refresh". Here the reading mode is LineageOS LiveDisplay reading
 *   enhancement (grayscale; warmth comes from Night Light), and the opt-in toggle is
 *   {@link #KEY_READING_LOW_REFRESH} (see {@link ReadingLowRefreshTile}).
 *
 * Both limits are folded into one DisplayManager.requestMaxRefreshRate() value
 * (PRIORITY_SYSTEM_REQUESTED_MODES), which DisplayModeDirector drops if this process dies.
 */
public class DisplayPolicyService extends Service {
    private static final String TAG = "DisplayPolicy";

    /** Secure setting, 1 = cap at 30 Hz while reading mode is on. Default off, like stock. */
    static final String KEY_READING_LOW_REFRESH = "tb375fc_reading_low_refresh";

    /** Matches stock ro.surface_flinger.set_pen_timer_ms. */
    private static final long STYLUS_TIMEOUT_MS = 10_000;
    /** Highest rate at which the NVT active pen is reported to work. */
    private static final float STYLUS_CAP_HZ = 120f;
    /** Matches stock ro.config.lgsi.reading_mode_require_fps. */
    private static final float READING_CAP_HZ = 30f;
    /**
     * LiveDisplay reading mode has no change notification (the QS tile and LineageParts call
     * LineageHardwareManager.set() directly and nothing writes a setting), so its state is
     * re-read shortly after each tap. Only done while the low-refresh toggle is on.
     */
    private static final long READING_RECHECK_DELAY_MS = 600;

    private final RefreshRateArbiter mArbiter =
            new RefreshRateArbiter(STYLUS_CAP_HZ, READING_CAP_HZ);

    private HandlerThread mThread;
    private Handler mHandler;
    private DisplayManager mDisplayManager;
    private LineageHardwareManager mHardware;
    private boolean mReadingSupported;
    private boolean mReadingLowRefreshEnabled;
    private InputMonitor mInputMonitor;
    private PolicyInputReceiver mInputReceiver;
    private ContentObserver mSettingsObserver;
    private BroadcastReceiver mScreenReceiver;

    private long mLastStylusUptime;
    private float mAppliedCapHz = RefreshRateArbiter.NO_LIMIT;

    private final Runnable mStylusTimeoutCheck = new Runnable() {
        @Override
        public void run() {
            final long idle = SystemClock.uptimeMillis() - mLastStylusUptime;
            if (idle >= STYLUS_TIMEOUT_MS) {
                mArbiter.setStylusActive(false);
                applyCap();
            } else {
                mHandler.postDelayed(this, STYLUS_TIMEOUT_MS - idle);
            }
        }
    };

    private final Runnable mReadingModeCheck = () -> {
        updateReadingMode();
        applyCap();
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_DISPLAY);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mDisplayManager = getSystemService(DisplayManager.class);
        mHardware = LineageHardwareManager.getInstance(this);
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
        mReadingSupported =
                mHardware.isSupported(LineageHardwareManager.FEATURE_READING_ENHANCEMENT);

        final ContentResolver cr = getContentResolver();
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                readSettings();
                updateReadingMode();
                applyCap();
            }
        };
        cr.registerContentObserver(Settings.System.getUriFor(Settings.System.MIN_REFRESH_RATE),
                false, mSettingsObserver);
        cr.registerContentObserver(Settings.Secure.getUriFor(KEY_READING_LOW_REFRESH),
                false, mSettingsObserver);

        mScreenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mArbiter.setScreenOn(Intent.ACTION_SCREEN_ON.equals(intent.getAction()));
                mHandler.removeCallbacks(mStylusTimeoutCheck);
                updateReadingMode();
                applyCap();
            }
        };
        final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, filter, null, mHandler, Context.RECEIVER_NOT_EXPORTED);

        mInputMonitor = InputManagerGlobal.getInstance()
                .monitorGestureInput(TAG, Display.DEFAULT_DISPLAY);
        mInputReceiver = new PolicyInputReceiver(mInputMonitor, mHandler);

        mArbiter.setScreenOn(getSystemService(PowerManager.class).isInteractive());
        readSettings();
        updateReadingMode();
        applyCap();
    }

    private void stop() {
        if (mInputReceiver != null) {
            mInputReceiver.dispose();
            mInputReceiver = null;
        }
        if (mInputMonitor != null) {
            mInputMonitor.dispose();
            mInputMonitor = null;
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
        mArbiter.setScreenOn(false);
        applyCap();
    }

    private void readSettings() {
        final ContentResolver cr = getContentResolver();
        mArbiter.setUserMinHz(
                Settings.System.getFloat(cr, Settings.System.MIN_REFRESH_RATE, 0f));
        mReadingLowRefreshEnabled = mReadingSupported
                && Settings.Secure.getInt(cr, KEY_READING_LOW_REFRESH, 0) != 0;
    }

    private void updateReadingMode() {
        final boolean reading = mReadingLowRefreshEnabled
                && mHardware.get(LineageHardwareManager.FEATURE_READING_ENHANCEMENT);
        mArbiter.setReadingMode(reading, mReadingLowRefreshEnabled);
    }

    /** Called on mHandler's looper for every stylus pointer event. */
    private void onStylusEvent() {
        mLastStylusUptime = SystemClock.uptimeMillis();
        if (mArbiter.isStylusActive()) {
            return;
        }
        mArbiter.setStylusActive(true);
        if (mArbiter.isStylusActive()) { // false while the screen is off
            applyCap();
            mHandler.postDelayed(mStylusTimeoutCheck, STYLUS_TIMEOUT_MS);
        }
    }

    /** Called on mHandler's looper when any pointer gesture ends. */
    private void onGestureEnd() {
        if (!mReadingLowRefreshEnabled) {
            return;
        }
        mHandler.removeCallbacks(mReadingModeCheck);
        mHandler.postDelayed(mReadingModeCheck, READING_RECHECK_DELAY_MS);
    }

    private void applyCap() {
        final float cap = mArbiter.computeCapHz();
        if (cap == mAppliedCapHz) {
            return;
        }
        try {
            mDisplayManager.requestMaxRefreshRate(Display.DEFAULT_DISPLAY, cap);
            mAppliedCapHz = cap;
            Log.i(TAG, "max refresh rate request: "
                    + (cap == RefreshRateArbiter.NO_LIMIT ? "none" : cap + " Hz"));
        } catch (RuntimeException e) {
            Log.e(TAG, "requestMaxRefreshRate(" + cap + ") failed", e);
        }
    }

    /** Observes only; never consumes or pilfers, so apps see the stream unchanged. */
    private final class PolicyInputReceiver extends InputEventReceiver {
        PolicyInputReceiver(InputMonitor monitor, Handler handler) {
            super(monitor.getInputChannel(), handler.getLooper());
        }

        @Override
        public void onInputEvent(InputEvent event) {
            if (event instanceof MotionEvent motion) {
                final int action = motion.getActionMasked();
                if (action != MotionEvent.ACTION_CANCEL && motion.isStylusPointer()) {
                    onStylusEvent();
                }
                if (action == MotionEvent.ACTION_UP) {
                    onGestureEnd();
                }
            }
            finishInputEvent(event, false);
        }
    }
}
