/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SeekBarPreference;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;
import org.lineageos.settings.device.widget.OptionCardsPreference;

/**
 * Screen color settings, laid out like stock ZUI Settings (display/ScreenTonePrefsX): a picture
 * and the color mode cards, the ambient light switch, then the color temperature wheel and the
 * picture adjustments folded under "Advanced adjustments". Additions: Disabled mode and the app
 * color settings. Values are read from and written to settings only; ScreenColorService applies
 * them.
 */
public class ScreenColorFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    private static final String KEY_MODE = "color_mode";
    private static final String KEY_ADAPTIVE = "color_adaptive";
    private static final String KEY_ADVANCED = "category_color_advanced";
    private static final String KEY_ADJUST_RESET = "color_adjust_reset";
    /** Preference keys of ColorSettings.ADJUSTMENTS, in the same order. */
    private static final String[] ADJUSTMENT_KEYS = {
            "color_saturation", "color_contrast", "color_sharpness"};

    private ContentResolver mResolver;
    private OptionCardsPreference mMode;
    private TwoStatePreference mAdaptive;
    private PreferenceCategory mAdvanced;
    private final SeekBarPreference[] mAdjustments =
            new SeekBarPreference[ColorSettings.ADJUSTMENTS.length];
    private Preference mAdjustReset;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.screen_color, rootKey);
        mResolver = requireContext().getContentResolver();

        mMode = findPreference(KEY_MODE);
        mMode.setOptions(
                new int[] {ColorSettings.MODE_STANDARD, ColorSettings.MODE_VIVID,
                        ColorSettings.MODE_DISABLED},
                new CharSequence[] {getString(R.string.color_mode_standard),
                        getString(R.string.color_mode_vivid),
                        getString(R.string.color_mode_disabled)},
                new int[] {R.drawable.ic_color_mode_standard, R.drawable.ic_color_mode_vivid,
                        R.drawable.ic_color_mode_disabled});
        mMode.setOnPreferenceChangeListener(this);
        mAdaptive = findPreference(KEY_ADAPTIVE);
        mAdaptive.setOnPreferenceChangeListener(this);
        mAdvanced = findPreference(KEY_ADVANCED);
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
    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();
    }

    private void updateState() {
        final int mode = ColorSettings.getMode(mResolver);
        mMode.setValue(mode);
        // Disabled keeps these settings but does not apply them.
        final boolean tuning = mode != ColorSettings.MODE_DISABLED;
        mAdaptive.setEnabled(tuning);
        mAdaptive.setChecked(ColorSettings.isAdaptive(mResolver));
        mAdvanced.setEnabled(tuning);

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
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mMode) {
            ColorSettings.putMode(mResolver, (Integer) newValue);
            updateState();
            return true;
        }
        if (preference == mAdaptive) {
            ColorSettings.setAdaptive(mResolver, (Boolean) newValue);
            return true;
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
