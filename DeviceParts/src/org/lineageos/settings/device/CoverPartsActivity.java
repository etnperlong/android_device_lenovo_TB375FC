/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

/** Folio cover settings; injected into Settings > Display > Lock screen. */
public class CoverPartsActivity extends DevicePartsActivity {
    @Override
    protected int getPreferencesResId() {
        return R.xml.cover_parts;
    }
}
