/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.pen;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import org.lineageos.settings.device.R;

/**
 * The official Lenovo pens, recognized by Bluetooth name as stock CommonUtils.isZuiStylusPen()
 * (services.jar) does, with the picture stock's PenService app shows for each (BtPenModels,
 * res/drawable-xxhdpi/ic_bt_pen_*.png, turned diagonal on a square for the Settings header).
 */
enum PenModel {
    PRECISION_PEN_3("Lenovo Precision Pen 3", R.raw.pen_sheaffer),
    TAB_PEN_PLUS("Lenovo Tab Pen Plus", R.raw.pen_picasso),
    // Before TAB_PEN_PRO, whose name it contains.
    TAB_PEN_PRO_2("Lenovo Tab Pen Pro 2", R.raw.pen_omas),
    TAB_PEN_PRO("Lenovo Tab Pen Pro", R.raw.pen_parker);

    private final String mBluetoothName;
    @RawRes
    final int image;

    PenModel(String bluetoothName, @RawRes int image) {
        mBluetoothName = bluetoothName;
        this.image = image;
    }

    /** The model of an official Lenovo pen, or null for any other device. */
    @Nullable
    static PenModel of(BluetoothDevice device) {
        final String name = device.getName();
        if (name == null) {
            return null;
        }
        for (PenModel model : values()) {
            if (name.contains(model.mBluetoothName)) {
                return model;
            }
        }
        return null;
    }
}
