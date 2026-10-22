/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The switch for the performance profiles and the launchable apps, those with vendor rules
 * first. Each app opens its own profile screen.
 */
public class PerformanceFragment extends SettingsBasePreferenceFragment {
    private static final String KEY_ENABLED = "perf_enabled";
    private static final String KEY_VENDOR_APPS = "perf_vendor_apps";
    private static final String KEY_OTHER_APPS = "perf_other_apps";

    private ContentResolver mResolver;
    private VendorAppList mVendor;
    private PerfLabels mLabels;
    private SwitchPreferenceCompat mEnabled;
    private PreferenceCategory mVendorApps;
    private PreferenceCategory mOtherApps;

    private static final class App {
        final String packageName;
        final String label;
        final Drawable icon;

        App(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context context = requireContext();
        mResolver = context.getContentResolver();
        mVendor = VendorAppList.get();
        mLabels = new PerfLabels(context, mVendor);

        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        mEnabled = new SwitchPreferenceCompat(context);
        mEnabled.setKey(KEY_ENABLED);
        mEnabled.setPersistent(false);
        mEnabled.setTitle(R.string.perf_enabled_title);
        mEnabled.setSummary(R.string.perf_enabled_summary);
        mEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
            PerfSettings.setEnabled(mResolver, (Boolean) newValue);
            updateEnabled((Boolean) newValue);
            return true;
        });
        screen.addPreference(mEnabled);

        mVendorApps = new PreferenceCategory(context);
        mVendorApps.setKey(KEY_VENDOR_APPS);
        mVendorApps.setTitle(R.string.category_perf_vendor_apps);
        screen.addPreference(mVendorApps);
        mOtherApps = new PreferenceCategory(context);
        mOtherApps.setKey(KEY_OTHER_APPS);
        mOtherApps.setTitle(R.string.category_perf_other_apps);
        screen.addPreference(mOtherApps);
        setPreferenceScreen(screen);

        // Labels and icons of all apps take a while; load them off the main thread.
        final Activity activity = requireActivity();
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            final List<App> apps = loadApps(appContext);
            activity.runOnUiThread(() -> {
                if (isAdded()) {
                    showApps(apps);
                }
            });
        }, "PerformanceApps").start();
    }

    @Override
    public void onResume() {
        super.onResume();
        final boolean enabled = PerfSettings.isEnabled(mResolver);
        mEnabled.setChecked(enabled);
        updateEnabled(enabled);
        updateSummaries();
    }

    private void updateEnabled(boolean enabled) {
        mVendorApps.setEnabled(enabled);
        mOtherApps.setEnabled(enabled);
    }

    private static List<App> loadApps(Context context) {
        final PackageManager pm = context.getPackageManager();
        final Intent launcher =
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final Set<String> seen = new HashSet<>();
        final List<App> apps = new ArrayList<>();
        for (ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
            final String pkg = info.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            apps.add(new App(pkg, info.activityInfo.applicationInfo.loadLabel(pm).toString(),
                    info.activityInfo.applicationInfo.loadIcon(pm)));
        }
        final Collator collator = Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.label, b.label));
        return apps;
    }

    private void showApps(List<App> apps) {
        final Context context = requireContext();
        for (App app : apps) {
            final Preference pref = new Preference(context);
            pref.setKey(app.packageName);
            pref.setPersistent(false);
            pref.setTitle(app.label);
            pref.setIcon(app.icon);
            pref.setIntent(new Intent(context, PerformanceAppActivity.class)
                    .putExtra(PerformanceAppActivity.EXTRA_PACKAGE, app.packageName)
                    .putExtra(PerformanceAppActivity.EXTRA_LABEL, app.label));
            (mVendor.isListed(app.packageName) ? mVendorApps : mOtherApps).addPreference(pref);
        }
        mVendorApps.setVisible(mVendorApps.getPreferenceCount() > 0);
        updateSummaries();
    }

    private void updateSummaries() {
        final Map<String, PerfProfile> profiles = PerfSettings.getAppProfiles(mResolver);
        for (PreferenceCategory category : new PreferenceCategory[] {mVendorApps, mOtherApps}) {
            for (int i = 0; i < category.getPreferenceCount(); i++) {
                final Preference pref = category.getPreference(i);
                final PerfProfile profile = profiles.get(pref.getKey());
                pref.setSummary(mLabels.profileSummary(pref.getKey(),
                        profile != null ? profile : new PerfProfile()));
            }
        }
    }
}
