/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.memory;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/**
 * RAM expansion settings, matching the stock ZUI memory expansion page. Back and the toolbar's
 * up button both go through the back dispatcher first, where the page asks about unsaved
 * changes; CollapsingToolbarBaseActivity would otherwise finish without asking.
 */
public class RamExpansionActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new RamExpansionFragment())
                    .commit();
        }
    }

    @Override
    public boolean onNavigateUp() {
        if (getOnBackPressedDispatcher().hasEnabledCallbacks()) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onNavigateUp();
    }

    @Override
    public void onBackPressed() {
        if (getOnBackPressedDispatcher().hasEnabledCallbacks()) {
            getOnBackPressedDispatcher().onBackPressed();
            return;
        }
        super.onBackPressed();
    }
}
