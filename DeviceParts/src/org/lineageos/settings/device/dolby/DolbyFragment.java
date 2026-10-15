/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

/**
 * Dolby Atmos settings, laid out like stock ZUI Settings (sound/dolby): switch, default mode,
 * the Music equalizers, the Movie dialogue enhancer, and the app modes. Values are read from and
 * written to settings only; DolbyService applies them.
 */
public class DolbyFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String KEY_ENABLE = "dolby_enable";
    private static final String KEY_PROFILE = "dolby_profile";
    private static final String KEY_IEQ_PRESET = "dolby_ieq_preset";
    private static final String KEY_GEQ = "dolby_geq";
    private static final String KEY_DIALOGUE_LEVEL = "dolby_dialogue_level";
    private static final String KEY_APP_PROFILES_ENABLED = "dolby_app_profiles_enabled";
    private static final String KEY_APP_PROFILES = "dolby_app_profiles";

    private ContentResolver mResolver;
    private TwoStatePreference mEnable;
    private ListPreference mProfile;
    private ListPreference mIeqPreset;
    private Preference mGeq;
    private SeekBarPreference mDialogueLevel;
    private TwoStatePreference mAppProfilesEnabled;
    private Preference mAppProfiles;

    /** The Quick Settings tile can switch Dolby while this screen is shown. */
    private final ContentObserver mStateObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    updateState();
                }
            };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.dolby_parts, rootKey);
        mResolver = requireContext().getContentResolver();

        mEnable = findPreference(KEY_ENABLE);
        mProfile = findPreference(KEY_PROFILE);
        mIeqPreset = findPreference(KEY_IEQ_PRESET);
        mGeq = findPreference(KEY_GEQ);
        mDialogueLevel = findPreference(KEY_DIALOGUE_LEVEL);
        mAppProfilesEnabled = findPreference(KEY_APP_PROFILES_ENABLED);
        mAppProfiles = findPreference(KEY_APP_PROFILES);

        mProfile.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        mIeqPreset.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        mDialogueLevel.setMin(0);
        mDialogueLevel.setMax(DolbySettings.DIALOGUE_MAX);
        mDialogueLevel.setShowSeekBarValue(true);
        mDialogueLevel.setUpdatesContinuously(false);

        mEnable.setOnPreferenceChangeListener(this);
        mProfile.setOnPreferenceChangeListener(this);
        mIeqPreset.setOnPreferenceChangeListener(this);
        mDialogueLevel.setOnPreferenceChangeListener(this);
        mAppProfilesEnabled.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mResolver.registerContentObserver(
                Settings.Global.getUriFor(DolbySettings.GLOBAL_DAP_STATE), false, mStateObserver);
        mProfile.setValue(String.valueOf(DolbySettings.getProfile(mResolver)));
        mIeqPreset.setValue(String.valueOf(DolbySettings.getIeqPreset(mResolver)));
        mDialogueLevel.setValue(DolbySettings.getDialogueLevel(mResolver));
        mAppProfilesEnabled.setChecked(DolbySettings.isAppProfilesEnabled(mResolver));
        updateState();
    }

    @Override
    public void onPause() {
        mResolver.unregisterContentObserver(mStateObserver);
        super.onPause();
    }

    private void updateState() {
        final boolean on = DolbySettings.isDolbyOn(mResolver);
        mEnable.setChecked(on);
        mProfile.setEnabled(on);
        mIeqPreset.setEnabled(on);
        mGeq.setEnabled(on);
        mDialogueLevel.setEnabled(on);
        mAppProfilesEnabled.setEnabled(on);
        mAppProfiles.setEnabled(on && mAppProfilesEnabled.isChecked());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case KEY_ENABLE:
                // The settings observer refreshes the other preferences.
                return Settings.Global.putInt(mResolver, DolbySettings.GLOBAL_DAP_STATE,
                        (Boolean) newValue ? 1 : 0);
            case KEY_PROFILE:
                return Settings.Secure.putInt(mResolver, DolbySettings.KEY_PROFILE,
                        Integer.parseInt((String) newValue));
            case KEY_IEQ_PRESET:
                return Settings.Secure.putInt(mResolver, DolbySettings.KEY_IEQ_PRESET,
                        Integer.parseInt((String) newValue));
            case KEY_DIALOGUE_LEVEL:
                return Settings.Secure.putInt(mResolver, DolbySettings.KEY_DIALOGUE_LEVEL,
                        (Integer) newValue);
            case KEY_APP_PROFILES_ENABLED:
                mAppProfiles.setEnabled((Boolean) newValue);
                return Settings.Secure.putInt(mResolver, DolbySettings.KEY_APP_PROFILES_ENABLED,
                        (Boolean) newValue ? 1 : 0);
            default:
                return false;
        }
    }
}
