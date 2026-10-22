/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

/** The profile of one app, opened from PerformanceFragment. */
public class PerformanceAppActivity extends PerformanceActivity {
    /** Package name of the app. */
    static final String EXTRA_PACKAGE = "package";
    /** Its label, shown as the title. */
    static final String EXTRA_LABEL = "label";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getIntent().getStringExtra(EXTRA_LABEL));
    }

    @Override
    protected Fragment createFragment() {
        return new PerformanceAppFragment();
    }
}
