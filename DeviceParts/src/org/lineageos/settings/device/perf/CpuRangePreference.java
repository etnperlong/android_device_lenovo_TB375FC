/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.RangeSlider;

import org.lineageos.settings.device.R;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The frequency limits of one CPU cluster as one row: a dropdown for the minimum, a two-thumb
 * slider for the range and a dropdown for the maximum, all three along the cluster's cpufreq
 * table and kept in step. The minimum never exceeds the maximum: moving one past the other
 * takes the other along. The listener gets table indexes once a drag ends or a dropdown
 * changes, not on every drag step.
 */
public class CpuRangePreference extends Preference {
    interface OnLimitsChangedListener {
        void onLimitsChanged(int minIndex, int maxIndex);
    }

    private CharSequence[] mFrequencies = new CharSequence[0];
    private CharSequence mMinDescription;
    private CharSequence mMaxDescription;
    private CharSequence mRangeDescription;
    private int mMinIndex;
    private int mMaxIndex;
    private OnLimitsChangedListener mListener;

    public CpuRangePreference(Context context) {
        super(context);
        setLayoutResource(R.layout.preference_cpu_range);
        setSelectable(false);
        setPersistent(false);
    }

    /** Labels of the cluster's frequencies, ascending; at least two. */
    public void setFrequencies(CharSequence[] frequencies) {
        if (frequencies.length < 2) {
            throw new IllegalArgumentException("A CPU range needs at least two frequencies");
        }
        mFrequencies = frequencies.clone();
        setLimits(mMinIndex, mMaxIndex);
    }

    public void setDescriptions(CharSequence minDescription, CharSequence maxDescription,
            CharSequence rangeDescription) {
        mMinDescription = minDescription;
        mMaxDescription = maxDescription;
        mRangeDescription = rangeDescription;
        notifyChanged();
    }

    public void setOnLimitsChangedListener(OnLimitsChangedListener listener) {
        mListener = listener;
    }

    public void setLimits(int minIndex, int maxIndex) {
        mMinIndex = clamp(minIndex);
        mMaxIndex = Math.max(mMinIndex, clamp(maxIndex));
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        // The views may have been bound to another cluster's row before.
        final Spinner minimum = (Spinner) holder.findViewById(R.id.cpu_range_minimum);
        final RangeSlider range = (RangeSlider) holder.findViewById(R.id.cpu_range_slider);
        final Spinner maximum = (Spinner) holder.findViewById(R.id.cpu_range_maximum);
        minimum.setOnItemSelectedListener(null);
        maximum.setOnItemSelectedListener(null);
        range.clearOnChangeListeners();
        range.clearOnSliderTouchListeners();

        final ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(getContext(),
                R.layout.cpu_range_spinner_item, mFrequencies);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        minimum.setAdapter(adapter);
        maximum.setAdapter(adapter);
        minimum.setContentDescription(mMinDescription);
        maximum.setContentDescription(mMaxDescription);
        range.setContentDescription(mRangeDescription);
        range.setValueFrom(0);
        range.setValueTo(mFrequencies.length - 1);
        range.setStepSize(1);
        range.setLabelFormatter(value -> mFrequencies[Math.round(value)].toString());
        show(minimum, maximum, range);

        final boolean enabled = isEnabled();
        minimum.setEnabled(enabled);
        maximum.setEnabled(enabled);
        range.setEnabled(enabled);

        minimum.setOnItemSelectedListener(new SelectionListener(index -> {
            if (index != mMinIndex) {
                mMinIndex = index;
                mMaxIndex = Math.max(mMaxIndex, index);
                show(minimum, maximum, range);
                notifyLimitsChanged();
            }
        }));
        maximum.setOnItemSelectedListener(new SelectionListener(index -> {
            if (index != mMaxIndex) {
                mMaxIndex = index;
                mMinIndex = Math.min(mMinIndex, index);
                show(minimum, maximum, range);
                notifyLimitsChanged();
            }
        }));
        range.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                final List<Float> values = slider.getValues();
                mMinIndex = Math.round(values.get(0));
                mMaxIndex = Math.round(values.get(1));
                minimum.setSelection(mMinIndex, false);
                maximum.setSelection(mMaxIndex, false);
            }
        });
        range.addOnSliderTouchListener(new RangeSlider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(RangeSlider slider) {
            }

            @Override
            public void onStopTrackingTouch(RangeSlider slider) {
                notifyLimitsChanged();
            }
        });
    }

    private void show(Spinner minimum, Spinner maximum, RangeSlider range) {
        minimum.setSelection(mMinIndex, false);
        maximum.setSelection(mMaxIndex, false);
        range.setValues((float) mMinIndex, (float) mMaxIndex);
    }

    private int clamp(int index) {
        return Math.max(0, Math.min(index, mFrequencies.length - 1));
    }

    private void notifyLimitsChanged() {
        if (mListener != null) {
            mListener.onLimitsChanged(mMinIndex, mMaxIndex);
        }
    }

    private static final class SelectionListener implements AdapterView.OnItemSelectedListener {
        private final IntConsumer mAction;

        SelectionListener(IntConsumer action) {
            mAction = action;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            mAction.accept(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}
