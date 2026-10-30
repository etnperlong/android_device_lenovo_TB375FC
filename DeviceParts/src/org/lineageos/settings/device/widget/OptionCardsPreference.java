/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.lineageos.settings.device.R;

/**
 * A choice shown as a row of cards, as stock ZUI shows the color modes: the title as heading,
 * the summary below it, then one card per option with an icon above its label. The owner sets
 * the options and the value; a tap calls the change listener with the option's value and
 * checks the card if the listener accepts it.
 *
 * The layout defaults to R.layout.preference_option_cards; one given with app:layout must have
 * the same ids (android:id/title, android:id/summary, R.id.option_cards). It has to be
 * app:layout: androidx reads that before android:layout, and the Settings theme's preference
 * style sets it.
 */
public class OptionCardsPreference extends Preference {
    private int[] mValues = new int[0];
    private CharSequence[] mLabels = new CharSequence[0];
    private int[] mIcons = new int[0];
    private int mValue;

    public OptionCardsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!hasLayoutAttribute(attrs)) {
            setLayoutResource(R.layout.preference_option_cards);
        }
        setSelectable(false);
        setPersistent(false);
    }

    private static boolean hasLayoutAttribute(AttributeSet attrs) {
        if (attrs != null) {
            for (int i = 0; i < attrs.getAttributeCount(); i++) {
                if (attrs.getAttributeNameResource(i) == androidx.preference.R.attr.layout) {
                    return true;
                }
            }
        }
        return false;
    }

    public OptionCardsPreference(Context context) {
        this(context, null);
    }

    /** One card per value, with its label and drawable resource (0 for none). */
    public void setOptions(int[] values, CharSequence[] labels, int[] icons) {
        if (values.length != labels.length || values.length != icons.length) {
            throw new IllegalArgumentException("Options of different lengths");
        }
        mValues = values.clone();
        mLabels = labels.clone();
        mIcons = icons.clone();
        notifyChanged();
    }

    public int getValue() {
        return mValue;
    }

    public void setValue(int value) {
        if (value != mValue) {
            mValue = value;
            notifyChanged();
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        // The view may have been bound to another OptionCardsPreference before.
        final RadioGroup group = (RadioGroup) holder.findViewById(R.id.option_cards);
        group.setOnCheckedChangeListener(null);
        if (group.getChildCount() != mValues.length) {
            group.removeAllViews();
            final LayoutInflater inflater = LayoutInflater.from(group.getContext());
            for (int i = 0; i < mValues.length; i++) {
                final View card = inflater.inflate(R.layout.option_card, group, false);
                card.setId(View.generateViewId());
                group.addView(card);
            }
        }
        group.clearCheck();
        for (int i = 0; i < mValues.length; i++) {
            final RadioButton card = (RadioButton) group.getChildAt(i);
            card.setText(mLabels[i]);
            card.setCompoundDrawablesRelativeWithIntrinsicBounds(0, mIcons[i], 0, 0);
            card.setEnabled(isEnabled());
            if (mValues[i] == mValue) {
                group.check(card.getId());
            }
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            final int index = g.indexOfChild(g.findViewById(checkedId));
            if (index < 0 || mValues[index] == mValue) {
                return;
            }
            if (callChangeListener(mValues[index])) {
                setValue(mValues[index]);
            } else {
                notifyChanged();
            }
        });
    }
}
