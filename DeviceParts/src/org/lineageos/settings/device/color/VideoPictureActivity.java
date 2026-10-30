/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import androidx.fragment.app.Fragment;

/** Video picture enhancements; injected into the Settings Display page. */
public class VideoPictureActivity extends ScreenColorActivity {
    @Override
    protected Fragment createFragment() {
        return new VideoPictureFragment();
    }
}
