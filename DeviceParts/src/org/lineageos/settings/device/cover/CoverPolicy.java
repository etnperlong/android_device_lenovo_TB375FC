/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.cover;

import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManagerGlobal;

/**
 * Folio-cover policy, driven by the lid switch (the patched hall.ko reports the cover as
 * SW_LID). It mirrors stock ZuiPhoneWindowManager:
 *  - closed while the screen is on and the feature is enabled: watch the ambient light sensor and
 *    put the device to sleep once it reads at most 20 lux (rejects a cover folded behind the
 *    tablet); lock the keyguard 2 s later if still closed and dark;
 *  - light returning while the cover put the device to sleep wakes it again.
 * Without a light sensor the cover sleeps the device immediately. Opening the cover is left to
 * PhoneWindowManager, which wakes the device on every lid open.
 *
 * All methods run on the handler passed to the constructor.
 */
public class CoverPolicy {
    private static final String TAG = "SmartCover";

    /** Settings.Secure toggle, 1 = enabled (stock: System zui_lid_enable, default 1). */
    public static final String KEY_SMART_COVER = "tb375fc_smart_cover";

    // Stock: framework-res integer/config_light_sensor_min_value = 20.
    private static final float DARK_LUX = 20f;
    // Stock: mLockDeviceRunnable posted with 2000 ms delay.
    private static final long LOCK_DELAY_MS = 2000;
    private static final long WAKELOCK_TIMEOUT_MS = LOCK_DELAY_MS + 1000;

    private final Context mContext;
    private final Handler mHandler;
    private final PowerManager mPowerManager;
    private final PowerManager.WakeLock mWakeLock;
    private final SensorManager mSensorManager;
    private final Sensor mLightSensor;

    private boolean mLightListening;
    private boolean mSleptByCover;

    private final SensorEventListener mLightListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            onLight(event.values[0]);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final Runnable mLockRunnable = this::lockIfStillClosed;

    public CoverPolicy(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mPowerManager = context.getSystemService(PowerManager.class);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mSensorManager = context.getSystemService(SensorManager.class);
        mLightSensor = mSensorManager != null
                ? mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;
    }

    public void onLidChanged(boolean closed) {
        if (closed) {
            onCoverClosed();
        } else {
            onCoverOpened();
        }
    }

    private void onCoverClosed() {
        // Stock ignores the cover while the screen is already off.
        if (!isEnabled() || !mPowerManager.isInteractive()) {
            return;
        }
        if (mLightSensor == null) {
            sleep();
            return;
        }
        if (!mLightListening) {
            mLightListening = mSensorManager.registerListener(mLightListener, mLightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, mHandler);
        }
    }

    private void onCoverOpened() {
        stopLight();
        mHandler.removeCallbacks(mLockRunnable);
        mSleptByCover = false;
    }

    private void onLight(float lux) {
        if (!mSleptByCover && !mPowerManager.isInteractive()) {
            // Screen went off by other means while we were waiting for darkness (stock unregisters
            // on SCREEN_OFF unless the cover already closed the lid).
            stopLight();
            return;
        }
        if (lux <= DARK_LUX && !mSleptByCover) {
            if (!isEnabled()) {
                stopLight();
                return;
            }
            sleep();
            if (!isKeyguardLocked()) {
                mWakeLock.acquire(WAKELOCK_TIMEOUT_MS);
                mHandler.postDelayed(mLockRunnable, LOCK_DELAY_MS);
            }
        } else if (lux > DARK_LUX && mSleptByCover) {
            // The lid is still closed, so PhoneWindowManager will not wake the device.
            mHandler.removeCallbacks(mLockRunnable);
            mSleptByCover = false;
            mPowerManager.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_LID,
                    "android.policy:LID");
        }
    }

    private void lockIfStillClosed() {
        if (!mSleptByCover || isKeyguardLocked()) {
            return;
        }
        try {
            WindowManagerGlobal.getWindowManagerService().lockNow(null);
        } catch (RemoteException e) {
            Log.w(TAG, "lockNow failed", e);
        }
    }

    private void sleep() {
        mSleptByCover = true;
        // Same call PhoneWindowManager.applyLidSwitchState() makes for LID_BEHAVIOR_SLEEP, which is
        // what stock reaches through notifyLidSwitchChanged() (stock config_lidControlsSleep=true).
        mPowerManager.goToSleep(SystemClock.uptimeMillis(),
                PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH,
                PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
    }

    private void stopLight() {
        if (mLightListening) {
            mSensorManager.unregisterListener(mLightListener);
            mLightListening = false;
        }
    }

    private boolean isEnabled() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(), KEY_SMART_COVER, 1,
                UserHandle.USER_CURRENT) != 0;
    }

    private boolean isKeyguardLocked() {
        final KeyguardManager km = mContext.getSystemService(KeyguardManager.class);
        return km != null && km.isKeyguardLocked();
    }
}
