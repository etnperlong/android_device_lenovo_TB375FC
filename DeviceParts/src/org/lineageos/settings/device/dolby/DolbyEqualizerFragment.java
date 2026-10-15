/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

/** Music mode graphic equalizer: one slider per band, -6..+6 dB like stock GeqView. */
public class DolbyEqualizerFragment extends SettingsBasePreferenceFragment {
    private ContentResolver mResolver;
    private final SeekBarPreference[] mBands = new SeekBarPreference[DolbySettings.GEQ_BANDS];

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = requireContext();
        mResolver = context.getContentResolver();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);

        for (int i = 0; i < DolbySettings.GEQ_BANDS; i++) {
            final int index = i;
            final SeekBarPreference band = new SeekBarPreference(context);
            band.setKey("geq_band_" + i);
            band.setPersistent(false);
            band.setTitle(formatFrequency(context, DolbySettings.GEQ_FREQUENCIES[i]));
            band.setMin(-DolbySettings.GEQ_MAX_DB);
            band.setMax(DolbySettings.GEQ_MAX_DB);
            band.setShowSeekBarValue(true);
            band.setUpdatesContinuously(false);
            band.setOnPreferenceChangeListener((preference, newValue) -> {
                // The slider stores newValue only after this listener returns.
                final int[] gains = currentGains();
                gains[index] = (Integer) newValue;
                DolbySettings.putGeqGains(mResolver, gains);
                return true;
            });
            mBands[i] = band;
            screen.addPreference(band);
        }

        final Preference reset = new Preference(context);
        reset.setKey("geq_reset");
        reset.setTitle(R.string.dolby_geq_reset_title);
        reset.setOnPreferenceClickListener(preference -> {
            for (SeekBarPreference band : mBands) {
                band.setValue(0);
            }
            DolbySettings.putGeqGains(mResolver, currentGains());
            return true;
        });
        screen.addPreference(reset);

        setPreferenceScreen(screen);
    }

    @Override
    public void onResume() {
        super.onResume();
        final int[] gains = DolbySettings.getGeqGains(mResolver);
        for (int i = 0; i < DolbySettings.GEQ_BANDS; i++) {
            mBands[i].setValue(gains[i]);
        }
    }

    private int[] currentGains() {
        final int[] gains = new int[DolbySettings.GEQ_BANDS];
        for (int i = 0; i < DolbySettings.GEQ_BANDS; i++) {
            gains[i] = mBands[i].getValue();
        }
        return gains;
    }

    private static String formatFrequency(Context context, int hz) {
        return hz < 1000
                ? context.getString(R.string.dolby_geq_band_hz, hz)
                : context.getString(R.string.dolby_geq_band_khz, hz / 1000f);
    }
}
