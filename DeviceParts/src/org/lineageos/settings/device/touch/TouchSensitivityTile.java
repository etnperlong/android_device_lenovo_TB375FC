/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.touch;

import android.app.AlertDialog;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.ContextThemeWrapper;

import org.lineageos.settings.device.R;

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
        if (isLocked()) {
            unlockAndRun(this::showLevelDialog);
        } else {
            showLevelDialog();
        }
    }

    private void showLevelDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(
                new ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_DayNight))
                .setTitle(R.string.touch_sensitivity_title)
                .setSingleChoiceItems(R.array.touch_sensitivity_entries,
                        TouchSensitivity.getLevel(this), (d, which) -> {
                            TouchSensitivity.setLevel(this, which);
                            updateTile();
                            d.dismiss();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showDialog(dialog);
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
