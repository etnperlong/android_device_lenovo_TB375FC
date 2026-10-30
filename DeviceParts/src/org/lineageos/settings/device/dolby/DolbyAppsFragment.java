/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

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
 * The Dolby mode of each launchable app: the default mode, or Dynamic, Movie or Music. Apps from
 * stock's whitelist start with their recommended mode; only choices that differ from it are
 * stored. The main switch controls whether these per-app modes are used.
 */
public class DolbyAppsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private static final String KEY_APP_PROFILES_ENABLED = "dolby_app_profiles_enabled";

    private ContentResolver mResolver;
    private Map<String, Integer> mRecommended;
    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private MainSwitchPreference mAppProfilesSwitch;
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
        mRecommended = DolbySettings.getRecommendedAppProfiles(context);
        mEntries = new CharSequence[] {
                getString(R.string.dolby_app_profile_default),
                getString(R.string.dolby_profile_dynamic),
                getString(R.string.dolby_profile_movie),
                getString(R.string.dolby_profile_music),
        };
        mEntryValues = new CharSequence[] {
                String.valueOf(DolbySettings.PROFILE_DEFAULT),
                String.valueOf(DolbySettings.PROFILE_DYNAMIC),
                String.valueOf(DolbySettings.PROFILE_MOVIE),
                String.valueOf(DolbySettings.PROFILE_MUSIC),
        };

        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);
        mAppProfilesSwitch = new MainSwitchPreference(context);
        mAppProfilesSwitch.setKey(KEY_APP_PROFILES_ENABLED);
        mAppProfilesSwitch.setPersistent(false);
        mAppProfilesSwitch.setTitle(R.string.dolby_app_profiles_enabled_title);
        mAppProfilesSwitch.setSummary(R.string.dolby_app_profiles_enabled_summary);
        mAppProfilesSwitch.setOnPreferenceChangeListener(this);
        screen.addPreference(mAppProfilesSwitch);
        updateEnabledState(DolbySettings.isAppProfilesEnabled(mResolver));

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
        }, "DolbyApps").start();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateEnabledState(DolbySettings.isAppProfilesEnabled(mResolver));
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
        final Map<String, Integer> profiles = DolbySettings.mergeAppProfiles(mRecommended,
                DolbySettings.getUserAppProfiles(mResolver));
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
                    profiles.getOrDefault(app.packageName, DolbySettings.PROFILE_DEFAULT)));
            pref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            pref.setOnPreferenceChangeListener(this);
            screen.addPreference(pref);
            mAppPreferences.add(pref);
        }
        updateEnabledState(mAppProfilesSwitch.isChecked());
    }

    private void updateEnabledState(boolean enabled) {
        mAppProfilesSwitch.setChecked(enabled);
        for (Preference preference : mAppPreferences) {
            preference.setEnabled(enabled);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (KEY_APP_PROFILES_ENABLED.equals(preference.getKey())) {
            final boolean enabled = (Boolean) newValue;
            if (!DolbySettings.putAppProfilesEnabled(mResolver, enabled)) {
                return false;
            }
            updateEnabledState(enabled);
            return true;
        }

        final String pkg = preference.getKey();
        final int profile = Integer.parseInt((String) newValue);
        final Map<String, Integer> user = DolbySettings.getUserAppProfiles(mResolver);
        if (profile == mRecommended.getOrDefault(pkg, DolbySettings.PROFILE_DEFAULT)) {
            user.remove(pkg);
        } else {
            user.put(pkg, profile);
        }
        DolbySettings.putUserAppProfiles(mResolver, user);
        return true;
    }
}
