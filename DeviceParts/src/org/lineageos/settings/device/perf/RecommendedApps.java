/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.content.Context;
import android.util.Log;

import org.lineageos.settings.device.R;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Apps the vendor list does not know that get one of its rule sets by default: international
 * versions of the listed games, and international apps of the kinds the list covers (video,
 * communication, music, camera). Each entry names a listed package whose rules the app takes,
 * so the presets stay exactly the vendor's. Listed packages keep their vendor rules.
 */
public final class RecommendedApps {
    private static final String TAG = "RecommendedApps";

    private static RecommendedApps sInstance;

    /** Package -> vendor preset. */
    private final Map<String, VendorAppList.Preset> mPresets;

    private RecommendedApps(Map<String, VendorAppList.Preset> presets) {
        mPresets = Collections.unmodifiableMap(presets);
    }

    public static synchronized RecommendedApps get(Context context) {
        if (sInstance == null) {
            sInstance = new RecommendedApps(parse(VendorAppList.get(),
                    context.getResources().getStringArray(R.array.perf_recommended_apps)));
        }
        return sInstance;
    }

    /** Entries are "package|listed package"; bad ones and listed packages are skipped. */
    static Map<String, VendorAppList.Preset> parse(VendorAppList vendor, String[] entries) {
        final Map<String, VendorAppList.Preset> presets = new HashMap<>();
        for (String entry : entries) {
            final int sep = entry.indexOf('|');
            if (sep <= 0) {
                Log.w(TAG, "Malformed entry " + entry);
                continue;
            }
            final String pkg = entry.substring(0, sep).trim();
            final String reference = entry.substring(sep + 1).trim();
            final VendorAppList.Preset preset = vendor.getAppPreset(reference);
            if (preset == null) {
                Log.w(TAG, reference + " has no vendor rules; skipping " + pkg);
            } else if (!vendor.isListed(pkg)) {
                presets.put(pkg, preset);
            }
        }
        return presets;
    }

    /** The app's recommended preset, or null. */
    public VendorAppList.Preset getPreset(String pkg) {
        return mPresets.get(pkg);
    }
}
