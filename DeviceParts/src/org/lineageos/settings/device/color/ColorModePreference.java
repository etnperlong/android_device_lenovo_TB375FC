/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.RadioGroup;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.lineageos.settings.device.R;

/**
 * Top of the screen color page, as in stock ZUI's ScreenTonePrefsX: a picture to judge the
 * colors by, then the color modes as a row of cards. Reads and writes ColorSettings itself.
 */
public class ColorModePreference extends Preference {
    public ColorModePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_color_mode);
        setSelectable(false);
        setPersistent(false);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        final ContentResolver cr = getContext().getContentResolver();
        final RadioGroup group = (RadioGroup) holder.findViewById(R.id.color_mode_group);
        group.setOnCheckedChangeListener(null);
        group.check(viewIdOf(ColorSettings.getMode(cr)));
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (checkedId != -1) {
                ColorSettings.putMode(cr, modeOf(checkedId));
            }
        });
    }

    private static int viewIdOf(int mode) {
        switch (mode) {
            case ColorSettings.MODE_STANDARD:
                return R.id.color_mode_standard;
            case ColorSettings.MODE_NATURAL:
                return R.id.color_mode_natural;
            default:
                return R.id.color_mode_vivid;
        }
    }

    private static int modeOf(int viewId) {
        if (viewId == R.id.color_mode_standard) {
            return ColorSettings.MODE_STANDARD;
        }
        if (viewId == R.id.color_mode_natural) {
            return ColorSettings.MODE_NATURAL;
        }
        return ColorSettings.MODE_VIVID;
    }
}
