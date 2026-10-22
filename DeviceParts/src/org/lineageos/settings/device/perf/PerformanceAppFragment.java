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
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The profile of one app: follow the vendor rules, off, or custom. A custom profile uses one of
 * the vendor rule sets as preset, starting with the app's own if it has one; with advanced
 * parameters, the preset's CPU frequency limits are replaced by the sliders (one minimum and one
 * maximum per cluster, along the cpufreq table) and the refresh rate can be limited.
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
    private PerfLabels mLabels;
    private List<CpuClusters.Cluster> mClusters;
    private String mPackage;
    private PerfProfile mProfile;

    private ListPreference mMode;
    private ListPreference mPreset;
    private Preference mPresetDetails;
    private SwitchPreferenceCompat mAdvanced;
    private PreferenceCategory mCpu;
    private SeekBarPreference[] mCpuMin;
    private SeekBarPreference[] mCpuMax;
    private ListPreference mRefreshRate;
    private Preference mReset;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = requireContext();
        mResolver = context.getContentResolver();
        mVendor = VendorAppList.get();
        mLabels = new PerfLabels(context, mVendor);
        mClusters = CpuClusters.get();
        mPackage = requireActivity().getIntent().getStringExtra(
                PerformanceAppActivity.EXTRA_PACKAGE);
        mProfile = PerfSettings.getAppProfile(mResolver, mPackage);

        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);

        final boolean listed = mVendor.isListed(mPackage);
        mMode = new ListPreference(context);
        mMode.setKey(KEY_MODE);
        mMode.setPersistent(false);
        mMode.setTitle(R.string.perf_mode_title);
        mMode.setDialogTitle(R.string.perf_mode_title);
        mMode.setEntries(listed
                ? new CharSequence[] {getString(R.string.perf_mode_vendor),
                        getString(R.string.perf_mode_custom), getString(R.string.perf_mode_off)}
                : new CharSequence[] {getString(R.string.perf_mode_default),
                        getString(R.string.perf_mode_custom)});
        mMode.setEntryValues(listed
                ? new CharSequence[] {String.valueOf(PerfProfile.MODE_VENDOR),
                        String.valueOf(PerfProfile.MODE_CUSTOM),
                        String.valueOf(PerfProfile.MODE_OFF)}
                : new CharSequence[] {String.valueOf(PerfProfile.MODE_VENDOR),
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
        mCpuMin = new SeekBarPreference[mClusters.size()];
        mCpuMax = new SeekBarPreference[mClusters.size()];
        for (int i = 0; i < mClusters.size(); i++) {
            final String name = mLabels.clusterName(i, mClusters.get(i), mClusters.size());
            mCpuMin[i] = addCpuSlider(context, i, false,
                    getString(R.string.perf_cpu_min_title, name));
            mCpuMax[i] = addCpuSlider(context, i, true,
                    getString(R.string.perf_cpu_max_title, name));
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

    private SeekBarPreference addCpuSlider(Context context, int cluster, boolean max,
            String title) {
        final SeekBarPreference slider = new SeekBarPreference(context);
        slider.setKey((max ? "perf_cpu_max_" : "perf_cpu_min_") + cluster);
        slider.setPersistent(false);
        slider.setTitle(title);
        slider.setMin(0);
        slider.setMax(mClusters.get(cluster).frequencies.length - 1);
        slider.setShowSeekBarValue(false);
        slider.setUpdatesContinuously(false);
        slider.setOnPreferenceChangeListener((preference, newValue) -> {
            setCpuLimit(cluster, max, (Integer) newValue);
            return true;
        });
        mCpu.addPreference(slider);
        return slider;
    }

    /** Distinct refresh rates of the built-in display, rounded to whole Hz, ascending. */
    private static List<Integer> supportedRefreshRates(Context context) {
        final Display display = context.getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
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
            // Start from the app's own vendor rules, if it has any.
            final VendorAppList.Preset preset = mVendor.getAppPreset(mPackage);
            profile.preset = preset != null ? preset.id : "";
        }
        mProfile = profile;
        save();
    }

    /** Moves one slider; the other limit of the cluster follows so that min <= max. */
    private void setCpuLimit(int cluster, boolean max, int index) {
        final CpuClusters.Cluster table = mClusters.get(cluster);
        final int khz = table.frequencies[index];
        ensureCpuArrays();
        if (max) {
            mProfile.cpuMax[cluster] = khz;
            mProfile.cpuMin[cluster] = Math.min(mProfile.cpuMin[cluster], khz);
        } else {
            mProfile.cpuMin[cluster] = khz;
            mProfile.cpuMax[cluster] = Math.max(mProfile.cpuMax[cluster], khz);
        }
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
        final VendorAppList.Preset preset = mVendor.getPreset(
                custom ? mProfile.preset : (mProfile.mode == PerfProfile.MODE_VENDOR
                        ? idOf(mVendor.getAppPreset(mPackage)) : null));
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
            mCpuMin[i].setValue(cluster.indexOf(mProfile.cpuMin[i]));
            mCpuMin[i].setSummary(mLabels.formatFrequency(mProfile.cpuMin[i]));
            mCpuMax[i].setValue(cluster.indexOf(mProfile.cpuMax[i]));
            mCpuMax[i].setSummary(mLabels.formatFrequency(mProfile.cpuMax[i]));
        }
        mRefreshRate.setValue(String.valueOf(mProfile.refreshRate));
    }

    private static String idOf(VendorAppList.Preset preset) {
        return preset != null ? preset.id : null;
    }
}
