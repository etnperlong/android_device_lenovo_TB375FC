/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.touch;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.lineageos.settings.device.R;
import org.lineageos.settings.device.TileChoiceDialog;

/**
 * QS tile for {@link TouchSensitivity}: shows the level and picks another one in a dialog over
 * the shade. Active while the level differs from the default.
 */
public class TouchSensitivityTile extends TileService {
    private static final int[] SUBTITLES = {
            R.string.tile_touch_sensitivity_low,
            R.string.tile_touch_sensitivity_medium,
            R.string.tile_touch_sensitivity_high,
    };

    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        if (!TouchSensitivity.isAvailable()) {
            updateTile();
            return;
        }
        TileChoiceDialog.show(this, R.string.touch_sensitivity_title,
                getResources().getTextArray(R.array.touch_sensitivity_entries),
                TouchSensitivity.getLevel(this), null, level -> {
                    TouchSensitivity.setLevel(this, level);
                    updateTile();
                });
    }

    private void updateTile() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        if (TouchSensitivity.isAvailable()) {
            final int level = TouchSensitivity.getLevel(this);
            tile.setState(level == TouchSensitivity.LEVEL_DEFAULT
                    ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
            tile.setSubtitle(getString(SUBTITLES[level]));
        } else {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(getString(R.string.touch_sensitivity_unavailable));
        }
        tile.updateTile();
    }
}
