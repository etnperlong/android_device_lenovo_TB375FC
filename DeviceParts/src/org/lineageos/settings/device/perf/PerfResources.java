/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** Builds the power HAL lock of a custom profile. */
final class PerfResources {
    private static final String TAG = "PerfResources";

    private PerfResources() {
    }

    /**
     * The resource ID / value pairs for IMtkPowerService.perfLockAcquire(), the way
     * lib_power_applist's AppScenarioEnable builds them: the preset's rules, then
     * PERF_RES_FPS_FPSGO_RENDER_PID set to the app's pid so the *_BY_PID resources apply to it.
     * With advanced parameters, the preset's CPU frequency rules are replaced by the chosen
     * limits; a limit at the end of the table is left out. Returns an empty array if there is
     * nothing to lock.
     */
    static int[] build(VendorAppList vendor, PerfProfile profile,
            List<CpuClusters.Cluster> clusters, int pid) {
        final List<Integer> pairs = new ArrayList<>();
        final VendorAppList.Preset preset = vendor.getPreset(profile.preset);
        if (preset != null) {
            for (VendorAppList.Rule rule : preset.rules) {
                if (profile.advanced && VendorAppList.isCpuFreqCommand(rule.command)) {
                    continue;
                }
                add(vendor, pairs, rule.command, rule.value);
            }
        }
        if (profile.advanced) {
            for (int i = 0; i < clusters.size(); i++) {
                final CpuClusters.Cluster cluster = clusters.get(i);
                final int min = valueAt(profile.cpuMin, i, cluster.lowest());
                final int max = valueAt(profile.cpuMax, i, cluster.highest());
                if (min > cluster.lowest()) {
                    add(vendor, pairs, VendorAppList.cpuFreqMinCommand(i), min);
                }
                if (max < cluster.highest()) {
                    add(vendor, pairs, VendorAppList.cpuFreqMaxCommand(i), max);
                }
            }
        }
        if (pairs.isEmpty()) {
            return new int[0];
        }
        add(vendor, pairs, VendorAppList.FPSGO_RENDER_PID, pid);
        return pairs.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The stored value for a cluster, or the fallback if there is none. */
    static int valueAt(int[] values, int index, int fallback) {
        return index < values.length && values[index] > 0 ? values[index] : fallback;
    }

    /**
     * Commands command.csv does not define (a few in power_app_cfg.xml, e.g.
     * PERF_RES_GPU_RECLAIM_POLICY) are skipped. The vendor service sends them with resource ID 0
     * instead (lib_power_applist getIDfromString logs "cannot find" and returns 0), an ID that
     * neither command.csv nor powercontable.xml defines.
     */
    private static void add(VendorAppList vendor, List<Integer> pairs, String command, int value) {
        final Integer id = vendor.getCommandId(command);
        if (id == null) {
            Log.w(TAG, "No resource ID for " + command);
            return;
        }
        pairs.add(id);
        pairs.add(value);
    }
}
