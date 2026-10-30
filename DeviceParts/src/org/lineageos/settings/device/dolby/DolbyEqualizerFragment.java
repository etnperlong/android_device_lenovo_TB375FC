/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Lifecycle;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

/**
 * The graphic equalizer of the Dynamic, Movie and Music modes on a page of its own, opened from
 * the Dolby page. Reset to flat is the toolbar action, shown while any band is off 0 dB and
 * confirmed first.
 */
public class DolbyEqualizerFragment extends SettingsBasePreferenceFragment {
    private static final String TAG_RESET_DIALOG = "reset_to_flat";

    private ContentResolver mResolver;
    private boolean mFlat;

    /** Follows the gains as the chart writes them, for the reset action. */
    private final ContentObserver mGainsObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    updateFlat();
                }
            };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.dolby_equalizer, rootKey);
        mResolver = requireContext().getContentResolver();
        mFlat = isFlat();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.dolby_equalizer, menu);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                menu.findItem(R.id.dolby_geq_reset).setVisible(!mFlat);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() != R.id.dolby_geq_reset) {
                    return false;
                }
                new ResetDialogFragment().show(getChildFragmentManager(), TAG_RESET_DIALOG);
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onResume() {
        super.onResume();
        mResolver.registerContentObserver(
                Settings.Secure.getUriFor(DolbySettings.KEY_GEQ_GAINS), false, mGainsObserver);
        updateFlat();
    }

    @Override
    public void onPause() {
        mResolver.unregisterContentObserver(mGainsObserver);
        super.onPause();
    }

    private void updateFlat() {
        final boolean flat = isFlat();
        if (flat != mFlat) {
            mFlat = flat;
            requireActivity().invalidateMenu();
        }
    }

    private boolean isFlat() {
        for (int gain : DolbySettings.getGeqGains(mResolver)) {
            if (gain != 0) {
                return false;
            }
        }
        return true;
    }

    /** Asks before resetting; the chart and the action follow the new gains by themselves. */
    public static class ResetDialogFragment extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            return new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dolby_geq_reset_confirm_title)
                    .setMessage(R.string.dolby_geq_reset_confirm_message)
                    .setPositiveButton(R.string.dolby_geq_reset_confirm, (dialog, which) ->
                            DolbySettings.putGeqGains(requireContext().getContentResolver(),
                                    new int[DolbySettings.GEQ_BANDS]))
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
        }
    }
}
