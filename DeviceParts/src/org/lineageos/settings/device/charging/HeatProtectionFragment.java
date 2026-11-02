/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.charging;

import android.content.ContentResolver;
import android.os.Bundle;

import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SeekBarPreference;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

/** The switch of the charging heat protection, its minimum battery level and threshold. */
public class HeatProtectionFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String KEY_ENABLED = "heat_protection_enabled";
    private static final String KEY_CATEGORY = "category_heat_protection";
    private static final String KEY_MIN_LEVEL = "heat_protection_min_level";
    private static final String KEY_THRESHOLD = "heat_protection_threshold";

    private ContentResolver mResolver;
    private MainSwitchPreference mEnabled;
    private PreferenceCategory mCategory;
    private SeekBarPreference mMinLevel;
    private DropDownPreference mThreshold;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.heat_protection, rootKey);
        mResolver = requireContext().getContentResolver();

        mEnabled = findPreference(KEY_ENABLED);
        mEnabled.setOnPreferenceChangeListener(this);
        mCategory = findPreference(KEY_CATEGORY);
        mMinLevel = findPreference(KEY_MIN_LEVEL);
        mMinLevel.setMin(ChargingSettings.HEAT_MIN_LEVEL_LOWEST);
        mMinLevel.setMax(ChargingSettings.HEAT_MIN_LEVEL_HIGHEST);
        mMinLevel.setUpdatesContinuously(false);
        mMinLevel.setOnPreferenceChangeListener(this);
        mThreshold = findPreference(KEY_THRESHOLD);
        mThreshold.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();
    }

    private void updateState() {
        final boolean enabled = ChargingSettings.isHeatProtectionEnabled(mResolver);
        mEnabled.setChecked(enabled);
        mCategory.setEnabled(enabled);
        final int level = ChargingSettings.getHeatMinLevel(mResolver);
        mMinLevel.setValue(level);
        mMinLevel.setSummary(getString(R.string.heat_protection_min_level_summary, level));
        mThreshold.setValueIndex(ChargingSettings.getHeatThresholdIndex(mResolver));
        mThreshold.setSummary(mThreshold.getEntry());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mEnabled) {
            ChargingSettings.setHeatProtectionEnabled(mResolver, (Boolean) newValue);
        } else if (preference == mMinLevel) {
            ChargingSettings.setHeatMinLevel(mResolver, (Integer) newValue);
        } else if (preference == mThreshold) {
            ChargingSettings.setHeatThresholdIndex(mResolver,
                    Integer.parseInt((String) newValue));
        } else {
            return false;
        }
        // The preference takes the value after this returns; the summaries follow then.
        getListView().post(this::updateState);
        return true;
    }
}
