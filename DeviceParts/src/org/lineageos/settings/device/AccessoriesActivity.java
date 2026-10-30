/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

/** Pen, folio keyboard and magnetic cover settings; injected into Settings > Connected devices. */
public class AccessoriesActivity extends DevicePartsActivity {
    @Override
    protected int getPreferencesResId() {
        return R.xml.accessories;
    }
}
