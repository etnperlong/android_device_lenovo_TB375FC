/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.memory;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemProperties;
import android.text.format.Formatter;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settingslib.widget.SelectorWithWidgetPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * RAM expansion settings after stock ZUI's memory expansion page. mmd configures zram and its
 * backing file at boot; this page stores the next-boot size and compression algorithm and reports
 * the live zram state without changing it.
 */
public class RamExpansionFragment extends SettingsBasePreferenceFragment {
    private static final String PROP_WRITEBACK_SIZE = "persist.sys.tb375fc.zram.writeback_size";
    private static final String PROP_COMP_ALGORITHM = "persist.sys.tb375fc.zram.comp_algorithm";
    private static final String PROP_APPLIED_WRITEBACK_SIZE = "sys.tb375fc.zram.writeback_size";
    private static final String PROP_APPLIED_COMP_ALGORITHM = "sys.tb375fc.zram.comp_algorithm";

    private static final String PATH_BACKING_DEV = "/sys/block/zram0/backing_dev";
    private static final String PATH_BD_STAT = "/sys/block/zram0/bd_stat";
    private static final String PATH_MM_STAT = "/sys/block/zram0/mm_stat";
    private static final String PATH_COMP_ALGORITHM = "/sys/block/zram0/comp_algorithm";

    private static final long GIB = 1024L * 1024 * 1024;
    private static final long STORAGE_RESERVE = 1536L * 1024 * 1024;
    private static final long BD_PAGE_SIZE = 4096L;
    private static final long[] WRITEBACK_SIZES = {
            0L, 2L * GIB, 4L * GIB, 6L * GIB, 8L * GIB
    };
    private static final String[] SIZE_KEYS = {
            "ram_expansion_size_0", "ram_expansion_size_2", "ram_expansion_size_4",
            "ram_expansion_size_6", "ram_expansion_size_8"
    };
    private static final String[] ALGORITHM_FALLBACK = {"lz4", "zstd", "lzo-rle", "lzo"};

    private final SelectorWithWidgetPreference[] mSizeOptions =
            new SelectorWithWidgetPreference[WRITEBACK_SIZES.length];
    private Preference mCompressedStatus;
    private Preference mStorageStatus;
    private Preference mPendingPreference;
    private ListPreference mAlgorithm;
    private String[] mAvailableAlgorithms = new String[0];

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.ram_expansion, rootKey);

        for (int i = 0; i < SIZE_KEYS.length; i++) {
            mSizeOptions[i] = findPreference(SIZE_KEYS[i]);
            mSizeOptions[i].setOnClickListener(this::onSizeOptionClicked);
        }
        mCompressedStatus = findPreference("ram_expansion_compressed_status");
        mStorageStatus = findPreference("ram_expansion_storage_status");
        mPendingPreference = findPreference("ram_expansion_pending");
        mPendingPreference.setOnPreferenceClickListener(preference -> {
            showRestartDialog();
            return true;
        });
        mAlgorithm = findPreference("ram_expansion_algorithm");
        mAlgorithm.setOnPreferenceChangeListener((preference, newValue) -> {
            String algorithm = newValue.toString();
            String current = readProperty(PROP_COMP_ALGORITHM, "lz4");
            if (!algorithm.equals(current) && isAlgorithmAvailable(algorithm)
                    && writeProperty(PROP_COMP_ALGORITHM, algorithm)) {
                mAlgorithm.setValue(algorithm);
                updateAlgorithmSummary(algorithm);
                updatePendingPreference();
                requireView().post(this::showRestartDialog);
            }
            return false;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAlgorithmOptions();
        updateStorageOptions();
        updateLiveStatus();
        updatePendingPreference();
    }

    private void onSizeOptionClicked(SelectorWithWidgetPreference selected) {
        for (int i = 0; i < mSizeOptions.length; i++) {
            if (mSizeOptions[i] == selected) {
                String size = Long.toString(WRITEBACK_SIZES[i]);
                if (!size.equals(readProperty(PROP_WRITEBACK_SIZE, "0"))
                        && writeProperty(PROP_WRITEBACK_SIZE, size)) {
                    updateStorageOptions();
                    updatePendingPreference();
                    showRestartDialog();
                }
                return;
            }
        }
    }

    private void updateStorageOptions() {
        long availableBytes = getAvailableStorageBytes();
        long requestedSize = parseNonNegativeLong(readProperty(PROP_WRITEBACK_SIZE, "0"));
        for (int i = 0; i < mSizeOptions.length; i++) {
            SelectorWithWidgetPreference option = mSizeOptions[i];
            boolean enabled = i == 0 || (availableBytes >= 0
                    && WRITEBACK_SIZES[i] <= availableBytes
                    && STORAGE_RESERVE <= availableBytes - WRITEBACK_SIZES[i]);
            option.setEnabled(enabled);
            option.setSummary(enabled ? null : availableBytes < 0
                    ? getString(R.string.ram_expansion_unavailable)
                    : getString(R.string.ram_expansion_not_enough_storage));
            option.setChecked(WRITEBACK_SIZES[i] == requestedSize);
        }
    }

    /**
     * Free space on /data that a writeback file could use: the current file (applied size,
     * while zram has a backing device) counts as free, as it is recreated at boot.
     */
    private long getAvailableStorageBytes() {
        long available;
        try {
            available = new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes();
        } catch (IllegalArgumentException e) {
            return -1;
        }
        String backingDevice = readFile(PATH_BACKING_DEV);
        if (backingDevice != null && !"none".equals(backingDevice.trim())) {
            long appliedSize = parseNonNegativeLong(
                    readProperty(PROP_APPLIED_WRITEBACK_SIZE, "0"));
            if (appliedSize > 0) {
                available = saturatedAdd(available, appliedSize);
            }
        }
        return available;
    }

    private void updateLiveStatus() {
        String mmStat = readFile(PATH_MM_STAT);
        long[] memoryStats = parseLongs(mmStat, 3);
        if (memoryStats == null) {
            mCompressedStatus.setSummary(R.string.ram_expansion_unavailable);
        } else {
            Context context = requireContext();
            mCompressedStatus.setSummary(getString(R.string.ram_expansion_compressed_summary,
                    Formatter.formatShortFileSize(context, memoryStats[0]),
                    Formatter.formatShortFileSize(context, memoryStats[2])));
        }

        String backingDevice = readFile(PATH_BACKING_DEV);
        if (backingDevice == null || backingDevice.trim().isEmpty()) {
            mStorageStatus.setSummary(R.string.ram_expansion_unavailable);
        } else if ("none".equals(backingDevice.trim())) {
            mStorageStatus.setSummary(R.string.ram_expansion_size_off);
        } else {
            long[] backingStats = parseLongs(readFile(PATH_BD_STAT), 3);
            if (backingStats == null || backingStats[0] > Long.MAX_VALUE / BD_PAGE_SIZE) {
                mStorageStatus.setSummary(R.string.ram_expansion_unavailable);
            } else {
                mStorageStatus.setSummary(getString(R.string.ram_expansion_storage_summary,
                        Formatter.formatShortFileSize(requireContext(),
                                backingStats[0] * BD_PAGE_SIZE)));
            }
        }
    }

    private void updateAlgorithmOptions() {
        String supported = readFile(PATH_COMP_ALGORITHM);
        ArrayList<String> algorithms = supported == null
                ? new ArrayList<>() : parseAlgorithms(supported);
        if (supported == null) {
            for (String algorithm : ALGORITHM_FALLBACK) {
                algorithms.add(algorithm);
            }
        }

        CharSequence[] allEntries = getResources().getTextArray(R.array.ram_expansion_algorithm_entries);
        CharSequence[] allValues = getResources().getTextArray(R.array.ram_expansion_algorithm_values);
        ArrayList<CharSequence> entries = new ArrayList<>();
        ArrayList<CharSequence> values = new ArrayList<>();
        for (String algorithm : algorithms) {
            for (int i = 0; i < allValues.length; i++) {
                if (algorithm.equals(allValues[i].toString())) {
                    entries.add(allEntries[i]);
                    values.add(allValues[i]);
                    break;
                }
            }
        }

        mAvailableAlgorithms = algorithms.toArray(new String[0]);
        mAlgorithm.setEntries(entries.toArray(new CharSequence[0]));
        mAlgorithm.setEntryValues(values.toArray(new CharSequence[0]));
        mAlgorithm.setVisible(!values.isEmpty());
        String current = readProperty(PROP_COMP_ALGORITHM, "lz4");
        mAlgorithm.setValue(current);
        updateAlgorithmSummary(current);
    }

    private ArrayList<String> parseAlgorithms(String value) {
        ArrayList<String> algorithms = new ArrayList<>();
        for (String token : value.trim().split("\\s+")) {
            String algorithm = token.replace("[", "").replace("]", "");
            if (isKnownAlgorithm(algorithm) && !algorithms.contains(algorithm)) {
                algorithms.add(algorithm);
            }
        }
        return algorithms;
    }

    private boolean isAlgorithmAvailable(String algorithm) {
        for (String available : mAvailableAlgorithms) {
            if (available.equals(algorithm)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKnownAlgorithm(String algorithm) {
        for (String available : ALGORITHM_FALLBACK) {
            if (available.equals(algorithm)) {
                return true;
            }
        }
        return false;
    }

    private void updateAlgorithmSummary(String algorithm) {
        int summary = R.string.ram_expansion_unavailable;
        if ("lz4".equals(algorithm)) {
            summary = R.string.ram_expansion_algorithm_lz4;
        } else if ("zstd".equals(algorithm)) {
            summary = R.string.ram_expansion_algorithm_zstd;
        } else if ("lzo-rle".equals(algorithm)) {
            summary = R.string.ram_expansion_algorithm_lzo_rle;
        } else if ("lzo".equals(algorithm)) {
            summary = R.string.ram_expansion_algorithm_lzo;
        }
        mAlgorithm.setSummary(summary);
    }

    private void updatePendingPreference() {
        String requestedSize = readProperty(PROP_WRITEBACK_SIZE, "0");
        String appliedSize = readProperty(PROP_APPLIED_WRITEBACK_SIZE, "0");
        String requestedAlgorithm = readProperty(PROP_COMP_ALGORITHM, "lz4");
        String appliedAlgorithm = readProperty(PROP_APPLIED_COMP_ALGORITHM, "lz4");
        mPendingPreference.setVisible(!requestedSize.equals(appliedSize)
                || !requestedAlgorithm.equals(appliedAlgorithm));
    }

    private void showRestartDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ram_expansion_restart_dialog_title)
                .setMessage(R.string.ram_expansion_restart_dialog_message)
                .setPositiveButton(R.string.ram_expansion_restart_now, (dialog, which) -> {
                    PowerManager powerManager = requireContext().getSystemService(PowerManager.class);
                    if (powerManager != null) {
                        powerManager.reboot(null);
                    }
                })
                .setNegativeButton(R.string.ram_expansion_restart_later, null)
                .show();
    }

    private static long[] parseLongs(String value, int count) {
        if (value == null) {
            return null;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length < count) {
            return null;
        }
        long[] values = new long[count];
        try {
            for (int i = 0; i < count; i++) {
                values[i] = Long.parseLong(parts[i]);
                if (values[i] < 0) {
                    return null;
                }
            }
            return values;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseNonNegativeLong(String value) {
        if (value == null) {
            return -1;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed < 0 ? -1 : parsed;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readProperty(String property, String defaultValue) {
        try {
            String value = SystemProperties.get(property, defaultValue);
            return value == null || value.isEmpty() ? defaultValue : value;
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    private static boolean writeProperty(String property, String value) {
        try {
            SystemProperties.set(property, value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static String readFile(String path) {
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (value.length() > 0) {
                    value.append(' ');
                }
                value.append(line);
            }
            return value.toString();
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
