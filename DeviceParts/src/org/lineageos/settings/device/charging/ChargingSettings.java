/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.charging;

import android.content.ContentResolver;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.Set;
import java.util.TreeSet;

/** Settings keys of bypass charging and the charging heat protection. */
public final class ChargingSettings {
    /** Secure setting, comma-separated package names of the apps that use bypass charging. */
    public static final String KEY_BYPASS_APPS = "tb375fc_bypass_apps";
    /** Secure setting, 1 = bypass charging for the game of a DerpFest GameSpace session. */
    public static final String KEY_BYPASS_GAMESPACE = "tb375fc_bypass_gamespace";
    /** DerpFest's GameSpace; LineageOS has no such app. */
    public static final String GAMESPACE_PACKAGE = "io.chaldeaprjkt.gamespace";

    /** Secure setting, 1 = charging heat protection on. Default off, as on stock. */
    public static final String KEY_HEAT_PROTECTION = "tb375fc_heat_protection";
    /** Secure setting, battery level in percent from which the heat protection applies. */
    public static final String KEY_HEAT_MIN_LEVEL = "tb375fc_heat_protection_min_level";
    /** Secure setting, index into HEAT_THRESHOLDS_C. */
    public static final String KEY_HEAT_THRESHOLD = "tb375fc_heat_protection_threshold";

    /** Bypass charging for an app stops at this battery level (percent). */
    public static final int BYPASS_MIN_LEVEL = 20;

    /** Stock's choices for the minimum level (seek bar) and its default. */
    public static final int HEAT_MIN_LEVEL_LOWEST = 20;
    public static final int HEAT_MIN_LEVEL_HIGHEST = 80;
    public static final int HEAT_MIN_LEVEL_DEFAULT = 40;
    /** Stock's low, medium and high thresholds in degrees Celsius; medium by default. */
    public static final int[] HEAT_THRESHOLDS_C = {39, 41, 43};
    public static final int HEAT_THRESHOLD_DEFAULT = 1;

    private ChargingSettings() {
    }

    public static Set<String> getBypassApps(ContentResolver cr) {
        final Set<String> apps = new TreeSet<>();
        final String value = Settings.Secure.getString(cr, KEY_BYPASS_APPS);
        if (!TextUtils.isEmpty(value)) {
            for (String pkg : value.split(",")) {
                if (!pkg.isEmpty()) {
                    apps.add(pkg);
                }
            }
        }
        return apps;
    }

    public static boolean isBypassApp(ContentResolver cr, String pkg) {
        return getBypassApps(cr).contains(pkg);
    }

    public static void setBypassApp(ContentResolver cr, String pkg, boolean bypass) {
        final Set<String> apps = getBypassApps(cr);
        if (bypass ? apps.add(pkg) : apps.remove(pkg)) {
            Settings.Secure.putString(cr, KEY_BYPASS_APPS, String.join(",", apps));
        }
    }

    public static boolean isGameSpaceBypassEnabled(ContentResolver cr) {
        return Settings.Secure.getInt(cr, KEY_BYPASS_GAMESPACE, 0) != 0;
    }

    public static void setGameSpaceBypassEnabled(ContentResolver cr, boolean enabled) {
        Settings.Secure.putInt(cr, KEY_BYPASS_GAMESPACE, enabled ? 1 : 0);
    }

    public static boolean isHeatProtectionEnabled(ContentResolver cr) {
        return Settings.Secure.getInt(cr, KEY_HEAT_PROTECTION, 0) != 0;
    }

    public static void setHeatProtectionEnabled(ContentResolver cr, boolean enabled) {
        Settings.Secure.putInt(cr, KEY_HEAT_PROTECTION, enabled ? 1 : 0);
    }

    public static int getHeatMinLevel(ContentResolver cr) {
        final int level = Settings.Secure.getInt(cr, KEY_HEAT_MIN_LEVEL, HEAT_MIN_LEVEL_DEFAULT);
        return Math.max(HEAT_MIN_LEVEL_LOWEST, Math.min(HEAT_MIN_LEVEL_HIGHEST, level));
    }

    public static void setHeatMinLevel(ContentResolver cr, int level) {
        Settings.Secure.putInt(cr, KEY_HEAT_MIN_LEVEL, level);
    }

    public static int getHeatThresholdIndex(ContentResolver cr) {
        final int index = Settings.Secure.getInt(cr, KEY_HEAT_THRESHOLD, HEAT_THRESHOLD_DEFAULT);
        return index >= 0 && index < HEAT_THRESHOLDS_C.length ? index : HEAT_THRESHOLD_DEFAULT;
    }

    public static void setHeatThresholdIndex(ContentResolver cr, int index) {
        Settings.Secure.putInt(cr, KEY_HEAT_THRESHOLD, index);
    }
}
