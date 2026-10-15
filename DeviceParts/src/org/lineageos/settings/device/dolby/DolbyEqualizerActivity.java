/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import androidx.fragment.app.Fragment;

/** Music mode graphic equalizer, opened from DolbyFragment. */
public class DolbyEqualizerActivity extends DolbyActivity {
    @Override
    protected Fragment createFragment() {
        return new DolbyEqualizerFragment();
    }
}
