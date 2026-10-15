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
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;

import com.android.internal.os.BackgroundThread;
import com.android.internal.os.DeviceKeyHandler;

/**
 * Folio-cover (Hall sensor) handling, loaded into system_server by PhoneWindowManager through
 * lineage-sdk config_deviceKeyHandlerLibs/Classes.
 *
 * The stock hall.ko driver reports the cover as EV_KEY scan code 252 (closed) / 253 (opened),
 * each as a down+up pair. Stock ZUI maps them to its private HALL_NEAR/HALL_FAR key codes and
 * ZuiPhoneWindowManager turns them into lid-switch events. LineageOS has neither, so the scan
 * codes arrive here unmapped (KEYCODE_UNKNOWN). Behaviour mirrors the stock policy:
 *  - closed while the screen is on and the feature is enabled: watch the ambient light sensor and
 *    put the device to sleep once it reads at most 20 lux (rejects a cover folded behind the
 *    tablet); lock the keyguard 2 s later if still closed and dark;
 *  - opened (or light returns while closed): wake the device, but only if the cover put it to
 *    sleep.
 * Without a light sensor the cover sleeps the device immediately.
 */
public class KeyHandler implements DeviceKeyHandler {
    private static final String TAG = "SmartCover";

    /** Settings.Secure toggle, 1 = enabled (stock: System zui_lid_enable, default 1). */
    public static final String KEY_SMART_COVER = "tb375fc_smart_cover";

    // hall.ko hall_eint_work: GPIO low -> scan code 0xfc, GPIO high -> 0xfd.
    private static final int SCANCODE_COVER_CLOSED = 252;
    private static final int SCANCODE_COVER_OPENED = 253;

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

    // Accessed only on mHandler's thread.
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

    public KeyHandler(Context context) {
        mContext = context;
        mHandler = BackgroundThread.getHandler();
        mPowerManager = context.getSystemService(PowerManager.class);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mSensorManager = context.getSystemService(SensorManager.class);
        mLightSensor = mSensorManager != null
                ? mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;
    }

    @Override
    public KeyEvent handleKeyEvent(KeyEvent event) {
        final int scanCode = event.getScanCode();
        if (event.getKeyCode() != KeyEvent.KEYCODE_UNKNOWN
                || (scanCode != SCANCODE_COVER_CLOSED && scanCode != SCANCODE_COVER_OPENED)) {
            return event;
        }
        // Called on the input dispatcher's policy path: never block here. The wake lock keeps the
        // system from re-suspending (the hall IRQ is a wake source) before the handler runs.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            mWakeLock.acquire(WAKELOCK_TIMEOUT_MS);
            final boolean closed = scanCode == SCANCODE_COVER_CLOSED;
            mHandler.post(() -> {
                if (closed) {
                    onCoverClosed();
                } else {
                    onCoverOpened();
                }
            });
        }
        return null;
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
        if (mSleptByCover) {
            mSleptByCover = false;
            wake();
        }
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
            mHandler.removeCallbacks(mLockRunnable);
            mSleptByCover = false;
            wake();
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

    private void wake() {
        mPowerManager.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_LID,
                "android.policy:LID");
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
