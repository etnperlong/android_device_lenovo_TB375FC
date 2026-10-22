/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.content.Context;
import android.content.pm.PackageManager;

import org.lineageos.settings.device.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Texts of the performance profile screens. */
final class PerfLabels {
    private final Context mContext;
    private final VendorAppList mVendor;
    private final Map<String, String> mReferenceNames = new HashMap<>();

    PerfLabels(Context context, VendorAppList vendor) {
        mContext = context;
        mVendor = vendor;
        for (String entry : context.getResources().getStringArray(
                R.array.perf_preset_reference_names)) {
            final int sep = entry.indexOf('|');
            if (sep > 0) {
                mReferenceNames.put(entry.substring(0, sep), entry.substring(sep + 1));
            }
        }
    }

    /**
     * Kind of app a preset is for, from the thermal policy stock assigns to it: video 10,
     * camera 11, communication 13, music 15, app clone 16, benchmark 20; games use the others.
     */
    private static int categoryOf(VendorAppList.Preset preset) {
        final Integer thermal = preset.valueOf(VendorAppList.THERMAL_POLICY);
        switch (thermal == null ? -1 : thermal) {
            case 10:
                return R.string.perf_category_video;
            case 11:
                return R.string.perf_category_camera;
            case 13:
                return R.string.perf_category_communication;
            case 15:
                return R.string.perf_category_music;
            case 16:
                return R.string.perf_category_app_clone;
            case 20:
                return R.string.perf_category_benchmark;
            default:
                return R.string.perf_category_game;
        }
    }

    String presetCategory(VendorAppList.Preset preset) {
        return mContext.getString(categoryOf(preset));
    }

    /**
     * The category, and for games, of which there are many rule sets, "Game · first app of the
     * rule set".
     */
    String presetLabel(VendorAppList.Preset preset) {
        final int category = categoryOf(preset);
        if (category != R.string.perf_category_game) {
            return mContext.getString(category);
        }
        return mContext.getString(R.string.perf_preset_label, mContext.getString(category),
                appName(preset.id));
    }

    /** The installed app's label, a known name, or the package name. */
    private String appName(String pkg) {
        final PackageManager pm = mContext.getPackageManager();
        try {
            return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            final String name = mReferenceNames.get(pkg);
            return name != null ? name : pkg;
        }
    }

    String presetOrNoneLabel(String presetId) {
        final VendorAppList.Preset preset = mVendor.getPreset(presetId);
        return preset != null ? presetLabel(preset) : mContext.getString(R.string.perf_preset_none);
    }

    /** The main settings of a preset: thermal policy, CPU limits, FPSGO, number of settings. */
    String presetDetails(VendorAppList.Preset preset) {
        final List<String> parts = new ArrayList<>();
        final Integer thermal = preset.valueOf(VendorAppList.THERMAL_POLICY);
        if (thermal != null) {
            parts.add(mContext.getString(R.string.perf_preset_thermal, thermal));
        }
        final String max = clusterValues(preset, true);
        if (max != null) {
            parts.add(mContext.getString(R.string.perf_preset_cpu_max, max));
        }
        final String min = clusterValues(preset, false);
        if (min != null) {
            parts.add(mContext.getString(R.string.perf_preset_cpu_min, min));
        }
        for (VendorAppList.Rule rule : preset.rules) {
            if (rule.command.startsWith("PERF_RES_FPS_")) {
                parts.add(mContext.getString(R.string.perf_preset_fpsgo));
                break;
            }
        }
        parts.add(mContext.getResources().getQuantityString(R.plurals.perf_preset_count,
                preset.rules.size(), preset.rules.size()));
        return String.join(mContext.getString(R.string.perf_list_separator), parts);
    }

    /** "1.6 / 2.9 / 3.0 GHz"-like list of a preset's per-cluster limits, or null if none. */
    private String clusterValues(VendorAppList.Preset preset, boolean max) {
        final List<CpuClusters.Cluster> clusters = CpuClusters.get();
        final List<String> values = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < clusters.size(); i++) {
            final Integer value = preset.valueOf(max ? VendorAppList.cpuFreqMaxCommand(i)
                    : VendorAppList.cpuFreqMinCommand(i));
            any |= value != null;
            values.add(value != null ? formatFrequency(value) : "-");
        }
        return any ? String.join(" / ", values) : null;
    }

    /** Summary of an app's profile on the app list. */
    String profileSummary(String pkg, PerfProfile profile) {
        switch (profile.mode) {
            case PerfProfile.MODE_CUSTOM:
                final String preset = presetOrNoneLabel(profile.preset);
                return mContext.getString(R.string.perf_mode_custom_summary, profile.advanced
                        ? mContext.getString(R.string.perf_mode_custom_advanced, preset)
                        : preset);
            case PerfProfile.MODE_OFF:
                return mContext.getString(R.string.perf_mode_off);
            default:
                final VendorAppList.Preset vendor = mVendor.getAppPreset(pkg);
                if (vendor != null) {
                    return mContext.getString(R.string.perf_mode_vendor_summary,
                            presetCategory(vendor));
                }
                return mContext.getString(mVendor.isListed(pkg)
                        ? R.string.perf_mode_vendor : R.string.perf_mode_default);
        }
    }

    /** "Little cores (CPU 0-3)" for 3 clusters; little/big for 2. */
    String clusterName(int index, CpuClusters.Cluster cluster, int count) {
        final int name;
        if (count == 1) {
            name = R.string.perf_cluster_all;
        } else if (index == 0) {
            name = R.string.perf_cluster_little;
        } else if (index == count - 1) {
            name = R.string.perf_cluster_big;
        } else {
            name = R.string.perf_cluster_medium;
        }
        final String cpus = cluster.firstCpu == cluster.lastCpu
                ? String.valueOf(cluster.firstCpu)
                : mContext.getString(R.string.perf_cpu_range, cluster.firstCpu, cluster.lastCpu);
        return mContext.getString(R.string.perf_cluster_title, mContext.getString(name), cpus);
    }

    String formatFrequency(int khz) {
        return khz >= 1_000_000
                ? mContext.getString(R.string.perf_freq_ghz, khz / 1_000_000f)
                : mContext.getString(R.string.perf_freq_mhz, khz / 1000);
    }
}
