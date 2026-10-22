/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.content.ContentResolver;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Settings keys of the performance profiles. Only apps whose profile differs from the default
 * (follow the vendor rules) are stored, as a JSON object keyed by package name.
 */
public final class PerfSettings {
    private static final String TAG = "PerfSettings";

    /** Secure setting, 1 = send the foreground app to the power HAL and apply profiles. */
    public static final String KEY_ENABLED = "tb375fc_perf_enabled";
    /** Secure setting, JSON object: package name -> PerfProfile. */
    public static final String KEY_APP_PROFILES = "tb375fc_perf_app_profiles";

    private PerfSettings() {
    }

    public static boolean isEnabled(ContentResolver cr) {
        return Settings.Secure.getInt(cr, KEY_ENABLED, 1) != 0;
    }

    public static void setEnabled(ContentResolver cr, boolean enabled) {
        Settings.Secure.putInt(cr, KEY_ENABLED, enabled ? 1 : 0);
    }

    public static Map<String, PerfProfile> getAppProfiles(ContentResolver cr) {
        return parse(Settings.Secure.getString(cr, KEY_APP_PROFILES));
    }

    /** The app's profile; the default one if none is stored. */
    public static PerfProfile getAppProfile(ContentResolver cr, String pkg) {
        final PerfProfile profile = getAppProfiles(cr).get(pkg);
        return profile != null ? profile : new PerfProfile();
    }

    /** Stores the app's profile, or forgets it if it is the default one. */
    public static void putAppProfile(ContentResolver cr, String pkg, PerfProfile profile) {
        final Map<String, PerfProfile> profiles = getAppProfiles(cr);
        if (profile.isDefault()) {
            profiles.remove(pkg);
        } else {
            profiles.put(pkg, profile);
        }
        Settings.Secure.putString(cr, KEY_APP_PROFILES, format(profiles));
    }

    static Map<String, PerfProfile> parse(String value) {
        final Map<String, PerfProfile> profiles = new TreeMap<>();
        if (value == null || value.isEmpty()) {
            return profiles;
        }
        try {
            final JSONObject json = new JSONObject(value);
            for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                final String pkg = it.next();
                final JSONObject entry = json.optJSONObject(pkg);
                if (entry != null) {
                    profiles.put(pkg, PerfProfile.fromJson(entry));
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Ignoring malformed " + KEY_APP_PROFILES, e);
        }
        return profiles;
    }

    static String format(Map<String, PerfProfile> profiles) {
        final JSONObject json = new JSONObject();
        try {
            for (Map.Entry<String, PerfProfile> entry : new TreeMap<>(profiles).entrySet()) {
                json.put(entry.getKey(), entry.getValue().toJson());
            }
        } catch (JSONException e) {
            // Only thrown for non-finite numbers, which profiles never hold.
            throw new IllegalStateException(e);
        }
        return json.toString();
    }
}
