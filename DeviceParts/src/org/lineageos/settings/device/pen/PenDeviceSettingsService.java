/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.pen;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.settingslib.bluetooth.devicesettings.ActionSwitchPreference;
import com.android.settingslib.bluetooth.devicesettings.DeviceInfo;
import com.android.settingslib.bluetooth.devicesettings.DeviceSetting;
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingConfigOptions;
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingId;
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingIntentAction;
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingItem;
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingState;
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingsConfig;
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingsConfigServiceStatus;
import com.android.settingslib.bluetooth.devicesettings.DeviceSettingsProviderServiceStatus;
import com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsConfigProviderService;
import com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsListener;
import com.android.settingslib.bluetooth.devicesettings.IDeviceSettingsProviderService;
import com.android.settingslib.bluetooth.devicesettings.IGetDeviceSettingsConfigCallback;

import org.lineageos.settings.device.AccessoriesActivity;
import org.lineageos.settings.device.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds the pen's entries to Settings' Bluetooth device details page through SettingsLib's
 * device settings API, the one Pixel Buds use: Settings binds ACTION_CONFIG (named in the pen's
 * metadata by {@link PenDetails}) for the page layout, then ACTION_PROVIDER for the entries it
 * names.
 *
 * Once a config is returned, the page shows only the listed preferences, so the layout lists
 * every top-level preference of Settings' bluetooth_device_details_fragment.xml in its order, as
 * Settings does without a config, with the pen entries after the connect/forget buttons. The
 * XML is read from Settings at run time so preferences that Settings adds later stay visible.
 */
public class PenDeviceSettingsService extends Service {
    private static final String TAG = "PenDeviceSettings";

    static final String ACTION_CONFIG = "org.lineageos.settings.device.pen.DEVICE_SETTINGS_CONFIG";
    private static final String ACTION_PROVIDER = "org.lineageos.settings.device.pen.DEVICE_SETTINGS";

    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String DETAILS_XML = "bluetooth_device_details_fragment";
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    /** The connect/forget buttons, which the pen entries follow. */
    private static final String KEY_ACTION_BUTTONS = "action_buttons";

    // App-provided setting IDs, outside the ranges DeviceSettingId defines.
    private static final int SETTING_ID_PEN_SETTINGS = 9001;
    private static final int SETTING_ID_FIRMWARE = 9002;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** Settings' listeners, each with the address of its pen as the cookie. */
    private final RemoteCallbackList<IDeviceSettingsListener> mListeners =
            new RemoteCallbackList<>();
    private BluetoothAdapter mAdapter;

    private final IDeviceSettingsConfigProviderService.Stub mConfigService =
            new IDeviceSettingsConfigProviderService.Stub() {
                @Override
                public void getDeviceSettingsConfig(DeviceInfo device,
                        IGetDeviceSettingsConfigCallback callback) throws RemoteException {
                    getDeviceSettingsConfigWithOptions(device, callback, null);
                }

                @Override
                public void getDeviceSettingsConfigWithOptions(DeviceInfo device,
                        IGetDeviceSettingsConfigCallback callback,
                        DeviceSettingConfigOptions options) throws RemoteException {
                    final DeviceSettingsConfig config = getPen(device) != null
                            ? buildConfig() : null;
                    callback.onResult(
                            new DeviceSettingsConfigServiceStatus(config != null, Bundle.EMPTY),
                            config);
                }
            };

    private final IDeviceSettingsProviderService.Stub mProviderService =
            new IDeviceSettingsProviderService.Stub() {
                @Override
                public DeviceSettingsProviderServiceStatus getServiceStatus() {
                    return new DeviceSettingsProviderServiceStatus(true, Bundle.EMPTY);
                }

                @Override
                public void registerDeviceSettingsListener(DeviceInfo device,
                        IDeviceSettingsListener listener) {
                    final BluetoothDevice pen = getPen(device);
                    if (pen != null) {
                        mListeners.register(listener, pen.getAddress());
                    }
                    // Settings waits for a first list before showing the page.
                    mHandler.post(() -> send(listener, pen));
                }

                @Override
                public void unregisterDeviceSettingsListener(DeviceInfo device,
                        IDeviceSettingsListener listener) {
                    mListeners.unregister(listener);
                }

                @Override
                public void updateDeviceSettings(DeviceInfo device, DeviceSettingState state) {
                    // No entry has a switch or toggles.
                }
            };

    private final PenDetails.Listener mDetailsListener =
            address -> mHandler.post(() -> onFirmwareVersionChanged(address));

    @Override
    public void onCreate() {
        super.onCreate();
        mAdapter = getSystemService(BluetoothManager.class).getAdapter();
        PenDetails.setListener(mDetailsListener);
    }

    @Override
    public void onDestroy() {
        PenDetails.setListener(null);
        mListeners.kill();
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        final String action = intent.getAction();
        if (ACTION_CONFIG.equals(action)) {
            return mConfigService;
        }
        if (ACTION_PROVIDER.equals(action)) {
            return mProviderService;
        }
        return null;
    }

    /** The official Lenovo pen Settings asks about, or null for any other device. */
    @Nullable
    private BluetoothDevice getPen(DeviceInfo device) {
        final String address = device.getBluetoothAddress();
        if (mAdapter == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            return null;
        }
        final BluetoothDevice pen = mAdapter.getRemoteDevice(address);
        return PenModel.of(pen) != null ? pen : null;
    }

    @Nullable
    private DeviceSettingsConfig buildConfig() {
        final List<String> keys = readDetailsPageKeys();
        if (keys == null) {
            return null;
        }
        final List<DeviceSettingItem> items = new ArrayList<>(keys.size() + 2);
        for (String key : keys) {
            items.add(builtinItem(key));
        }
        final int actionButtons = keys.indexOf(KEY_ACTION_BUTTONS);
        final int position = actionButtons >= 0 ? actionButtons + 1 : items.size();
        items.add(position, providedItem(SETTING_ID_FIRMWARE));
        items.add(position, providedItem(SETTING_ID_PEN_SETTINGS));
        return new DeviceSettingsConfig(items, new ArrayList<>(), null, null, Bundle.EMPTY);
    }

    /** The keys of the top-level preferences of Settings' device details page, in order. */
    @Nullable
    private List<String> readDetailsPageKeys() {
        final Resources res;
        try {
            res = getPackageManager().getResourcesForApplication(SETTINGS_PACKAGE);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Settings not found", e);
            return null;
        }
        final int id = res.getIdentifier(DETAILS_XML, "xml", SETTINGS_PACKAGE);
        if (id == 0) {
            Log.e(TAG, "Settings has no " + DETAILS_XML);
            return null;
        }
        final List<String> keys = new ArrayList<>();
        try (XmlResourceParser parser = res.getXml(id)) {
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                // Depth 1 is the PreferenceScreen.
                if (type != XmlPullParser.START_TAG || parser.getDepth() != 2) {
                    continue;
                }
                final int keyRes = parser.getAttributeResourceValue(ANDROID_NAMESPACE, "key", 0);
                final String key = keyRes != 0 ? res.getString(keyRes)
                        : parser.getAttributeValue(ANDROID_NAMESPACE, "key");
                if (key != null) {
                    keys.add(key);
                }
            }
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Failed to read " + DETAILS_XML, e);
            return null;
        }
        return keys;
    }

    private static DeviceSettingItem builtinItem(String key) {
        return new DeviceSettingItem(DeviceSettingId.DEVICE_SETTING_ID_UNKNOWN, null, null, null,
                key, false, null, false, Bundle.EMPTY);
    }

    private DeviceSettingItem providedItem(int settingId) {
        return new DeviceSettingItem(settingId, getPackageName(),
                PenDeviceSettingsService.class.getName(), ACTION_PROVIDER, null, false, null, false,
                Bundle.EMPTY);
    }

    private void onFirmwareVersionChanged(String address) {
        final int count = mListeners.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                if (address.equals(mListeners.getBroadcastCookie(i))) {
                    send(mListeners.getBroadcastItem(i), mAdapter.getRemoteDevice(address));
                }
            }
        } finally {
            mListeners.finishBroadcast();
        }
    }

    /** Sends the pen's entries, or none for a device that is not an official Lenovo pen. */
    private void send(IDeviceSettingsListener listener, @Nullable BluetoothDevice pen) {
        final List<DeviceSetting> settings = new ArrayList<>(2);
        if (pen != null) {
            addPenSettings(settings, pen);
        }
        try {
            listener.onDeviceSettingsChanged(settings);
        } catch (RemoteException e) {
            Log.w(TAG, "Settings listener died", e);
        }
    }

    private void addPenSettings(List<DeviceSetting> settings, BluetoothDevice pen) {
        settings.add(new DeviceSetting.Builder()
                .setSettingId(SETTING_ID_PEN_SETTINGS)
                .setPreference(new ActionSwitchPreference.Builder()
                        .setTitle(getString(R.string.pen_details_settings_title))
                        .setSummary(getString(R.string.pen_details_settings_summary))
                        .setAction(new DeviceSettingIntentAction.Builder()
                                .setIntent(new Intent(this, AccessoriesActivity.class))
                                .build())
                        .build())
                .build());
        // Left out until the firmware version has been read.
        final String firmware = PenDetails.getFirmwareVersion(pen);
        if (firmware != null) {
            settings.add(new DeviceSetting.Builder()
                    .setSettingId(SETTING_ID_FIRMWARE)
                    .setPreference(new ActionSwitchPreference.Builder()
                            .setTitle(getString(R.string.pen_details_firmware_title))
                            .setSummary(firmware)
                            .build())
                    .build());
        }
    }
}
