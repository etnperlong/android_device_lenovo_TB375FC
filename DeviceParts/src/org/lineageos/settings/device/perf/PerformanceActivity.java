/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/** Performance profiles; injected into the Settings Battery page. */
public class PerformanceActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            createFragment())
                    .commit();
        }
    }

    protected Fragment createFragment() {
        return new PerformanceFragment();
    }
}
