/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.widget.FooterPreference;

import org.lineageos.settings.device.R;

/**
 * Footer of the pages for a licensed vendor feature (MediaTek MiraVision, Dolby Atmos): the
 * trademark notice, a note that the page only drives the vendor's own components, and a
 * "Disclaimer" link that shows the disclaimer in a dialog.
 */
public final class DisclaimerFooter {
    private static final String TAG_DIALOG = "disclaimer";

    private DisclaimerFooter() {}

    /**
     * Fills in the footer preference.
     *
     * @param trademarkRes the trademark notice
     * @param disclaimerRes the disclaimer shown by the link
     */
    public static void setUp(PreferenceFragmentCompat fragment, String key, int trademarkRes,
            int disclaimerRes) {
        final FooterPreference footer = fragment.findPreference(key);
        final Context context = fragment.requireContext();
        footer.setTitle(context.getText(trademarkRes) + "\n"
                + context.getText(R.string.disclaimer_footer_notice));
        footer.setLearnMoreText(context.getText(R.string.disclaimer_title));
        footer.setLearnMoreAction(v -> {
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
