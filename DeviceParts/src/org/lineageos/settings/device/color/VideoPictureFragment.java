/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;
import org.lineageos.settings.device.widget.OptionCardsPreference;

/**
 * MediaTek's video picture enhancements: On / Off cards, and while On the single features.
 * Values are read from and written to settings only; ScreenColorService applies them.
 */
public class VideoPictureFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String KEY_VIDEO_ENHANCE = "video_enhance";
    private static final String KEY_VIDEO_FEATURES_CATEGORY = "category_video_features";
    /** Preference keys of ColorSettings.VIDEO_FEATURES, in the same order. */
    private static final String[] VIDEO_FEATURE_KEYS = {
            "video_sharpness", "video_dynamic_sharpness", "video_iso_sharpness",
            "video_ultra_resolution", "video_dynamic_contrast", "video_color"};

    private ContentResolver mResolver;
    private OptionCardsPreference mVideoEnhance;
    private PreferenceCategory mVideoFeaturesCategory;
    private final TwoStatePreference[] mVideoFeatures =
            new TwoStatePreference[ColorSettings.VIDEO_FEATURES.length];

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.video_picture, rootKey);
        mResolver = requireContext().getContentResolver();

        mVideoEnhance = findPreference(KEY_VIDEO_ENHANCE);
        mVideoEnhance.setOptions(new int[] {1, 0},
                new CharSequence[] {getString(R.string.video_enhance_on),
                        getString(R.string.video_enhance_off)},
                new int[] {R.drawable.ic_video_enhance_on, R.drawable.ic_video_enhance_off});
        mVideoEnhance.setOnPreferenceChangeListener(this);
        mVideoFeaturesCategory = findPreference(KEY_VIDEO_FEATURES_CATEGORY);
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
        final boolean videoEnhance = ColorSettings.isVideoEnhance(mResolver);
        mVideoEnhance.setValue(videoEnhance ? 1 : 0);
        mVideoFeaturesCategory.setVisible(videoEnhance);
        for (int i = 0; i < mVideoFeatures.length; i++) {
            mVideoFeatures[i].setChecked(ColorSettings.VIDEO_FEATURES[i].isEnabled(mResolver));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mVideoEnhance) {
            ColorSettings.setVideoEnhance(mResolver, (Integer) newValue == 1);
            updateState();
            return true;
        }
        for (int i = 0; i < mVideoFeatures.length; i++) {
            if (preference == mVideoFeatures[i]) {
                ColorSettings.VIDEO_FEATURES[i].setEnabled(mResolver, (Boolean) newValue);
                return true;
            }
        }
        return false;
    }
}
