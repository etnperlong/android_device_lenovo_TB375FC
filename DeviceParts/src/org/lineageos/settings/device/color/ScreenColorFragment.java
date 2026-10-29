/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

/**
 * Screen color settings, laid out like stock ZUI Settings (display/ScreenTonePrefsX): a picture
 * and the color modes, the color temperature wheel and presets, then the switches. Additions:
 * picture adjustments, app color settings and the video picture enhancements. Values are read
 * from and written to settings only; ScreenColorService applies them.
 */
public class ScreenColorFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    private static final String KEY_ADAPTIVE = "color_adaptive";
    private static final String KEY_ADJUST_RESET = "color_adjust_reset";
    private static final String KEY_APP_PROFILES_ENABLED = "color_app_profiles_enabled";
    private static final String KEY_APP_PROFILES = "color_app_profiles";
    private static final String KEY_VIDEO_ENHANCE = "video_enhance";
    /** Preference keys of ColorSettings.ADJUSTMENTS, in the same order. */
    private static final String[] ADJUSTMENT_KEYS = {
            "color_saturation", "color_contrast", "color_sharpness"};
    /** Preference keys of ColorSettings.VIDEO_FEATURES, in the same order. */
    private static final String[] VIDEO_FEATURE_KEYS = {
            "video_sharpness", "video_dynamic_sharpness", "video_iso_sharpness",
            "video_ultra_resolution", "video_dynamic_contrast", "video_color"};

    private ContentResolver mResolver;
    private TwoStatePreference mAdaptive;
    private final SeekBarPreference[] mAdjustments =
            new SeekBarPreference[ColorSettings.ADJUSTMENTS.length];
    private Preference mAdjustReset;
    private TwoStatePreference mAppProfilesEnabled;
    private Preference mAppProfiles;
    private TwoStatePreference mVideoEnhance;
    private final TwoStatePreference[] mVideoFeatures =
            new TwoStatePreference[ColorSettings.VIDEO_FEATURES.length];

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.screen_color, rootKey);
        mResolver = requireContext().getContentResolver();

        mAdaptive = findPreference(KEY_ADAPTIVE);
        mAdaptive.setOnPreferenceChangeListener(this);
        for (int i = 0; i < mAdjustments.length; i++) {
            final SeekBarPreference pref = findPreference(ADJUSTMENT_KEYS[i]);
            pref.setMin(0);
            pref.setMax(ColorSettings.Adjustment.LEVEL_MAX);
            pref.setShowSeekBarValue(true);
            pref.setUpdatesContinuously(false);
            pref.setOnPreferenceChangeListener(this);
            mAdjustments[i] = pref;
        }
        mAdjustReset = findPreference(KEY_ADJUST_RESET);
        mAdjustReset.setOnPreferenceClickListener(this);
        mAppProfilesEnabled = findPreference(KEY_APP_PROFILES_ENABLED);
        mAppProfilesEnabled.setOnPreferenceChangeListener(this);
        mAppProfiles = findPreference(KEY_APP_PROFILES);
        mVideoEnhance = findPreference(KEY_VIDEO_ENHANCE);
        mVideoEnhance.setOnPreferenceChangeListener(this);
        for (int i = 0; i < mVideoFeatures.length; i++) {
            mVideoFeatures[i] = findPreference(VIDEO_FEATURE_KEYS[i]);
            mVideoFeatures[i].setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();
    }

    private void updateState() {
        mAdaptive.setChecked(ColorSettings.isAdaptive(mResolver));
        boolean anyChanged = false;
        for (int i = 0; i < mAdjustments.length; i++) {
            final ColorSettings.Adjustment adjustment = ColorSettings.ADJUSTMENTS[i];
            final int level = adjustment.get(mResolver);
            final boolean unchanged = level == ColorSettings.Adjustment.LEVEL_DEFAULT;
            mAdjustments[i].setValue(unchanged ? adjustment.defaultLevel : level);
            mAdjustments[i].setSummary(unchanged ? getString(R.string.color_adjust_default) : null);
            anyChanged |= !unchanged;
        }
        mAdjustReset.setEnabled(anyChanged);
        final boolean appProfiles = ColorSettings.isAppProfilesEnabled(mResolver);
        mAppProfilesEnabled.setChecked(appProfiles);
        mAppProfiles.setEnabled(appProfiles);
        mVideoEnhance.setChecked(ColorSettings.isVideoEnhance(mResolver));
        for (int i = 0; i < mVideoFeatures.length; i++) {
            mVideoFeatures[i].setChecked(ColorSettings.VIDEO_FEATURES[i].isEnabled(mResolver));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mAdaptive) {
            ColorSettings.setAdaptive(mResolver, (Boolean) newValue);
            return true;
        }
        if (preference == mAppProfilesEnabled) {
            final boolean enabled = (Boolean) newValue;
            ColorSettings.setAppProfilesEnabled(mResolver, enabled);
            mAppProfiles.setEnabled(enabled);
            return true;
        }
        if (preference == mVideoEnhance) {
            ColorSettings.setVideoEnhance(mResolver, (Boolean) newValue);
            return true;
        }
        for (int i = 0; i < mVideoFeatures.length; i++) {
            if (preference == mVideoFeatures[i]) {
                ColorSettings.VIDEO_FEATURES[i].setEnabled(mResolver, (Boolean) newValue);
                return true;
            }
        }
        for (int i = 0; i < mAdjustments.length; i++) {
            if (preference == mAdjustments[i]) {
                ColorSettings.ADJUSTMENTS[i].put(mResolver, (Integer) newValue);
                // The seek bar takes the value after this returns; summaries follow then.
                getListView().post(this::updateState);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mAdjustReset) {
            for (ColorSettings.Adjustment adjustment : ColorSettings.ADJUSTMENTS) {
                adjustment.put(mResolver, adjustment.defaultLevel);
            }
            updateState();
            return true;
        }
        return false;
    }
}
