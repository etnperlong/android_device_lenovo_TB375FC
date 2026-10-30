/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceFragmentCompat;

import org.lineageos.settings.device.R;

/**
 * Disclaimer of the pages for a licensed vendor feature (MediaTek MiraVision, Dolby Atmos),
 * shown in a dialog when the page's {@link DisclaimerFooterPreference} is tapped.
 */
public final class DisclaimerFooter {
    private static final String TAG_DIALOG = "disclaimer";

    private DisclaimerFooter() {}

    /**
     * Makes the footer open the disclaimer.
     *
     * @param disclaimerRes the disclaimer text
     */
    public static void setUp(PreferenceFragmentCompat fragment, String key, int disclaimerRes) {
        final DisclaimerFooterPreference footer = fragment.findPreference(key);
        footer.setOnDisclaimerClickListener(v -> {
            if (fragment.getChildFragmentManager().findFragmentByTag(TAG_DIALOG) == null) {
                DisclaimerDialogFragment.newInstance(disclaimerRes)
                        .show(fragment.getChildFragmentManager(), TAG_DIALOG);
            }
        });
    }

    /** The disclaimer, with only a close button. */
    public static class DisclaimerDialogFragment extends DialogFragment {
        private static final String ARG_MESSAGE = "message";

        static DisclaimerDialogFragment newInstance(int messageRes) {
            final DisclaimerDialogFragment fragment = new DisclaimerDialogFragment();
            final Bundle args = new Bundle();
            args.putInt(ARG_MESSAGE, messageRes);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            return new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.disclaimer_title)
                    .setMessage(requireArguments().getInt(ARG_MESSAGE))
                    .setPositiveButton(R.string.disclaimer_close, null)
                    .create();
        }
    }
}
