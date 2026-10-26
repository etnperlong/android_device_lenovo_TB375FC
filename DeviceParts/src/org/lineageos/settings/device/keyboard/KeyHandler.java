/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.keyboard;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;

/**
 * Folio keyboard fold-back policy, loaded into system_server by PhoneWindowManager through
 * lineage-sdk config_deviceKeyHandlerLibs/Classes (overlay/LineageSdkDeviceOverlay).
 *
 * tn_keyboard.ko reports the keyboard firmware's fold state on its "tn_wakeup" input device:
 * scan code 655 once the keyboard is folded behind the tablet, 654 once it is folded back. It
 * keeps reporting keys and touchpad input either way. Stock ZUI (KeyboardShortcutController)
 * disables the keyboard on 655, enables it on 654 and meanwhile turns on
 * Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD, because a disabled keyboard still counts as a
 * hardware keyboard and would keep the on-screen keyboard hidden. This does the same with
 * InputManager, for the keyboard and its touchpad.
 *
 * The scan codes have no key layout entry, so they arrive as KEYCODE_UNKNOWN and are matched by
 * scan code; they are consumed so that they never wake the device or reach apps.
 *
 * If the keyboard goes away while folded (detached, or powered down by tn_keyboard.ko after the
 * cover stays closed), the policy is reset: it is re-registered enabled, and the firmware may not
 * report 654 for a keyboard that was unfolded while it was gone.
 */
public final class KeyHandler implements DeviceKeyHandler {
    private static final String TAG = "KeyboardFold";

    private static final int SCAN_CODE_UNFOLDED = 654; // KEY_KB_ENABLE, ZUI KEYBOARD_RESTORE
    private static final int SCAN_CODE_FOLDED = 655; // KEY_KB_DISABLE, ZUI KEYBOARD_REVERSE

    // All three tn_keyboard.ko input devices report BUS_HOST 17ef:6175.
    private static final int VENDOR_ID = 0x17ef;
    private static final int PRODUCT_ID = 0x6175;
    private static final String DEVICE_WAKEUP = "tn_wakeup";
    private static final String DEVICE_KEYBOARD = "tn_keyboard";
    private static final String DEVICE_TOUCHPAD = "tn_touchpad";

    /** Settings.Secure, 1 while SHOW_IME_WITH_HARD_KEYBOARD is on because of a fold. */
    private static final String KEY_FOLD_IME = "tb375fc_keyboard_fold_ime";

    private final Context mContext;
    private final Handler mHandler;

    // Handler thread only.
    private InputManager mInputManager;
    private boolean mFolded;
    private final ArraySet<Integer> mDisabledIds = new ArraySet<>();

    private final InputManager.InputDeviceListener mDeviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    if (mFolded && isKeyboardOrTouchpad(mInputManager.getInputDevice(deviceId))) {
                        disable(deviceId);
                    }
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    if (mFolded && mDisabledIds.contains(deviceId)) {
                        Log.i(TAG, "Keyboard removed while folded");
                        setFolded(false);
                    }
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                }
            };

    /** Called by PhoneWindowManager.init(), before the input service is published. */
    public KeyHandler(Context context) {
        mContext = context;
        final HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mHandler = new Handler(thread.getLooper());

        // A reboot while folded leaves the on-screen keyboard setting forced on.
        context.registerReceiverForAllUsers(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!mFolded) {
                    restoreIme();
                }
            }
        }, new IntentFilter(Intent.ACTION_LOCKED_BOOT_COMPLETED), null, mHandler);
    }

    /** Runs on the input reader thread from interceptKeyBeforeQueueing(). */
    @Override
    public KeyEvent handleKeyEvent(KeyEvent event) {
        final int scanCode = event.getScanCode();
        if (scanCode != SCAN_CODE_FOLDED && scanCode != SCAN_CODE_UNFOLDED) {
            return event;
        }
        if (!isFolioDevice(event.getDevice(), DEVICE_WAKEUP)) {
            return event;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            final boolean folded = scanCode == SCAN_CODE_FOLDED;
            mHandler.post(() -> setFolded(folded));
        }
        return null;
    }

    private void setFolded(boolean folded) {
        if (folded == mFolded) {
            return;
        }
        mFolded = folded;
        Log.i(TAG, folded ? "Folded behind the tablet" : "Folded back");
        final InputManager im = getInputManager();
        if (folded) {
            for (int id : im.getInputDeviceIds()) {
                if (isKeyboardOrTouchpad(im.getInputDevice(id))) {
                    disable(id);
                }
            }
            forceIme();
        } else {
            // Ids of devices that are gone are only dropped from InputManagerService's list.
            for (int id : mDisabledIds) {
                im.enableInputDevice(id);
            }
            mDisabledIds.clear();
            restoreIme();
        }
    }

    private void disable(int deviceId) {
        final InputDevice device = mInputManager.getInputDevice(deviceId);
        if (device == null || !device.isEnabled()) {
            return;
        }
        mInputManager.disableInputDevice(deviceId);
        mDisabledIds.add(deviceId);
    }

    private InputManager getInputManager() {
        if (mInputManager == null) {
            mInputManager = mContext.getSystemService(InputManager.class);
            mInputManager.registerInputDeviceListener(mDeviceListener, mHandler);
        }
        return mInputManager;
    }

    private void forceIme() {
        final ContentResolver resolver = mContext.getContentResolver();
        if (Settings.Secure.getIntForUser(resolver, Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD,
                0, UserHandle.USER_CURRENT) != 0) {
            return;
        }
        // Marker first, so that a crash in between still gets the setting restored.
        Settings.Secure.putIntForUser(resolver, KEY_FOLD_IME, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD, 1,
                UserHandle.USER_CURRENT);
    }

    private void restoreIme() {
        final ContentResolver resolver = mContext.getContentResolver();
        if (Settings.Secure.getIntForUser(resolver, KEY_FOLD_IME, 0,
                UserHandle.USER_CURRENT) == 0) {
            return;
        }
        Settings.Secure.putIntForUser(resolver, Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD, 0,
                UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(resolver, KEY_FOLD_IME, 0, UserHandle.USER_CURRENT);
    }

    private static boolean isKeyboardOrTouchpad(InputDevice device) {
        return isFolioDevice(device, DEVICE_KEYBOARD) || isFolioDevice(device, DEVICE_TOUCHPAD);
    }

    private static boolean isFolioDevice(InputDevice device, String name) {
        return device != null && device.getVendorId() == VENDOR_ID
                && device.getProductId() == PRODUCT_ID && name.equals(device.getName());
    }
}
