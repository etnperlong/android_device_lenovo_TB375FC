/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.content.ContentResolver;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The profile of one app: follow the vendor rules, off, or custom. A custom profile uses one of
 * the vendor rule sets as preset, starting with the app's own if it has one; with advanced
 * parameters, the preset's CPU frequency limits are replaced by a linked range for each cluster
 * along the cpufreq table, and the refresh rate can be limited.
 */
public class PerformanceAppFragment extends SettingsBasePreferenceFragment {
    private static final String KEY_MODE = "perf_mode";
    private static final String KEY_PRESET = "perf_preset";
    private static final String KEY_PRESET_DETAILS = "perf_preset_details";
    private static final String KEY_ADVANCED = "perf_advanced";
    private static final String KEY_CPU = "perf_cpu";
    private static final String KEY_REFRESH_RATE = "perf_refresh_rate";
    private static final String KEY_RESET = "perf_reset";

    private ContentResolver mResolver;
    private VendorAppList mVendor;
    private RecommendedApps mRecommended;
    private PerfLabels mLabels;
    private List<CpuClusters.Cluster> mClusters;
    private String mPackage;
    private PerfProfile mProfile;

    private ListPreference mMode;
    private ListPreference mPreset;
    private Preference mPresetDetails;
    private SwitchPreferenceCompat mAdvanced;
    private PreferenceCategory mCpu;
    private CpuRangePreference[] mCpuRanges;
    private ListPreference mRefreshRate;
    private Preference mReset;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = requireContext();
        mResolver = context.getContentResolver();
        mVendor = VendorAppList.get();
        mRecommended = RecommendedApps.get(context);
        mLabels = new PerfLabels(context, mVendor, mRecommended);
        mClusters = CpuClusters.get();
        mPackage = requireActivity().getIntent().getStringExtra(
                PerformanceAppActivity.EXTRA_PACKAGE);
        mProfile = PerfSettings.getAppProfile(mResolver, mPackage);

        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);

        // The default is called after what it does: the vendor rules, a recommended preset, or
        // nothing (then "off" would be the same, so it is not offered).
        final boolean listed = mVendor.isListed(mPackage);
        final boolean recommended = mRecommended.getPreset(mPackage) != null;
        mMode = new ListPreference(context);
        mMode.setKey(KEY_MODE);
        mMode.setPersistent(false);
        mMode.setTitle(R.string.perf_mode_title);
        mMode.setDialogTitle(R.string.perf_mode_title);
        final CharSequence defaultLabel = getString(listed ? R.string.perf_mode_vendor
                : recommended ? R.string.perf_mode_recommended : R.string.perf_mode_default);
        mMode.setEntries(listed || recommended
                ? new CharSequence[] {defaultLabel, getString(R.string.perf_mode_custom),
                        getString(R.string.perf_mode_off)}
                : new CharSequence[] {defaultLabel, getString(R.string.perf_mode_custom)});
        mMode.setEntryValues(listed || recommended
                ? new CharSequence[] {String.valueOf(PerfProfile.MODE_DEFAULT),
                        String.valueOf(PerfProfile.MODE_CUSTOM),
                        String.valueOf(PerfProfile.MODE_OFF)}
                : new CharSequence[] {String.valueOf(PerfProfile.MODE_DEFAULT),
                        String.valueOf(PerfProfile.MODE_CUSTOM)});
        mMode.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        mMode.setOnPreferenceChangeListener((preference, newValue) -> {
            setMode(Integer.parseInt((String) newValue));
            return true;
        });
        screen.addPreference(mMode);

        final List<VendorAppList.Preset> presets = mVendor.getPresets();
        final CharSequence[] presetEntries = new CharSequence[presets.size() + 1];
        final CharSequence[] presetValues = new CharSequence[presets.size() + 1];
        presetEntries[0] = getString(R.string.perf_preset_none);
        presetValues[0] = "";
        for (int i = 0; i < presets.size(); i++) {
            presetEntries[i + 1] = mLabels.presetLabel(presets.get(i));
            presetValues[i + 1] = presets.get(i).id;
        }
        mPreset = new ListPreference(context);
        mPreset.setKey(KEY_PRESET);
        mPreset.setPersistent(false);
        mPreset.setTitle(R.string.perf_preset_title);
        mPreset.setDialogTitle(R.string.perf_preset_title);
        mPreset.setEntries(presetEntries);
        mPreset.setEntryValues(presetValues);
        mPreset.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        mPreset.setOnPreferenceChangeListener((preference, newValue) -> {
            mProfile.preset = (String) newValue;
            if (mProfile.advanced) {
                resetCpuToPreset();
            }
            save();
            return true;
        });
        screen.addPreference(mPreset);

        mPresetDetails = new Preference(context);
        mPresetDetails.setKey(KEY_PRESET_DETAILS);
        mPresetDetails.setTitle(R.string.perf_preset_details_title);
        mPresetDetails.setSelectable(false);
        screen.addPreference(mPresetDetails);

        mAdvanced = new SwitchPreferenceCompat(context);
        mAdvanced.setKey(KEY_ADVANCED);
        mAdvanced.setPersistent(false);
        mAdvanced.setTitle(R.string.perf_advanced_title);
        mAdvanced.setSummary(R.string.perf_advanced_summary);
        mAdvanced.setOnPreferenceChangeListener((preference, newValue) -> {
            mProfile.advanced = (Boolean) newValue;
            if (mProfile.advanced) {
                resetCpuToPreset();
            }
            save();
            return true;
        });
        screen.addPreference(mAdvanced);

        mCpu = new PreferenceCategory(context);
        mCpu.setKey(KEY_CPU);
        mCpu.setTitle(R.string.category_perf_cpu);
        screen.addPreference(mCpu);
        mCpuRanges = new CpuRangePreference[mClusters.size()];
        for (int i = 0; i < mClusters.size(); i++) {
            final int clusterIndex = i;
            final CpuClusters.Cluster cluster = mClusters.get(i);
            final String name = mLabels.clusterName(i, cluster, mClusters.size());
            final String[] frequencies = new String[cluster.frequencies.length];
            for (int j = 0; j < frequencies.length; j++) {
                frequencies[j] = mLabels.formatFrequency(cluster.frequencies[j]);
            }
            mCpuRanges[i] = new CpuRangePreference(context);
            mCpuRanges[i].setKey("perf_cpu_range_" + i);
            mCpuRanges[i].setTitle(name);
            mCpuRanges[i].setFrequencies(frequencies);
            mCpuRanges[i].setDescriptions(getString(R.string.perf_cpu_min_description, name),
                    getString(R.string.perf_cpu_max_description, name),
                    getString(R.string.perf_cpu_range_description, name));
            mCpuRanges[i].setOnLimitsChangedListener(
                    (min, max) -> setCpuLimits(clusterIndex, min, max));
            mCpu.addPreference(mCpuRanges[i]);
        }

        mRefreshRate = new ListPreference(context);
        mRefreshRate.setKey(KEY_REFRESH_RATE);
        mRefreshRate.setPersistent(false);
        mRefreshRate.setTitle(R.string.perf_refresh_rate_title);
        mRefreshRate.setDialogTitle(R.string.perf_refresh_rate_title);
        final List<Integer> rates = supportedRefreshRates(context);
        final CharSequence[] rateEntries = new CharSequence[rates.size() + 1];
        final CharSequence[] rateValues = new CharSequence[rates.size() + 1];
        rateEntries[0] = getString(R.string.perf_refresh_rate_none);
        rateValues[0] = "0";
        for (int i = 0; i < rates.size(); i++) {
            rateEntries[i + 1] = getString(R.string.perf_refresh_rate_hz, rates.get(i));
            rateValues[i + 1] = String.valueOf(rates.get(i));
        }
        mRefreshRate.setEntries(rateEntries);
        mRefreshRate.setEntryValues(rateValues);
        mRefreshRate.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        mRefreshRate.setOnPreferenceChangeListener((preference, newValue) -> {
            mProfile.refreshRate = Integer.parseInt((String) newValue);
            save();
            return true;
        });
        screen.addPreference(mRefreshRate);

        mReset = new Preference(context);
        mReset.setKey(KEY_RESET);
        mReset.setTitle(R.string.perf_advanced_reset_title);
        mReset.setOnPreferenceClickListener(preference -> {
            resetCpuToPreset();
            mProfile.refreshRate = 0;
            save();
            return true;
        });
        screen.addPreference(mReset);

        setPreferenceScreen(screen);
        updateUi();
    }

    /** Distinct refresh rates of the built-in display, rounded to whole Hz, ascending. */
    private static List<Integer> supportedRefreshRates(Context context) {
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        final Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        final TreeSet<Integer> rates = new TreeSet<>();
        if (display != null) {
            for (Display.Mode mode : display.getSupportedModes()) {
                rates.add(Math.round(mode.getRefreshRate()));
            }
        }
        return new ArrayList<>(rates);
    }

    private void setMode(int mode) {
        final PerfProfile profile = new PerfProfile();
        profile.mode = mode;
        if (mode == PerfProfile.MODE_CUSTOM) {
            // Start from the rules the app gets by default, if any.
            final VendorAppList.Preset preset = defaultPreset();
            profile.preset = preset != null ? preset.id : "";
        }
        mProfile = profile;
        save();
    }

    /** The vendor rules of the app, or its recommended preset, or null. */
    private VendorAppList.Preset defaultPreset() {
        final VendorAppList.Preset vendor = mVendor.getAppPreset(mPackage);
        return vendor != null ? vendor : mRecommended.getPreset(mPackage);
    }

    /** Stores a cluster's limits, table indexes with min <= max (CpuRangePreference). */
    private void setCpuLimits(int cluster, int minIndex, int maxIndex) {
        final CpuClusters.Cluster table = mClusters.get(cluster);
        ensureCpuArrays();
        mProfile.cpuMin[cluster] = table.frequencies[minIndex];
        mProfile.cpuMax[cluster] = table.frequencies[maxIndex];
        save();
    }

    /** CPU limits of the preset, the full table where it sets none. */
    private void resetCpuToPreset() {
        final VendorAppList.Preset preset = mVendor.getPreset(mProfile.preset);
        mProfile.cpuMin = new int[mClusters.size()];
        mProfile.cpuMax = new int[mClusters.size()];
        for (int i = 0; i < mClusters.size(); i++) {
            final CpuClusters.Cluster cluster = mClusters.get(i);
            final Integer min = preset != null
                    ? preset.valueOf(VendorAppList.cpuFreqMinCommand(i)) : null;
            final Integer max = preset != null
                    ? preset.valueOf(VendorAppList.cpuFreqMaxCommand(i)) : null;
            mProfile.cpuMin[i] = min != null
                    ? cluster.frequencies[cluster.indexOf(min)] : cluster.lowest();
            mProfile.cpuMax[i] = max != null
                    ? cluster.frequencies[cluster.indexOf(max)] : cluster.highest();
        }
    }

    /** Stored limits may be missing or from another cpufreq table; snap them to this one. */
    private void ensureCpuArrays() {
        final int[] min = new int[mClusters.size()];
        final int[] max = new int[mClusters.size()];
        for (int i = 0; i < mClusters.size(); i++) {
            final CpuClusters.Cluster cluster = mClusters.get(i);
            min[i] = cluster.frequencies[cluster.indexOf(
                    PerfResources.valueAt(mProfile.cpuMin, i, cluster.lowest()))];
            max[i] = cluster.frequencies[cluster.indexOf(
                    PerfResources.valueAt(mProfile.cpuMax, i, cluster.highest()))];
        }
        mProfile.cpuMin = min;
        mProfile.cpuMax = max;
    }

    private void save() {
        PerfSettings.putAppProfile(mResolver, mPackage, mProfile.copy());
        updateUi();
    }

    private void updateUi() {
        final boolean custom = mProfile.mode == PerfProfile.MODE_CUSTOM;
        final boolean advanced = custom && mProfile.advanced;
        mMode.setValue(String.valueOf(mProfile.mode));

        mPreset.setVisible(custom);
        mPreset.setValue(mProfile.preset);
        final VendorAppList.Preset preset = custom ? mVendor.getPreset(mProfile.preset)
                : mProfile.isDefault() ? defaultPreset() : null;
        mPresetDetails.setVisible(preset != null);
        if (preset != null) {
            mPresetDetails.setSummary(mLabels.presetDetails(preset));
        }

        mAdvanced.setVisible(custom);
        mAdvanced.setChecked(mProfile.advanced);
        mCpu.setVisible(advanced && !mClusters.isEmpty());
        mRefreshRate.setVisible(advanced);
        mReset.setVisible(advanced);
        if (!advanced) {
            return;
        }
        ensureCpuArrays();
        for (int i = 0; i < mClusters.size(); i++) {
            final CpuClusters.Cluster cluster = mClusters.get(i);
            mCpuRanges[i].setLimits(cluster.indexOf(mProfile.cpuMin[i]),
                    cluster.indexOf(mProfile.cpuMax[i]));
        }
        mRefreshRate.setValue(String.valueOf(mProfile.refreshRate));
    }
}

