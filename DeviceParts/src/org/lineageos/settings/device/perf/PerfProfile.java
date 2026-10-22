/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

/** The performance profile chosen for one app. */
public final class PerfProfile {
    /** The vendor rules for the app, if it has any: what stock does. */
    public static final int MODE_VENDOR = 0;
    /** A vendor rule set chosen by the user, optionally with advanced parameters. */
    public static final int MODE_CUSTOM = 1;
    /** No rules, even if the vendor list has some for the app. */
    public static final int MODE_OFF = 2;

    private static final String JSON_MODE = "mode";
    private static final String JSON_PRESET = "preset";
    private static final String JSON_ADVANCED = "advanced";
    private static final String JSON_CPU_MIN = "cpu_min";
    private static final String JSON_CPU_MAX = "cpu_max";
    private static final String JSON_REFRESH_RATE = "refresh_rate";

    public int mode = MODE_VENDOR;
    /** VendorAppList.Preset id, or "" for none. */
    public String preset = "";
    public boolean advanced;
    /** Per CPU cluster, in kHz; used while advanced is on. */
    public int[] cpuMin = new int[0];
    public int[] cpuMax = new int[0];
    /** Refresh-rate limit in Hz while the app is in front, 0 for none; used while advanced. */
    public int refreshRate;

    public PerfProfile copy() {
        final PerfProfile copy = new PerfProfile();
        copy.mode = mode;
        copy.preset = preset;
        copy.advanced = advanced;
        copy.cpuMin = cpuMin.clone();
        copy.cpuMax = cpuMax.clone();
        copy.refreshRate = refreshRate;
        return copy;
    }

    public boolean isDefault() {
        return mode == MODE_VENDOR;
    }

    JSONObject toJson() throws JSONException {
        final JSONObject json = new JSONObject();
        json.put(JSON_MODE, mode);
        if (mode != MODE_CUSTOM) {
            return json;
        }
        json.put(JSON_PRESET, preset);
        json.put(JSON_ADVANCED, advanced);
        json.put(JSON_CPU_MIN, toJsonArray(cpuMin));
        json.put(JSON_CPU_MAX, toJsonArray(cpuMax));
        json.put(JSON_REFRESH_RATE, refreshRate);
        return json;
    }

    /** Unknown or malformed fields fall back to their defaults. */
    static PerfProfile fromJson(JSONObject json) {
        final PerfProfile profile = new PerfProfile();
        final int mode = json.optInt(JSON_MODE, MODE_VENDOR);
        profile.mode = mode == MODE_CUSTOM || mode == MODE_OFF ? mode : MODE_VENDOR;
        profile.preset = json.optString(JSON_PRESET, "");
        profile.advanced = json.optBoolean(JSON_ADVANCED, false);
        profile.cpuMin = fromJsonArray(json.optJSONArray(JSON_CPU_MIN));
        profile.cpuMax = fromJsonArray(json.optJSONArray(JSON_CPU_MAX));
        profile.refreshRate = Math.max(0, json.optInt(JSON_REFRESH_RATE, 0));
        return profile;
    }

    private static JSONArray toJsonArray(int[] values) {
        final JSONArray array = new JSONArray();
        for (int value : values) {
            array.put(value);
        }
        return array;
    }

    private static int[] fromJsonArray(JSONArray array) {
        if (array == null) {
            return new int[0];
        }
        final int[] values = new int[array.length()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.optInt(i, 0);
        }
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PerfProfile other)) {
            return false;
        }
        return mode == other.mode && preset.equals(other.preset) && advanced == other.advanced
                && Arrays.equals(cpuMin, other.cpuMin) && Arrays.equals(cpuMax, other.cpuMax)
                && refreshRate == other.refreshRate;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] {mode, preset, advanced, Arrays.hashCode(cpuMin),
                Arrays.hashCode(cpuMax), refreshRate});
    }
}
