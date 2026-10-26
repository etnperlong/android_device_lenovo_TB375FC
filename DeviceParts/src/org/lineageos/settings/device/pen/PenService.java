/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.pen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import org.lineageos.settings.device.R;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Turns on the pen mode of the Novatek touch controller for the Lenovo active pen, and tells the
 * user when the pen connects or disconnects.
 *
 * The pen draws through the touch controller, not through Bluetooth (Bluetooth only carries the
 * buttons and the battery), but the controller only looks for the pen while its pen mode is on.
 * nt36532.ko takes the mode from /proc/support_pen and sends it as controller command 0x7B; it
 * starts at 0, sends 0 after the boot firmware update and re-sends the last written value on
 * every resume. Stock ZUI's BluetoothPenConnectPolicy (services.jar) writes it through the Lenovo
 * touchscreen HAL: 1 (setPenMode(true)) then 3 (setPenBle(false)) when a Lenovo pen connects
 * over HID, 0 when it disconnects, and 1 at boot once a pen has been paired.
 *
 * Here the mode follows the same values, except that it stays at 1 (stock's boot value for a
 * paired pen) while a Lenovo pen is paired but not connected, so the controller keeps looking for
 * the pen while its Bluetooth link is down (pen asleep, Bluetooth off); stock instead relies on
 * reconnecting the pen when the controller reports its address (UEVENT_TO=PEN_FRAMEWORK). The mode
 * is turned off (0) once no Lenovo pen is paired.
 */
public class PenService extends Service {
    private static final String TAG = "PenService";

    /** Secure setting, 1 = notify when a Lenovo pen connects or disconnects. Default on. */
    public static final String KEY_PEN_NOTIFICATIONS = "tb375fc_pen_notifications";
    /** Secure setting: comma-separated addresses of the Lenovo pens that have connected. */
    private static final String KEY_PEN_ADDRESSES = "tb375fc_pen_addresses";

    private static final String SUPPORT_PEN_PATH = "/proc/support_pen";
    /** No pen paired: pen mode off (stock setPenMode(false)). */
    private static final String[] MODE_OFF = {"0"};
    /** Pen paired but not connected: pen mode on (stock setPenMode(true) at boot). */
    private static final String[] MODE_PAIRED = {"1"};
    /** Pen connected: pen mode on, then Bluetooth linked (stock setPenMode(true), setPenBle(false)). */
    private static final String[] MODE_CONNECTED = {"1", "3"};

    /** Bluetooth names stock CommonUtils.isZuiStylusPen() accepts ("Lenovo Tab Pen Pro 2" too). */
    private static final String[] PEN_NAMES = {
        "Lenovo Precision Pen 3",
        "Lenovo Tab Pen Plus",
        "Lenovo Tab Pen Pro",
    };

    private static final String CHANNEL_ID = "pen_connection";
    private static final int NOTIFICATION_ID = 1;
    private static final long NOTIFICATION_TIMEOUT_MS = 10_000;

    private final Set<String> mPairedPens = new ArraySet<>();
    private final Set<String> mConnectedPens = new ArraySet<>();

    private HandlerThread mThread;
    private Handler mHandler;
    private NotificationManager mNotificationManager;
    private BluetoothAdapter mAdapter;
    private BluetoothProfile mHidHost;
    private String[] mAppliedMode;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final BluetoothDevice device =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            if (device == null) {
                return;
            }
            if (BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
                onConnectionStateChanged(device, intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED));
            } else if (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE) == BluetoothDevice.BOND_NONE) {
                onUnpaired(device);
            }
        }
    };

    private final BluetoothProfile.ServiceListener mHidHostListener =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    mHandler.post(() -> onHidHostConnected(proxy));
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    mHandler.post(() -> mHidHost = null);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mNotificationManager = getSystemService(NotificationManager.class);
        mAdapter = getSystemService(BluetoothManager.class).getAdapter();
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
                getString(R.string.pen_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        mNotificationManager.createNotificationChannel(channel);

        final String saved =
                Settings.Secure.getString(getContentResolver(), KEY_PEN_ADDRESSES);
        if (!TextUtils.isEmpty(saved)) {
            for (String address : saved.split(",")) {
                mPairedPens.add(address);
            }
        }
        // A paired pen must write before Bluetooth is up, as on stock.
        applyPenMode();

        final IntentFilter filter =
                new IntentFilter(BluetoothHidHost.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mReceiver, filter, null, mHandler, Context.RECEIVER_EXPORTED);

        // Pens already connected when this service (re)starts send no broadcast.
        if (mAdapter != null) {
            mAdapter.getProfileProxy(this, mHidHostListener, BluetoothProfile.HID_HOST);
        }
    }

    private void stop() {
        unregisterReceiver(mReceiver);
        if (mAdapter != null && mHidHost != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.HID_HOST, mHidHost);
        }
        mHidHost = null;
        mHandler.removeCallbacksAndMessages(null);
    }

    private void onHidHostConnected(BluetoothProfile proxy) {
        mHidHost = proxy;
        boolean changed = false;
        for (BluetoothDevice device : proxy.getConnectedDevices()) {
            if (isLenovoPen(device)) {
                changed |= mConnectedPens.add(device.getAddress());
                rememberPen(device.getAddress());
            }
        }
        if (changed) {
            applyPenMode();
        }
    }

    private void onConnectionStateChanged(BluetoothDevice device, int state) {
        final String address = device.getAddress();
        if (state == BluetoothProfile.STATE_CONNECTED) {
            if (!isLenovoPen(device) || !mConnectedPens.add(address)) {
                return;
            }
            rememberPen(address);
            applyPenMode();
            notifyConnection(device, true);
        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
            if (!mConnectedPens.remove(address)) {
                return;
            }
            applyPenMode();
            notifyConnection(device, false);
        }
    }

    private void onUnpaired(BluetoothDevice device) {
        final String address = device.getAddress();
        mConnectedPens.remove(address);
        if (mPairedPens.remove(address)) {
            savePairedPens();
            applyPenMode();
        }
    }

    private void rememberPen(String address) {
        if (mPairedPens.add(address)) {
            savePairedPens();
        }
    }

    private void savePairedPens() {
        Settings.Secure.putString(getContentResolver(), KEY_PEN_ADDRESSES,
                mPairedPens.isEmpty() ? null : String.join(",", mPairedPens));
    }

    private static boolean isLenovoPen(BluetoothDevice device) {
        final String name = device.getName();
        if (name == null) {
            return false;
        }
        for (String penName : PEN_NAMES) {
            if (name.contains(penName)) {
                return true;
            }
        }
        return false;
    }

    private void applyPenMode() {
        final String[] mode = !mConnectedPens.isEmpty() ? MODE_CONNECTED
                : !mPairedPens.isEmpty() ? MODE_PAIRED : MODE_OFF;
        if (mode == mAppliedMode) {
            return;
        }
        // Each value is one controller command; the driver reads one character per write.
        for (String value : mode) {
            try (FileOutputStream out = new FileOutputStream(SUPPORT_PEN_PATH)) {
                out.write(value.getBytes(StandardCharsets.US_ASCII));
            } catch (IOException e) {
                Log.e(TAG, "Failed to write " + value + " to " + SUPPORT_PEN_PATH, e);
                return;
            }
        }
        mAppliedMode = mode;
        Log.i(TAG, "support_pen=" + String.join(",", mode));
    }

    private void notifyConnection(BluetoothDevice device, boolean connected) {
        if (Settings.Secure.getInt(getContentResolver(), KEY_PEN_NOTIFICATIONS, 1) == 0) {
            return;
        }
        final PendingIntent bluetoothSettings = PendingIntent.getActivity(this, 0,
                new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE);
        final Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pen)
                .setContentTitle(getString(connected
                        ? R.string.pen_connected_title : R.string.pen_disconnected_title))
                .setContentText(device.getAlias())
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(bluetoothSettings)
                .setAutoCancel(true)
                .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
                .build();
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }
}
