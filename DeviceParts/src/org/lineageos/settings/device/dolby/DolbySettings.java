/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import android.content.ContentResolver;
import android.provider.Settings;

/**
 * Settings keys and value conversions for the Dolby screens and DolbyService.
 *
 * Dolby on/off and the active DAX profile stay in the Global keys owned by the vendor daxService
 * (it applies them to the effect and keeps them in sync with its Quick Settings tile). The user's
 * choices for the profile-specific parameters live in Secure keys that DolbyService applies;
 * these are the settings stock ZUI Settings exposes: intelligent equalizer preset and graphic
 * equalizer for Music, dialogue enhancer for Movie.
 */
public final class DolbySettings {
    /** Owned by daxService: 1 = Dolby on. */
    public static final String GLOBAL_DAP_STATE = "dolby_dap_state";
    /** Owned by daxService: active DAX profile. */
    public static final String GLOBAL_DAP_PROFILE = "dolby_dap_profile";

    /** Mode used while no app-specific mode applies. */
    public static final String KEY_PROFILE = "tb375fc_dolby_profile";
    /** Music intelligent equalizer preset: 0 none, 1 detailed, 2 balanced, 3 warm. */
    public static final String KEY_IEQ_PRESET = "tb375fc_dolby_ieq_preset";
    /** Music graphic equalizer, GEQ_BANDS comma-separated gains in dB. */
    public static final String KEY_GEQ_GAINS = "tb375fc_dolby_geq_gains";
    /** Movie dialogue enhancer level: 0 off, 1..DIALOGUE_MAX amount. */
    public static final String KEY_DIALOGUE_LEVEL = "tb375fc_dolby_dialogue_level";

    // DAX profiles in dax-default.xml that stock ZUI offers.
    public static final int PROFILE_DYNAMIC = 0;
    public static final int PROFILE_MOVIE = 1;
    public static final int PROFILE_MUSIC = 2;

    public static final int IEQ_PRESET_MAX = 3;

    /** Stock seek bar range (layout/dolby_dialogue_enhancer: min 0, max 9). */
    public static final int DIALOGUE_MAX = 9;

    /** Stock GeqView: 10 bands, every other DAX band, limited to +-6 dB. */
    public static final int GEQ_BANDS = 10;
    public static final int GEQ_MAX_DB = 6;
    /** Centre frequencies of the bands shown, in Hz (dax-default.xml bands 0, 2, ... 18). */
    public static final int[] GEQ_FREQUENCIES = {
            47, 234, 469, 844, 1313, 2250, 3750, 5813, 9000, 13875};

    /** DAX graphic equalizer: 20 bands, gains in 1/16 dB. */
    static final int DAP_GEQ_BANDS = 20;
    private static final int DAP_GEQ_UNITS_PER_DB = 16;

    private DolbySettings() {
    }

    public static boolean isDolbyOn(ContentResolver cr) {
        return Settings.Global.getInt(cr, GLOBAL_DAP_STATE, 1) != 0;
    }

    public static int getProfile(ContentResolver cr) {
        return sanitizeProfile(Settings.Secure.getInt(cr, KEY_PROFILE, PROFILE_DYNAMIC));
    }

    public static int getIeqPreset(ContentResolver cr) {
        return clamp(Settings.Secure.getInt(cr, KEY_IEQ_PRESET, 0), 0, IEQ_PRESET_MAX);
    }

    public static int getDialogueLevel(ContentResolver cr) {
        return clamp(Settings.Secure.getInt(cr, KEY_DIALOGUE_LEVEL, 0), 0, DIALOGUE_MAX);
    }

    public static int[] getGeqGains(ContentResolver cr) {
        return parseGeqGains(Settings.Secure.getString(cr, KEY_GEQ_GAINS));
    }

    public static void putGeqGains(ContentResolver cr, int[] gains) {
        Settings.Secure.putString(cr, KEY_GEQ_GAINS, formatGeqGains(gains));
    }

    static int sanitizeProfile(int profile) {
        return profile == PROFILE_MOVIE || profile == PROFILE_MUSIC ? profile : PROFILE_DYNAMIC;
    }

    /** Missing or malformed values read as a flat equalizer. */
    static int[] parseGeqGains(String value) {
        final int[] gains = new int[GEQ_BANDS];
        if (value == null) {
            return gains;
        }
        final String[] parts = value.split(",");
        if (parts.length != GEQ_BANDS) {
            return gains;
        }
        try {
            for (int i = 0; i < GEQ_BANDS; i++) {
                gains[i] = clamp(Integer.parseInt(parts[i].trim()), -GEQ_MAX_DB, GEQ_MAX_DB);
            }
        } catch (NumberFormatException e) {
            return new int[GEQ_BANDS];
        }
        return gains;
    }

    static String formatGeqGains(int[] gains) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < GEQ_BANDS; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(clamp(gains[i], -GEQ_MAX_DB, GEQ_MAX_DB));
        }
        return sb.toString();
    }

    /**
     * Expands the shown bands to the 20 DAX bands the way stock GeqView does: even bands take
     * the gain of their band, odd bands the mean of their neighbours, the last one repeats.
     */
    static int[] toDapGeqGains(int[] gains) {
        final int[] dap = new int[DAP_GEQ_BANDS];
        for (int i = 0; i < GEQ_BANDS; i++) {
            dap[2 * i] = gains[i] * DAP_GEQ_UNITS_PER_DB;
            dap[2 * i + 1] = i + 1 < GEQ_BANDS
                    ? (int) ((gains[i] + gains[i + 1]) / 2f * DAP_GEQ_UNITS_PER_DB)
                    : gains[i] * DAP_GEQ_UNITS_PER_DB;
        }
        return dap;
    }

    /** Inverse of toDapGeqGains for the shown bands, rounded to whole dB. */
    static int[] fromDapGeqGains(int[] dap) {
        final int[] gains = new int[GEQ_BANDS];
        for (int i = 0; i < GEQ_BANDS; i++) {
            gains[i] = clamp(Math.round(dap[2 * i] / (float) DAP_GEQ_UNITS_PER_DB),
                    -GEQ_MAX_DB, GEQ_MAX_DB);
        }
        return gains;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
