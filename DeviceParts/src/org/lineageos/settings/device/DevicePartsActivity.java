/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/** All device settings; injected into the Settings homepage. */
public class DevicePartsActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            DevicePartsFragment.newInstance(getPreferencesResId()))
                    .commit();
        }
    }

    /** The preference screen this entry point shows. */
    protected int getPreferencesResId() {
        return R.xml.device_parts;
    }
}
