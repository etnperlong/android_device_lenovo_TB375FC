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

import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.settings.device.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The color setting of each launchable app: the screen color settings, enhancements off, or a
 * color mode of its own. Only choices other than the screen color settings are stored.
 */
public class ScreenColorAppsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {
    private ContentResolver mResolver;
    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;

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
                getString(R.string.color_app_profile_off),
                getString(R.string.color_mode_standard),
                getString(R.string.color_mode_vivid),
                getString(R.string.color_mode_natural),
        };
        mEntryValues = new CharSequence[] {
                String.valueOf(ColorSettings.APP_DEFAULT),
                String.valueOf(ColorSettings.APP_OFF),
                String.valueOf(ColorSettings.MODE_STANDARD),
                String.valueOf(ColorSettings.MODE_VIVID),
                String.valueOf(ColorSettings.MODE_NATURAL),
        };

        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(context));

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
            pref.setOnPreferenceChangeListener(this);
            screen.addPreference(pref);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ColorSettings.putAppProfile(mResolver, preference.getKey(),
                Integer.parseInt((String) newValue));
        return true;
    }
}
