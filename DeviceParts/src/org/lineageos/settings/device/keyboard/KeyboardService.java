/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.keyboard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.InputDevice;

import org.lineageos.settings.device.R;

/**
 * Tells the user when the folio keyboard connects or disconnects (stock: ZUI keyboard
 * framework).
 *
 * The keyboard is attached through pogo pins and a UART, not Bluetooth; tn_keyboard.ko
 * registers its "tn_keyboard" input device once the keyboard answers on the UART and removes it
 * when the keyboard is detached. The driver also cuts the keyboard's power 60 s after the cover
 * closes and powers it again when the screen turns back on, which may remove and register the
 * device again; so a removal while the screen is off is not announced, and neither is the next
 * registration. A removal followed by a registration within DISCONNECT_DELAY_MS (the driver
 * re-probing the keyboard) is not announced either.
 *
 * Disabling the keyboard while it is folded back (KeyHandler) only changes the device, so it
 * does not count as a disconnection.
 */
public class KeyboardService extends Service {
    private static final String TAG = "KeyboardService";

    /** Secure setting, 1 = notify when the folio keyboard connects or disconnects. Default on. */
    public static final String KEY_KEYBOARD_NOTIFICATIONS = "tb375fc_keyboard_notifications";

    // tn_keyboard.ko registers the keyboard as BUS_HOST 17ef:6175 "tn_keyboard".
    private static final int VENDOR_ID = 0x17ef;
    private static final int PRODUCT_ID = 0x6175;
    private static final String DEVICE_KEYBOARD = "tn_keyboard";

    private static final String CHANNEL_ID = "keyboard_connection";
    // PenService uses 1.
    private static final int NOTIFICATION_ID = 2;
    private static final long NOTIFICATION_TIMEOUT_MS = 10_000;
    private static final long DISCONNECT_DELAY_MS = 2_000;

    private HandlerThread mThread;
    private Handler mHandler;
    private InputManager mInputManager;
    private NotificationManager mNotificationManager;
    private PowerManager mPowerManager;

    // Handler thread only.
    private int mKeyboardId = -1;
    private boolean mRemovedWhileAsleep;

    private final Runnable mNotifyDisconnected = () -> notifyConnection(false);

    private final InputManager.InputDeviceListener mDeviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    if (isKeyboard(mInputManager.getInputDevice(deviceId))) {
                        onKeyboardAdded(deviceId);
                    }
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    if (deviceId == mKeyboardId) {
                        onKeyboardRemoved();
                    }
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mInputManager = getSystemService(InputManager.class);
        mNotificationManager = getSystemService(NotificationManager.class);
        mPowerManager = getSystemService(PowerManager.class);
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
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.keyboard_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        mNotificationManager.createNotificationChannel(channel);

        mInputManager.registerInputDeviceListener(mDeviceListener, mHandler);
        // A keyboard attached before this service (re)starts is not announced.
        for (int id : mInputManager.getInputDeviceIds()) {
            if (isKeyboard(mInputManager.getInputDevice(id))) {
                mKeyboardId = id;
            }
        }
    }

    private void stop() {
        mInputManager.unregisterInputDeviceListener(mDeviceListener);
        mHandler.removeCallbacksAndMessages(null);
    }

    private void onKeyboardAdded(int deviceId) {
        mKeyboardId = deviceId;
        if (mHandler.hasCallbacks(mNotifyDisconnected)) {
            mHandler.removeCallbacks(mNotifyDisconnected);
            return;
        }
        if (mRemovedWhileAsleep) {
            mRemovedWhileAsleep = false;
            return;
        }
        if (mPowerManager.isInteractive()) {
            notifyConnection(true);
        }
    }

    private void onKeyboardRemoved() {
        mKeyboardId = -1;
        if (!mPowerManager.isInteractive()) {
            mRemovedWhileAsleep = true;
            return;
        }
        mHandler.postDelayed(mNotifyDisconnected, DISCONNECT_DELAY_MS);
    }

    private static boolean isKeyboard(InputDevice device) {
        return device != null && device.getVendorId() == VENDOR_ID
                && device.getProductId() == PRODUCT_ID && DEVICE_KEYBOARD.equals(device.getName());
    }

    private void notifyConnection(boolean connected) {
        Log.i(TAG, connected ? "Keyboard connected" : "Keyboard disconnected");
        if (Settings.Secure.getInt(getContentResolver(), KEY_KEYBOARD_NOTIFICATIONS, 1) == 0) {
            return;
        }
        final PendingIntent keyboardSettings = PendingIntent.getActivity(this, 0,
                new Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);
        final Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_keyboard)
                .setContentTitle(getString(connected
                        ? R.string.keyboard_connected_title : R.string.keyboard_disconnected_title))
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(keyboardSettings)
                .setAutoCancel(true)
                .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }
}
