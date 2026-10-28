/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.widget.RadioGroup;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.lineageos.settings.device.R;

/**
 * The color temperature part of stock ZUI's ScreenTonePrefsX: the color wheel, and the
 * Standard, Warm and Cool presets as a row of cards below it. A point picked on the wheel is a
 * custom temperature, with no preset checked; picking a preset moves the dot to stock's spot for
 * it. Like stock, the screen follows the dot while it is dragged. Reads and writes
 * ColorSettings itself.
 */
public class ColorTemperaturePreference extends Preference {
    /** While dragging, the custom point is written at most this often. */
    private static final long DRAG_WRITE_INTERVAL_MS = 80;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ContentResolver mResolver;
    private long mLastWriteUptime;
    private float mPendingX;
    private float mPendingY;
    private final Runnable mWritePending = this::writePending;

    public ColorTemperaturePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_color_temperature);
        setSelectable(false);
        setPersistent(false);
        mResolver = context.getContentResolver();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        final ColorWheelView wheel = (ColorWheelView) holder.findViewById(R.id.color_wheel);
        final RadioGroup group = (RadioGroup) holder.findViewById(R.id.color_temperature_group);

        final float[] point = ColorSettings.getTemperaturePoint(mResolver);
        wheel.setPoint(point[0], point[1]);
        group.setOnCheckedChangeListener(null);
        final int temperature = ColorSettings.getTemperature(mResolver);
        if (temperature == ColorSettings.TEMPERATURE_CUSTOM) {
            group.clearCheck();
        } else {
            group.check(viewIdOf(temperature));
        }

        // clearCheck() below reports -1, which is not a preset choice.
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (checkedId == -1) {
                return;
            }
            mHandler.removeCallbacks(mWritePending);
            ColorSettings.putTemperature(mResolver, temperatureOf(checkedId));
            final float[] presetPoint = ColorSettings.getTemperaturePoint(mResolver);
            wheel.setPoint(presetPoint[0], presetPoint[1]);
        });

        wheel.setOnPointChangedListener((x, y, done) -> {
            group.clearCheck();
            mPendingX = x;
            mPendingY = y;
            mHandler.removeCallbacks(mWritePending);
            final long sinceLast = SystemClock.uptimeMillis() - mLastWriteUptime;
            if (done || sinceLast >= DRAG_WRITE_INTERVAL_MS) {
                writePending();
            } else {
                mHandler.postDelayed(mWritePending, DRAG_WRITE_INTERVAL_MS - sinceLast);
            }
        });
    }

    private void writePending() {
        mLastWriteUptime = SystemClock.uptimeMillis();
        ColorSettings.putCustomTemperature(mResolver, mPendingX, mPendingY);
    }

    private static int viewIdOf(int temperature) {
        switch (temperature) {
            case ColorSettings.TEMPERATURE_WARM:
                return R.id.color_temperature_warm;
            case ColorSettings.TEMPERATURE_COOL:
                return R.id.color_temperature_cool;
            default:
                return R.id.color_temperature_standard;
        }
    }

    private static int temperatureOf(int viewId) {
        if (viewId == R.id.color_temperature_warm) {
            return ColorSettings.TEMPERATURE_WARM;
        }
        if (viewId == R.id.color_temperature_cool) {
            return ColorSettings.TEMPERATURE_COOL;
        }
        return ColorSettings.TEMPERATURE_STANDARD;
    }
}
