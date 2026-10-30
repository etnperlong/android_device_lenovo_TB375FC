/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settingslib.widget.GroupSectionDividerMixin;
import com.android.settingslib.widget.OnScreenWidgetMixin;

import org.lineageos.settings.device.R;

/**
 * Fine print at the end of a page: the title (a trademark notice), then the note that the page
 * only drives the vendor's own components, ending in a "Disclaimer" link. The whole text is one
 * touch target, so the link does not have to be hit exactly at 12sp.
 *
 * Like SettingsLib's FooterPreference it sits on the page background, outside the cards
 * (GroupSectionDividerMixin), at the on-screen widget padding (OnScreenWidgetMixin), so it lines
 * up with the category titles.
 */
public class DisclaimerFooterPreference extends Preference
        implements GroupSectionDividerMixin, OnScreenWidgetMixin {
    private View.OnClickListener mDisclaimerListener;

    public DisclaimerFooterPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_disclaimer_footer);
        setSelectable(false);
        setPersistent(false);
    }

    /** Called when the footer is tapped. */
    public void setOnDisclaimerClickListener(View.OnClickListener listener) {
        mDisclaimerListener = listener;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final TextView text = (TextView) holder.findViewById(R.id.disclaimer_footer_text);
        final Context context = getContext();
        final CharSequence notice = context.getText(R.string.disclaimer_footer_notice);
        final SpannableStringBuilder builder = new SpannableStringBuilder()
                .append(getTitle()).append('\n').append(notice);
        // CJK sentences end in full-width punctuation and take no space before the next one.
        if (!endsWithFullWidth(notice)) {
            builder.append(' ');
        }
        final int linkStart = builder.length();
        builder.append(context.getText(R.string.disclaimer_title));
        builder.setSpan(new ForegroundColorSpan(getLinkColor(context)), linkStart,
                builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new UnderlineSpan(), linkStart, builder.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setText(builder);
        text.setOnClickListener(mDisclaimerListener);
    }

    private static boolean endsWithFullWidth(CharSequence text) {
        if (text.length() == 0) {
            return false;
        }
        final char last = text.charAt(text.length() - 1);
        return (last >= '\u3000' && last <= '\u303f') || (last >= '\uff00' && last <= '\uffef');
    }

    private static int getLinkColor(Context context) {
        final TypedArray a = context.obtainStyledAttributes(
                new int[] {android.R.attr.colorAccent});
        final int color = a.getColor(0, 0);
        a.recycle();
        return color;
    }
}
