/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.memory;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemProperties;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * RAM expansion settings after stock ZUI's memory expansion page. mmd configures zram, its
 * writeback file and recompression at boot; this page edits the next boot's writeback size,
 * compression algorithm and recompression, saved only by the toolbar's save action (or when
 * leaving with unsaved changes and choosing to save), and reports the live zram state without
 * changing it.
 */
public class RamExpansionFragment extends SettingsBasePreferenceFragment {
    private static final String PROP_WRITEBACK_SIZE = "persist.sys.tb375fc.zram.writeback_size";
    private static final String PROP_COMP_ALGORITHM = "persist.sys.tb375fc.zram.comp_algorithm";
    private static final String PROP_RECOMPRESSION = "persist.sys.tb375fc.zram.recompression";
    private static final String PROP_APPLIED_WRITEBACK_SIZE = "sys.tb375fc.zram.writeback_size";
    private static final String PROP_APPLIED_COMP_ALGORITHM = "sys.tb375fc.zram.comp_algorithm";
    private static final String PROP_APPLIED_RECOMPRESSION = "sys.tb375fc.zram.recompression";

    private static final String PATH_BACKING_DEV = "/sys/block/zram0/backing_dev";
    private static final String PATH_BD_STAT = "/sys/block/zram0/bd_stat";
    private static final String PATH_MM_STAT = "/sys/block/zram0/mm_stat";
    private static final String PATH_COMP_ALGORITHM = "/sys/block/zram0/comp_algorithm";
    private static final String PATH_RECOMP_ALGORITHM = "/sys/block/zram0/recomp_algorithm";

    private static final String DEFAULT_ALGORITHM = "lz4";
    /** The recompression algorithm (device.mk); recompressing zstd data with it gains nothing. */
    private static final String RECOMPRESSION_ALGORITHM = "zstd";

    private static final String STATE_SIZE = "size";
    private static final String STATE_ALGORITHM = "algorithm";
    private static final String STATE_RECOMPRESSION = "recompression";
    private static final String TAG_RESTART_DIALOG = "restart";
    private static final String TAG_UNSAVED_DIALOG = "unsaved";

    private static final long GIB = 1024L * 1024 * 1024;
    private static final long STORAGE_RESERVE = 1536L * 1024 * 1024;
    private static final long BD_PAGE_SIZE = 4096L;
    private static final long[] WRITEBACK_SIZES = {
            0L, 2L * GIB, 4L * GIB, 6L * GIB, 8L * GIB
    };
    private static final String[] ALGORITHM_FALLBACK = {"lz4", "zstd", "lzo-rle", "lzo"};

    private RamExpansionSizePreference mSize;
    private ListPreference mAlgorithm;
    private SwitchPreferenceCompat mRecompression;
    private Preference mCompressedStatus;
    private Preference mStorageStatus;
    private Preference mPendingPreference;
    private CharSequence[] mSizeLabels;
    private String[] mAvailableAlgorithms = new String[0];

    // The choices shown, written to the persist properties only on save.
    private int mSizeIndex;
    private String mAlgorithmValue;
    private boolean mRecompressionValue;

    private final OnBackPressedCallback mUnsavedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            new UnsavedDialogFragment().show(getChildFragmentManager(), TAG_UNSAVED_DIALOG);
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.ram_expansion, rootKey);

        mSizeLabels = getResources().getTextArray(R.array.ram_expansion_size_entries);
        mSize = findPreference("ram_expansion_size");
        mSize.setLabels(mSizeLabels);
        mSize.setOnSizeChangedListener(index -> {
            mSizeIndex = index;
            updateUnsaved();
        });
        mCompressedStatus = findPreference("ram_expansion_compressed_status");
        mStorageStatus = findPreference("ram_expansion_storage_status");
        mPendingPreference = findPreference("ram_expansion_pending");
        mPendingPreference.setOnPreferenceClickListener(preference -> {
            showRestartDialog(false);
            return true;
        });
        mAlgorithm = findPreference("ram_expansion_algorithm");
        mAlgorithm.setOnPreferenceChangeListener((preference, newValue) -> {
            mAlgorithmValue = newValue.toString();
            updateAlgorithmSummary(mAlgorithmValue);
            updateRecompression();
            updateUnsaved();
            return true;
        });
        mRecompression = findPreference("ram_expansion_recompression");
        mRecompression.setOnPreferenceChangeListener((preference, newValue) -> {
            mRecompressionValue = (Boolean) newValue;
            updateUnsaved();
            return true;
        });

        if (savedInstanceState != null) {
            mSizeIndex = savedInstanceState.getInt(STATE_SIZE);
            mAlgorithmValue = savedInstanceState.getString(STATE_ALGORITHM);
            mRecompressionValue = savedInstanceState.getBoolean(STATE_RECOMPRESSION);
        } else {
            mSizeIndex = savedSizeIndex();
            mAlgorithmValue = savedAlgorithm();
            mRecompressionValue = savedRecompression();
        }
        mSize.setIndex(mSizeIndex);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), mUnsavedCallback);
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
                inflater.inflate(R.menu.ram_expansion, menu);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                menu.findItem(R.id.ram_expansion_save).setVisible(hasUnsavedChanges());
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() != R.id.ram_expansion_save) {
                    return false;
                }
                saveAndOfferRestart(false);
                return true;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        mUnsavedCallback.setEnabled(hasUnsavedChanges());
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAlgorithmOptions();
        updateRecompression();
        updateSizeLimit();
        updateLiveStatus();
        updatePendingPreference();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SIZE, mSizeIndex);
        outState.putString(STATE_ALGORITHM, mAlgorithmValue);
        outState.putBoolean(STATE_RECOMPRESSION, mRecompressionValue);
    }

    private boolean hasUnsavedChanges() {
        return mSizeIndex != savedSizeIndex()
                || !mAlgorithmValue.equals(savedAlgorithm())
                || mRecompressionValue != savedRecompression();
    }

    /** Shows the save action and asks before leaving while the choices differ from the saved. */
    private void updateUnsaved() {
        mUnsavedCallback.setEnabled(hasUnsavedChanges());
        requireActivity().invalidateMenu();
    }

    /** Saves the choices for the next boot, then offers a restart. */
    private void saveAndOfferRestart(boolean leaveAfterwards) {
        if (!writeProperty(PROP_WRITEBACK_SIZE, Long.toString(WRITEBACK_SIZES[mSizeIndex]))
                || !writeProperty(PROP_COMP_ALGORITHM, mAlgorithmValue)
                || !writeProperty(PROP_RECOMPRESSION, Boolean.toString(mRecompressionValue))) {
            return;
        }
        updateUnsaved();
        updatePendingPreference();
        showRestartDialog(leaveAfterwards);
    }

    private void showRestartDialog(boolean leaveAfterwards) {
        RestartDialogFragment.newInstance(leaveAfterwards)
                .show(getChildFragmentManager(), TAG_RESTART_DIALOG);
    }

    private int savedSizeIndex() {
        long size = parseNonNegativeLong(readProperty(PROP_WRITEBACK_SIZE, "0"));
        for (int i = 0; i < WRITEBACK_SIZES.length; i++) {
            if (WRITEBACK_SIZES[i] == size) {
                return i;
            }
        }
        return 0;
    }

    private static String savedAlgorithm() {
        return readProperty(PROP_COMP_ALGORITHM, DEFAULT_ALGORITHM);
    }

    private static boolean savedRecompression() {
        return !"false".equals(readProperty(PROP_RECOMPRESSION, "true"));
    }

    /** Stops the slider at the largest size that leaves {@link #STORAGE_RESERVE} free. */
    private void updateSizeLimit() {
        long availableBytes = getAvailableStorageBytes();
        int maxIndex = 0;
        for (int i = 1; i < WRITEBACK_SIZES.length; i++) {
            if (WRITEBACK_SIZES[i] <= availableBytes
                    && STORAGE_RESERVE <= availableBytes - WRITEBACK_SIZES[i]) {
                maxIndex = i;
            }
        }
        mSize.setMaxIndex(maxIndex);
        if (maxIndex == WRITEBACK_SIZES.length - 1) {
            mSize.setSummary(null);
        } else if (maxIndex == 0) {
            mSize.setSummary(R.string.ram_expansion_not_enough_storage);
        } else {
            mSize.setSummary(getString(R.string.ram_expansion_size_limited,
                    mSizeLabels[maxIndex]));
        }
    }

    /**
     * Free space on /data that a writeback file could use: the current file (applied size,
     * while zram has a backing device) counts as free, as it is recreated at boot.
     */
    private long getAvailableStorageBytes() {
        long available =
                new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes();
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

        CharSequence[] allEntries =
                getResources().getTextArray(R.array.ram_expansion_algorithm_entries);
        CharSequence[] allValues =
                getResources().getTextArray(R.array.ram_expansion_algorithm_values);
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
        mAlgorithm.setValue(mAlgorithmValue);
        updateAlgorithmSummary(mAlgorithmValue);
    }

    /**
     * Recompression is offered while the kernel supports it and zstd, the recompression
     * algorithm, is not already the compression algorithm; the choice is kept either way.
     */
    private void updateRecompression() {
        boolean supported = readFile(PATH_RECOMP_ALGORITHM) != null
                && isAlgorithmAvailable(RECOMPRESSION_ALGORITHM);
        boolean redundant = RECOMPRESSION_ALGORITHM.equals(mAlgorithmValue);
        mRecompression.setEnabled(supported && !redundant);
        mRecompression.setChecked(supported && !redundant && mRecompressionValue);
        if (!supported) {
            mRecompression.setSummary(R.string.ram_expansion_unavailable);
        } else if (redundant) {
            mRecompression.setSummary(R.string.ram_expansion_recompression_zstd_summary);
        } else {
            mRecompression.setSummary(R.string.ram_expansion_recompression_summary);
        }
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

    /** "Restart to apply changes" while what is saved differs from what this boot applied. */
    private void updatePendingPreference() {
        mPendingPreference.setVisible(
                !readProperty(PROP_WRITEBACK_SIZE, "0").equals(
                        readProperty(PROP_APPLIED_WRITEBACK_SIZE, "0"))
                || !savedAlgorithm().equals(
                        readProperty(PROP_APPLIED_COMP_ALGORITHM, DEFAULT_ALGORITHM))
                || !readProperty(PROP_RECOMPRESSION, "true").equals(
                        readProperty(PROP_APPLIED_RECOMPRESSION, "true")));
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
        try {
            long parsed = Long.parseLong(value);
            return parsed < 0 ? -1 : parsed;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readProperty(String property, String defaultValue) {
        String value = SystemProperties.get(property, defaultValue);
        return value == null || value.isEmpty() ? defaultValue : value;
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

    /** Changes take effect after a restart: offers one, leaving the page if asked to. */
    public static class RestartDialogFragment extends DialogFragment {
        private static final String ARG_LEAVE = "leave";

        static RestartDialogFragment newInstance(boolean leaveAfterwards) {
            RestartDialogFragment fragment = new RestartDialogFragment();
            Bundle args = new Bundle();
            args.putBoolean(ARG_LEAVE, leaveAfterwards);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            return new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.ram_expansion_restart_dialog_title)
                    .setMessage(R.string.ram_expansion_restart_dialog_message)
                    .setPositiveButton(R.string.ram_expansion_restart_now, (dialog, which) -> {
                        PowerManager powerManager =
                                requireContext().getSystemService(PowerManager.class);
                        if (powerManager != null) {
                            powerManager.reboot(null);
                        }
                    })
                    .setNegativeButton(R.string.ram_expansion_restart_later,
                            (dialog, which) -> leaveIfAsked())
                    .create();
        }

        @Override
        public void onCancel(@NonNull DialogInterface dialog) {
            super.onCancel(dialog);
            leaveIfAsked();
        }

        private void leaveIfAsked() {
            if (requireArguments().getBoolean(ARG_LEAVE)) {
                requireActivity().finish();
            }
        }
    }

    /** Leaving with unsaved changes: save them (then offer a restart), discard them or stay. */
    public static class UnsavedDialogFragment extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            RamExpansionFragment page = (RamExpansionFragment) requireParentFragment();
            return new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.ram_expansion_unsaved_title)
                    .setMessage(R.string.ram_expansion_unsaved_message)
                    .setPositiveButton(R.string.ram_expansion_save,
                            (dialog, which) -> page.saveAndOfferRestart(true))
                    .setNegativeButton(R.string.ram_expansion_discard,
                            (dialog, which) -> requireActivity().finish())
                    .setNeutralButton(android.R.string.cancel, null)
                    .create();
        }
    }
}
