/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import lineageos.hardware.LineageHardwareManager;

import org.lineageos.settings.device.cover.CoverPolicy;
import org.lineageos.settings.device.display.DisplayPolicyService;
import org.lineageos.settings.device.keyboard.KeyboardService;
import org.lineageos.settings.device.pen.PenService;
import org.lineageos.settings.device.touch.TouchSensitivity;

/**
 * Shows one of the preference screens (all settings, or the subset for one Settings page). Every
 * switch is backed by a Secure setting or a kernel node rather than SharedPreferences, so the
 * same state is shown wherever the switch appears, including the Quick Settings tiles.
 */
public class DevicePartsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String ARG_PREFERENCES = "preferences";

    private static final String KEY_PEN_NOTIFICATIONS = "pen_notifications";
    private static final String KEY_KEYBOARD_NOTIFICATIONS = "keyboard_notifications";
    private static final String KEY_PEN_REFRESH_CAP = "pen_refresh_cap";
    private static final String KEY_READING_LOW_REFRESH = "reading_low_refresh";
    private static final String KEY_TOUCH_SENSITIVITY = "touch_sensitivity";
    private static final String KEY_SMART_COVER = "smart_cover";

    static DevicePartsFragment newInstance(int preferencesResId) {
        final Bundle args = new Bundle();
        args.putInt(ARG_PREFERENCES, preferencesResId);
        final DevicePartsFragment fragment = new DevicePartsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(requireArguments().getInt(ARG_PREFERENCES), rootKey);
        for (String key : new String[] {KEY_PEN_NOTIFICATIONS, KEY_KEYBOARD_NOTIFICATIONS,
                KEY_PEN_REFRESH_CAP, KEY_READING_LOW_REFRESH, KEY_TOUCH_SENSITIVITY,
                KEY_SMART_COVER}) {
            final Preference pref = findPreference(key);
            if (pref != null) {
                pref.setOnPreferenceChangeListener(this);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final ContentResolver cr = requireContext().getContentResolver();

        final TwoStatePreference penNotifications = findPreference(KEY_PEN_NOTIFICATIONS);
        if (penNotifications != null) {
            penNotifications.setChecked(Settings.Secure.getInt(cr,
                    PenService.KEY_PEN_NOTIFICATIONS, 1) != 0);
        }

        final TwoStatePreference keyboardNotifications =
                findPreference(KEY_KEYBOARD_NOTIFICATIONS);
        if (keyboardNotifications != null) {
            keyboardNotifications.setChecked(Settings.Secure.getInt(cr,
                    KeyboardService.KEY_KEYBOARD_NOTIFICATIONS, 1) != 0);
        }

        final TwoStatePreference penCap = findPreference(KEY_PEN_REFRESH_CAP);
        if (penCap != null) {
            penCap.setChecked(Settings.Secure.getInt(cr,
                    DisplayPolicyService.KEY_PEN_REFRESH_CAP, 1) != 0);
        }

        final TwoStatePreference reading = findPreference(KEY_READING_LOW_REFRESH);
        if (reading != null) {
            final boolean supported = LineageHardwareManager.getInstance(requireContext())
                    .isSupported(LineageHardwareManager.FEATURE_READING_ENHANCEMENT);
            reading.setEnabled(supported);
            reading.setChecked(supported && Settings.Secure.getInt(cr,
                    DisplayPolicyService.KEY_READING_LOW_REFRESH, 0) != 0);
            if (!supported) {
                reading.setSummary(R.string.reading_low_refresh_unavailable);
            }
        }

        final ListPreference touch = findPreference(KEY_TOUCH_SENSITIVITY);
        if (touch != null) {
            final boolean available = TouchSensitivity.isAvailable();
            touch.setEnabled(available);
            if (available) {
                updateTouchSensitivity(touch, TouchSensitivity.getLevel(requireContext()));
            } else {
                touch.setSummary(R.string.touch_sensitivity_unavailable);
            }
        }

        final TwoStatePreference cover = findPreference(KEY_SMART_COVER);
        if (cover != null) {
            cover.setChecked(Settings.Secure.getInt(cr, CoverPolicy.KEY_SMART_COVER, 1) != 0);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (KEY_TOUCH_SENSITIVITY.equals(preference.getKey())) {
            final int level = Integer.parseInt((String) newValue);
            if (!TouchSensitivity.setLevel(requireContext(), level)) {
                return false;
            }
            updateTouchSensitivity((ListPreference) preference, level);
            return true;
        }
        final boolean enabled = (Boolean) newValue;
        final ContentResolver cr = requireContext().getContentResolver();
        switch (preference.getKey()) {
            case KEY_PEN_NOTIFICATIONS:
                return Settings.Secure.putInt(cr, PenService.KEY_PEN_NOTIFICATIONS,
                        enabled ? 1 : 0);
            case KEY_KEYBOARD_NOTIFICATIONS:
                return Settings.Secure.putInt(cr, KeyboardService.KEY_KEYBOARD_NOTIFICATIONS,
                        enabled ? 1 : 0);
            case KEY_PEN_REFRESH_CAP:
                return Settings.Secure.putInt(cr, DisplayPolicyService.KEY_PEN_REFRESH_CAP,
                        enabled ? 1 : 0);
            case KEY_READING_LOW_REFRESH:
                return Settings.Secure.putInt(cr, DisplayPolicyService.KEY_READING_LOW_REFRESH,
                        enabled ? 1 : 0);
            case KEY_SMART_COVER:
                return Settings.Secure.putInt(cr, CoverPolicy.KEY_SMART_COVER, enabled ? 1 : 0);
            default:
                return false;
        }
    }

    /** Selects the level and shows its name and what it does. */
    private void updateTouchSensitivity(ListPreference touch, int level) {
        touch.setValueIndex(level);
        final CharSequence[] details =
                getResources().getTextArray(R.array.touch_sensitivity_summaries);
        touch.setSummary(touch.getEntries()[level] + "\n" + details[level]);
    }
}
