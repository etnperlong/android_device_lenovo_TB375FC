/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.memory;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;

import org.lineageos.settings.device.R;

import java.util.function.IntConsumer;

/**
 * The RAM expansion size as a stepped slider, from Off at the start to the largest size at the
 * end, with the chosen size next to the title. Sizes past {@link #setMaxIndex} (not enough free
 * storage) cannot be chosen: the slider stops there. The listener gets the size index on every
 * step.
 */
public class RamExpansionSizePreference extends Preference {
    private CharSequence[] mLabels = new CharSequence[0];
    private int mIndex;
    private int mMaxIndex = Integer.MAX_VALUE;
    private IntConsumer mListener;

    public RamExpansionSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_ram_expansion_size);
        setSelectable(false);
        setPersistent(false);
    }

    /** Labels of the sizes, Off first; at least two. */
    public void setLabels(CharSequence[] labels) {
        if (labels.length < 2) {
            throw new IllegalArgumentException("A size slider needs at least two sizes");
        }
        mLabels = labels.clone();
        notifyChanged();
    }

    public void setOnSizeChangedListener(IntConsumer listener) {
        mListener = listener;
    }

    /** Shows {@code index}, even past the maximum: a size chosen when there was room. */
    public void setIndex(int index) {
        mIndex = index;
        notifyChanged();
    }

    /** The largest size index the user may move to. */
    public void setMaxIndex(int maxIndex) {
        mMaxIndex = maxIndex;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        final TextView value = (TextView) holder.findViewById(R.id.ram_expansion_size_value);
        final Slider slider = (Slider) holder.findViewById(R.id.ram_expansion_size_slider);
        final TextView start = (TextView) holder.findViewById(R.id.ram_expansion_size_start);
        final TextView end = (TextView) holder.findViewById(R.id.ram_expansion_size_end);
        final View summary = holder.findViewById(android.R.id.summary);
        summary.setVisibility(TextUtils.isEmpty(getSummary()) ? View.GONE : View.VISIBLE);

        final int last = mLabels.length - 1;
        slider.clearOnChangeListeners();
        slider.setValueFrom(0);
        slider.setValueTo(last);
        slider.setStepSize(1);
        slider.setValue(clamp(mIndex));
        slider.setLabelFormatter(step -> mLabels[Math.round(step)].toString());
        slider.setContentDescription(getTitle());
        slider.setEnabled(isEnabled());
        value.setText(mLabels[clamp(mIndex)]);
        start.setText(mLabels[0]);
        end.setText(mLabels[last]);

        slider.addOnChangeListener((s, step, fromUser) -> {
            if (!fromUser) {
                return;
            }
            int index = Math.round(step);
            if (index > mMaxIndex && index > mIndex) {
                index = Math.max(mIndex, Math.min(mMaxIndex, last));
                s.setValue(index);
            }
            if (index != mIndex) {
                mIndex = index;
                value.setText(mLabels[index]);
                if (mListener != null) {
                    mListener.accept(index);
                }
            }
        });
    }

    private int clamp(int index) {
        return Math.max(0, Math.min(index, mLabels.length - 1));
    }
}
