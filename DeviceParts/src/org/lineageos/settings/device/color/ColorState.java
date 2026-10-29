/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;

import java.util.Arrays;

/**
 * What the picture quality service is told to show: the settings, or an app's override. The
 * video enhancements are not part of app color settings.
 */
final class ColorState {
    final int mode;
    /** RGB gains in ColorSettings.UNIT. */
    final int[] gains;
    final boolean adaptive;
    /** Level of each ColorSettings.ADJUSTMENTS entry, LEVEL_DEFAULT if unchanged. */
    final int[] levels;
    /** Whether each ColorSettings.VIDEO_FEATURES entry is on. */
    final boolean[] video;

    private ColorState(int mode, int[] gains, boolean adaptive, int[] levels, boolean[] video) {
        this.mode = mode;
        this.gains = gains;
        this.adaptive = adaptive;
        this.levels = levels;
        this.video = video;
    }

    /** The screen color settings. */
    static ColorState fromSettings(ContentResolver cr) {
        final int[] levels = new int[ColorSettings.ADJUSTMENTS.length];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = ColorSettings.ADJUSTMENTS[i].get(cr);
        }
        return new ColorState(ColorSettings.getMode(cr), ColorSettings.getTemperatureGains(cr),
                ColorSettings.isAdaptive(cr), levels, ColorSettings.getVideoFeatures(cr));
    }

    /**
     * This state with an app color setting applied: APP_OFF shows Standard with a neutral white
     * point and no adaptation or adjustments, a mode replaces only the mode.
     */
    ColorState forApp(int profile) {
        if (profile == ColorSettings.APP_OFF) {
            final int[] defaults = new int[levels.length];
            Arrays.fill(defaults, ColorSettings.Adjustment.LEVEL_DEFAULT);
            return new ColorState(ColorSettings.MODE_STANDARD, ColorSettings.neutralGains(),
                    false, defaults, video);
        }
        if (profile == ColorSettings.APP_DEFAULT || profile == mode) {
            return this;
        }
        return new ColorState(ColorSettings.sanitizeMode(profile), gains, adaptive, levels, video);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ColorState)) {
            return false;
        }
        final ColorState other = (ColorState) o;
        return mode == other.mode && adaptive == other.adaptive
                && Arrays.equals(gains, other.gains) && Arrays.equals(levels, other.levels)
                && Arrays.equals(video, other.video);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * (31 * mode + Arrays.hashCode(gains)) + (adaptive ? 1 : 0))
                + Arrays.hashCode(levels)) + Arrays.hashCode(video);
    }

    @Override
    public String toString() {
        return "mode " + mode + ", gains " + Arrays.toString(gains) + ", adaptive " + adaptive
                + ", levels " + Arrays.toString(levels) + ", video " + Arrays.toString(video);
    }
}
