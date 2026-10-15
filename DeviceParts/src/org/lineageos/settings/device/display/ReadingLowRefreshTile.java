/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.display;

import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import lineageos.hardware.LineageHardwareManager;

/**
 * QS toggle for "run at 30 Hz while reading mode is on" (stock: Secure readingmode_lowrefresh).
 * Reading mode itself stays the LineageOS one (QS "Reading mode" tile / LiveDisplay).
 */
public class ReadingLowRefreshTile extends TileService {
    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        if (!isReadingModeSupported()) {
            return;
        }
        Settings.Secure.putInt(getContentResolver(),
                DisplayPolicyService.KEY_READING_LOW_REFRESH, isEnabled() ? 0 : 1);
        updateTile();
    }

    private boolean isReadingModeSupported() {
        return LineageHardwareManager.getInstance(this)
                .isSupported(LineageHardwareManager.FEATURE_READING_ENHANCEMENT);
    }

    private boolean isEnabled() {
        return Settings.Secure.getInt(getContentResolver(),
                DisplayPolicyService.KEY_READING_LOW_REFRESH, 0) != 0;
    }

    private void updateTile() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        if (!isReadingModeSupported()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(isEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }
}
