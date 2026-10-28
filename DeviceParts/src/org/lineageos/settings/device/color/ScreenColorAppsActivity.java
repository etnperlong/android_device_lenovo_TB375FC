/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import androidx.fragment.app.Fragment;

/** App color settings, opened from ScreenColorFragment. */
public class ScreenColorAppsActivity extends ScreenColorActivity {
    @Override
    protected Fragment createFragment() {
        return new ScreenColorAppsFragment();
    }
}
