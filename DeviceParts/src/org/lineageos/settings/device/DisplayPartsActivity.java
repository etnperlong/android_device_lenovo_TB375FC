/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

/** Display and touch settings; injected into Settings > Display. */
public class DisplayPartsActivity extends DevicePartsActivity {
    @Override
    protected int getPreferencesResId() {
        return R.xml.display_parts;
    }
}
