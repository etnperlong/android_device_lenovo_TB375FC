/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;

import java.util.Arrays;

/**
 * What the picture quality service is told to show: the settings, or an app's override. While
 * the screen color mode is Disabled, the stored white point, adaptation and adjustments are kept
 * but not applied. The video enhancements are not part of app color settings.
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
        final int mode = ColorSettings.getMode(cr);
        final boolean[] video = ColorSettings.getVideoFeatures(cr);
        if (mode == ColorSettings.MODE_DISABLED) {
            return disabled(video);
        }
        final int[] levels = new int[ColorSettings.ADJUSTMENTS.length];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = ColorSettings.ADJUSTMENTS[i].get(cr);
        }
        return new ColorState(mode, ColorSettings.getTemperatureGains(cr),
                ColorSettings.isAdaptive(cr), levels, video);
    }

    /** Vendor defaults: neutral white point, no adaptation, default adjustment levels. */
    private static ColorState disabled(boolean[] video) {
        return new ColorState(ColorSettings.MODE_DISABLED, ColorSettings.neutralGains(), false,
                defaultLevels(), video);
    }

    /**
     * This state with an app color setting applied: Disabled as for the screen, Standard or
     * Vivid replaces only the mode.
     */
    ColorState forApp(int profile) {
        if (!ColorSettings.isAppMode(profile) || profile == mode) {
            return this;
        }
        if (profile == ColorSettings.MODE_DISABLED) {
            return disabled(video);
        }
        return new ColorState(profile, gains, adaptive, levels, video);
    }

    private static int[] defaultLevels() {
        final int[] levels = new int[ColorSettings.ADJUSTMENTS.length];
        Arrays.fill(levels, ColorSettings.Adjustment.LEVEL_DEFAULT);
        return levels;
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
