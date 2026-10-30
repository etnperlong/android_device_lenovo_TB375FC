/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The switch for the app color settings, then the color setting of each launchable app: the
 * screen color settings, or a color mode of its own (Standard, Vivid or Disabled). Only choices
 * other than the screen color settings are stored.
 */
public class ScreenColorAppsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String KEY_PROFILES_ENABLED = "color_app_profiles_enabled";

    private ContentResolver mResolver;
    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private MainSwitchPreference mProfilesEnabled;
    private final List<Preference> mAppPreferences = new ArrayList<>();

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
        mEntries = new CharSequence[] {
                getString(R.string.color_app_profile_default),
                getString(R.string.color_mode_standard),
                getString(R.string.color_mode_vivid),
                getString(R.string.color_mode_disabled),
        };
        mEntryValues = new CharSequence[] {
                String.valueOf(ColorSettings.APP_DEFAULT),
                String.valueOf(ColorSettings.MODE_STANDARD),
                String.valueOf(ColorSettings.MODE_VIVID),
                String.valueOf(ColorSettings.MODE_DISABLED),
        };

        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        mProfilesEnabled = new MainSwitchPreference(context);
        mProfilesEnabled.setKey(KEY_PROFILES_ENABLED);
        mProfilesEnabled.setPersistent(false);
        mProfilesEnabled.setTitle(R.string.color_app_profiles_enabled_title);
        mProfilesEnabled.setSummary(R.string.color_app_profiles_enabled_summary);
        mProfilesEnabled.setChecked(ColorSettings.isAppProfilesEnabled(mResolver));
        mProfilesEnabled.setOnPreferenceChangeListener(this);
        screen.addPreference(mProfilesEnabled);
        setPreferenceScreen(screen);
        updateAppPreferencesEnabled(mProfilesEnabled.isChecked());

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
        }, "ScreenColorApps").start();
    }

    @Override
    public void onResume() {
        super.onResume();
        final boolean enabled = ColorSettings.isAppProfilesEnabled(mResolver);
        mProfilesEnabled.setChecked(enabled);
        updateAppPreferencesEnabled(enabled);
    }

    private static List<App> loadApps(Context context) {
        final PackageManager pm = context.getPackageManager();
        final Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
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
        final PreferenceScreen screen = getPreferenceScreen();
        final Map<String, Integer> profiles = ColorSettings.getAppProfiles(mResolver);
        for (App app : apps) {
            final ListPreference pref = new ListPreference(context);
            pref.setKey(app.packageName);
            pref.setPersistent(false);
            pref.setTitle(app.label);
            pref.setDialogTitle(app.label);
            pref.setIcon(app.icon);
            pref.setEntries(mEntries);
            pref.setEntryValues(mEntryValues);
            pref.setValue(String.valueOf(
                    profiles.getOrDefault(app.packageName, ColorSettings.APP_DEFAULT)));
            pref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            pref.setEnabled(mProfilesEnabled.isChecked());
            pref.setOnPreferenceChangeListener(this);
            screen.addPreference(pref);
            mAppPreferences.add(pref);
        }
    }

    private void updateAppPreferencesEnabled(boolean enabled) {
        for (Preference preference : mAppPreferences) {
            preference.setEnabled(enabled);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mProfilesEnabled) {
            final boolean enabled = (Boolean) newValue;
            ColorSettings.setAppProfilesEnabled(mResolver, enabled);
            updateAppPreferencesEnabled(enabled);
            return true;
        }
        ColorSettings.putAppProfile(mResolver, preference.getKey(),
                Integer.parseInt((String) newValue));
        return true;
    }
}
