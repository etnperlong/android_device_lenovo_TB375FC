/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.pen;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Describes an official Lenovo pen to Settings through its Bluetooth metadata, as Fast Pair does
 * for headphones.
 *
 * - METADATA_DEVICE_TYPE Stylus and METADATA_MAIN_ICON make Settings show the advanced header
 *   (the pen's picture and battery) on the device details page and the picture in the device
 *   list (SettingsLib BluetoothUtils.isAdvancedDetailsHeader), and show the stylus settings.
 * - METADATA_MAIN_BATTERY is the battery level that the header shows for a stylus; the Bluetooth
 *   stack's Battery Service client only fills in BluetoothDevice.getBatteryLevel().
 * - The device settings config service in the Fast Pair customized fields makes Settings ask
 *   {@link PenDeviceSettingsService} for the layout of the details page
 *   (SettingsLib DeviceSettingServiceConnection).
 * - METADATA_SOFTWARE_VERSION holds the pen firmware read by {@link PenFirmwareReader}.
 *
 * The Bluetooth stack keeps metadata across reboots and deletes it when the pen is unpaired.
 */
final class PenDetails {
    private static final String TAG = "PenDetails";

    private static final String SETTINGS_PACKAGE = "com.android.settings";

    /** BluetoothDevice.METADATA_FAST_PAIR_CUSTOMIZED_FIELDS, hidden from the SDK. */
    private static final int METADATA_FAST_PAIR_CUSTOMIZED_FIELDS = 25;
    /** Tags of the customized fields naming the config service (DeviceSettingServiceConnection). */
    private static final String TAG_CONFIG_PACKAGE = "DEVICE_SETTINGS_CONFIG_PACKAGE_NAME";
    private static final String TAG_CONFIG_CLASS = "DEVICE_SETTINGS_CONFIG_CLASS";
    private static final String TAG_CONFIG_ACTION = "DEVICE_SETTINGS_CONFIG_ACTION";

    /** Receives the address of a pen whose firmware version changed. */
    interface Listener {
        void onFirmwareVersionChanged(String address);
    }

    private static Listener sListener;

    private PenDetails() {
    }

    /** Publishes the type, picture and settings provider of a connected pen. */
    static void publish(Context context, BluetoothDevice device, PenModel model) {
        setString(device, BluetoothDevice.METADATA_DEVICE_TYPE, BluetoothDevice.DEVICE_TYPE_STYLUS);

        final Uri image = PenImageProvider.uriFor(model);
        // Settings takes a persistable grant before loading the picture.
        context.grantUriPermission(SETTINGS_PACKAGE, image,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        setString(device, BluetoothDevice.METADATA_MAIN_ICON, image.toString());

        String fields = getString(device, METADATA_FAST_PAIR_CUSTOMIZED_FIELDS);
        fields = withTag(fields, TAG_CONFIG_PACKAGE, context.getPackageName());
        fields = withTag(fields, TAG_CONFIG_CLASS, PenDeviceSettingsService.class.getName());
        fields = withTag(fields, TAG_CONFIG_ACTION, PenDeviceSettingsService.ACTION_CONFIG);
        setString(device, METADATA_FAST_PAIR_CUSTOMIZED_FIELDS, fields);
    }

    /** Sets the battery level shown in the header, BATTERY_LEVEL_UNKNOWN when there is none. */
    static void setBatteryLevel(BluetoothDevice device, int level) {
        setString(device, BluetoothDevice.METADATA_MAIN_BATTERY, String.valueOf(
                level >= 0 && level <= 100 ? level : BluetoothDevice.BATTERY_LEVEL_UNKNOWN));
    }

    /** The firmware version of a pen, or null if it has not been read. */
    @Nullable
    static String getFirmwareVersion(BluetoothDevice device) {
        return getString(device, BluetoothDevice.METADATA_SOFTWARE_VERSION);
    }

    static void setFirmwareVersion(BluetoothDevice device, String version) {
        if (version.equals(getFirmwareVersion(device))) {
            return;
        }
        setString(device, BluetoothDevice.METADATA_SOFTWARE_VERSION, version);
        final Listener listener;
        synchronized (PenDetails.class) {
            listener = sListener;
        }
        if (listener != null) {
            listener.onFirmwareVersionChanged(device.getAddress());
        }
    }

    /** Registers the single listener, or clears it with null. */
    static synchronized void setListener(@Nullable Listener listener) {
        sListener = listener;
    }

    /** Replaces or appends <tag>value</tag> in the Fast Pair customized fields. */
    private static String withTag(@Nullable String fields, String tag, String value) {
        final String element = "<" + tag + ">" + value + "</" + tag + ">";
        if (fields == null) {
            return element;
        }
        return fields.replaceAll(
                Pattern.quote("<" + tag + ">") + ".*?" + Pattern.quote("</" + tag + ">"), "")
                + element;
    }

    @Nullable
    private static String getString(BluetoothDevice device, int key) {
        final byte[] value = device.getMetadata(key);
        return value != null ? new String(value, StandardCharsets.UTF_8) : null;
    }

    private static void setString(BluetoothDevice device, int key, String value) {
        if (!device.setMetadata(key, value.getBytes(StandardCharsets.UTF_8))) {
            Log.w(TAG, "Failed to set metadata " + key + " of " + device.getAnonymizedAddress());
        }
    }
}
