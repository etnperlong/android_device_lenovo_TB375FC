/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import androidx.fragment.app.Fragment;

/** Per-app Dolby modes, opened from DolbyFragment. */
public class DolbyAppsActivity extends DolbyActivity {
    @Override
    protected Fragment createFragment() {
        return new DolbyAppsFragment();
    }
}
