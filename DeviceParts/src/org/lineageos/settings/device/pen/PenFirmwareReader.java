/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.pen;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the firmware version of a connected Lenovo pen from the GATT Device Information service
 * (Firmware Revision String 0x2A26, e.g. "V1.18"), which stock's PenService app reads too
 * (BleGattHelper.UUID_HW_VERSION), and stores it with {@link PenDetails#setFirmwareVersion}.
 *
 * The pen is already connected over LE for HID, so the GATT client shares that link and closing
 * it does not disconnect the pen. All calls and callbacks run on the given handler.
 */
final class PenFirmwareReader {
    private static final String TAG = "PenFirmwareReader";

    private static final UUID DEVICE_INFORMATION =
            UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    private static final UUID FIRMWARE_REVISION =
            UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    private static final long TIMEOUT_MS = 30_000;

    private final Context mContext;
    private final Handler mHandler;
    /** The GATT client of each pen being read, by address. */
    private final Map<String, BluetoothGatt> mReads = new ArrayMap<>();

    private final BluetoothGattCallback mCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!gatt.discoverServices()) {
                    finish(gatt);
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                finish(gatt);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            final BluetoothGattService service = gatt.getService(DEVICE_INFORMATION);
            final BluetoothGattCharacteristic firmware =
                    service != null ? service.getCharacteristic(FIRMWARE_REVISION) : null;
            if (status != BluetoothGatt.GATT_SUCCESS || firmware == null
                    || !gatt.readCharacteristic(firmware)) {
                Log.w(TAG, "No firmware revision to read, status " + status);
                finish(gatt);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // A GATT string may be padded with NULs.
                final String version =
                        new String(value, StandardCharsets.UTF_8).replace("\0", "").trim();
                if (!version.isEmpty()) {
                    PenDetails.setFirmwareVersion(gatt.getDevice(), version);
                }
            } else {
                Log.w(TAG, "Firmware revision read failed, status " + status);
            }
            finish(gatt);
        }
    };

    PenFirmwareReader(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    /** Reads the firmware version of a connected pen, unless a read is already running. */
    void read(BluetoothDevice device) {
        if (mReads.containsKey(device.getAddress())) {
            return;
        }
        final BluetoothGatt gatt = device.connectGatt(mContext, false /* autoConnect */,
                mCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK,
                mHandler);
        if (gatt == null) {
            return;
        }
        mReads.put(device.getAddress(), gatt);
        mHandler.postDelayed(() -> finish(gatt), gatt, TIMEOUT_MS);
    }

    /** Stops every running read. */
    void cancelAll() {
        for (BluetoothGatt gatt : mReads.values().toArray(new BluetoothGatt[0])) {
            finish(gatt);
        }
    }

    private void finish(BluetoothGatt gatt) {
        if (mReads.get(gatt.getDevice().getAddress()) != gatt) {
            return;
        }
        mReads.remove(gatt.getDevice().getAddress());
        mHandler.removeCallbacksAndMessages(gatt);
        gatt.close();
    }
}
