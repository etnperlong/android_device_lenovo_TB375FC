/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * CPU frequency domains (cpufreq policies) in CPU order. Index i is the "cluster i" of
 * libpowerhal's PERF_RES_CPUFREQ_*_CLUSTER_i resources: its LAUNCH scenario logs
 * "cpufreq[0] min:2200000 cpufreq[1] min:3200000 cpufreq[2] min:3350000", the maximum
 * frequencies of policy0 (CPU 0-3), policy4 (CPU 4-6) and policy7 (CPU 7).
 */
public final class CpuClusters {
    private static final String TAG = "CpuClusters";
    private static final String CPUFREQ = "/sys/devices/system/cpu/cpufreq";

    /** One frequency domain. */
    public static final class Cluster {
        public final int firstCpu;
        public final int lastCpu;
        /** Ascending, in kHz. */
        public final int[] frequencies;

        Cluster(int firstCpu, int lastCpu, int[] frequencies) {
            this.firstCpu = firstCpu;
            this.lastCpu = lastCpu;
            this.frequencies = frequencies;
        }

        public int lowest() {
            return frequencies[0];
        }

        public int highest() {
            return frequencies[frequencies.length - 1];
        }

        /** Index of the table entry closest to the given frequency. */
        public int indexOf(int khz) {
            int best = 0;
            for (int i = 1; i < frequencies.length; i++) {
                if (Math.abs(frequencies[i] - khz) < Math.abs(frequencies[best] - khz)) {
                    best = i;
                }
            }
            return best;
        }
    }

    private static List<Cluster> sClusters;

    private CpuClusters() {
    }

    /** The domains, read once per process; empty if cpufreq cannot be read. */
    public static synchronized List<Cluster> get() {
        if (sClusters == null) {
            sClusters = Collections.unmodifiableList(read());
        }
        return sClusters;
    }

    private static List<Cluster> read() {
        final List<Cluster> clusters = new ArrayList<>();
        final File[] policies = new File(CPUFREQ).listFiles(
                (dir, name) -> name.matches("policy\\d+"));
        if (policies == null) {
            Log.e(TAG, "Cannot list " + CPUFREQ);
            return clusters;
        }
        Arrays.sort(policies, (a, b) -> Integer.compare(policyNumber(a), policyNumber(b)));
        for (File policy : policies) {
            try {
                final int[] cpus = parseInts(readLine(new File(policy, "related_cpus")));
                final int[] freqs = parseInts(
                        readLine(new File(policy, "scaling_available_frequencies")));
                if (cpus.length == 0 || freqs.length == 0) {
                    continue;
                }
                clusters.add(new Cluster(cpus[0], cpus[cpus.length - 1],
                        Arrays.stream(freqs).distinct().toArray()));
            } catch (IOException | NumberFormatException e) {
                Log.e(TAG, "Cannot read " + policy, e);
            }
        }
        return clusters;
    }

    private static int policyNumber(File policy) {
        return Integer.parseInt(policy.getName().substring("policy".length()));
    }

    private static String readLine(File file) throws IOException {
        return Files.readString(file.toPath()).trim();
    }

    /** Space-separated integers, sorted ascending. */
    private static int[] parseInts(String line) {
        if (line.isEmpty()) {
            return new int[0];
        }
        final int[] values = Arrays.stream(line.split("\\s+")).mapToInt(Integer::parseInt)
                .toArray();
        Arrays.sort(values);
        return values;
    }
}
