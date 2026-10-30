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
import androidx.preference.PreferenceCategory;
import androidx.preference.SeekBarPreference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;
import org.lineageos.settings.device.widget.DisclaimerFooter;
import org.lineageos.settings.device.widget.OptionCardsPreference;

/**
 * Dolby Atmos settings, after stock ZUI Settings (sound/dolby): the mode as a row of cards with
 * Off for Dolby off, the parameter stock exposes for the chosen mode (Music: intelligent
 * equalizer preset, Movie: dialogue enhancer; Dynamic keeps its own tuning), the entry to the
 * graphic equalizer of all three modes (DolbyEqualizerFragment, hidden while Dolby is off) and
 * the app modes. Everything else but the cards is disabled while Dolby is off. Values are read
 * from and written to settings only; DolbyService applies them.
 */
public class DolbyFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String KEY_MODE = "dolby_mode";
    private static final String KEY_IEQ_PRESET = "dolby_ieq_preset";
    private static final String KEY_DIALOGUE_LEVEL = "dolby_dialogue_level";
    /** Card value for Dolby off; the DAX profiles are 0 and up. */
    private static final int MODE_OFF = -1;

    private ContentResolver mResolver;
    private OptionCardsPreference mMode;
    private ListPreference mIeqPreset;
    private PreferenceCategory mGeqCategory;
    private SeekBarPreference mDialogueLevel;
    private PreferenceCategory mMusicCategory;
    private PreferenceCategory mMovieCategory;
    private PreferenceCategory mAppsCategory;

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

        mMode = findPreference(KEY_MODE);
        mIeqPreset = findPreference(KEY_IEQ_PRESET);
        mGeqCategory = findPreference("category_dolby_geq");
        mDialogueLevel = findPreference(KEY_DIALOGUE_LEVEL);
        mMusicCategory = findPreference("category_dolby_music");
        mMovieCategory = findPreference("category_dolby_movie");
        mAppsCategory = findPreference("category_dolby_apps");

        mMode.setOptions(
                new int[] {DolbySettings.PROFILE_DYNAMIC, DolbySettings.PROFILE_MOVIE,
                        DolbySettings.PROFILE_MUSIC, MODE_OFF},
                new CharSequence[] {getString(R.string.dolby_profile_dynamic),
                        getString(R.string.dolby_profile_movie),
                        getString(R.string.dolby_profile_music),
                        getString(R.string.dolby_mode_off)},
                new int[] {R.drawable.ic_dolby_dynamic, R.drawable.ic_dolby_movie,
                        R.drawable.ic_dolby_music, R.drawable.ic_dolby_off});
        mIeqPreset.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        mDialogueLevel.setMin(0);
        mDialogueLevel.setMax(DolbySettings.DIALOGUE_MAX);
        mDialogueLevel.setShowSeekBarValue(true);
        mDialogueLevel.setUpdatesContinuously(false);

        mMode.setOnPreferenceChangeListener(this);
        mIeqPreset.setOnPreferenceChangeListener(this);
        mDialogueLevel.setOnPreferenceChangeListener(this);
        DisclaimerFooter.setUp(this, "dolby_footer", R.string.disclaimer_dolby_message);
    }

    @Override
    public void onResume() {
        super.onResume();
        mResolver.registerContentObserver(
                Settings.Global.getUriFor(DolbySettings.GLOBAL_DAP_STATE), false, mStateObserver);
        mIeqPreset.setValue(String.valueOf(DolbySettings.getIeqPreset(mResolver)));
        mDialogueLevel.setValue(DolbySettings.getDialogueLevel(mResolver));
        updateState();
    }

    @Override
    public void onPause() {
        mResolver.unregisterContentObserver(mStateObserver);
        super.onPause();
    }

    private void updateState() {
        final boolean on = DolbySettings.isDolbyOn(mResolver);
        final int profile = DolbySettings.getProfile(mResolver);
        mMode.setValue(on ? profile : MODE_OFF);
        mMusicCategory.setVisible(profile == DolbySettings.PROFILE_MUSIC);
        mMovieCategory.setVisible(profile == DolbySettings.PROFILE_MOVIE);
        mMusicCategory.setEnabled(on);
        mMovieCategory.setEnabled(on);
        mGeqCategory.setVisible(on);
        mAppsCategory.setEnabled(on);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case KEY_MODE:
                final int mode = (Integer) newValue;
                final boolean ok = mode == MODE_OFF
                        ? DolbySettings.putDolbyOn(mResolver, false)
                        : DolbySettings.putProfile(mResolver, mode)
                                && DolbySettings.putDolbyOn(mResolver, true);
                // The state observer follows daxService's key; the profile is ours.
                updateState();
                return ok;
            case KEY_IEQ_PRESET:
                return DolbySettings.putIeqPreset(mResolver,
                        Integer.parseInt((String) newValue));
            case KEY_DIALOGUE_LEVEL:
                return DolbySettings.putDialogueLevel(mResolver, (Integer) newValue);
            default:
                return false;
        }
    }
}
