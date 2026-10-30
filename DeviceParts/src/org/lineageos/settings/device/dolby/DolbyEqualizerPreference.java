/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.lineageos.settings.device.R;

/** The graphic equalizer's response curve card, on the equalizer page. */
public class DolbyEqualizerPreference extends Preference {
    public DolbyEqualizerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_dolby_equalizer);
        setSelectable(false);
        setPersistent(false);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        ((DolbyEqualizerView) holder.findViewById(R.id.dolby_equalizer_chart)).setGains(
                DolbySettings.getGeqGains(getContext().getContentResolver()));
    }
}
