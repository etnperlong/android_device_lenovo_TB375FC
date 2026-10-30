/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.lineageos.settings.device.R;

/**
 * QS toggle for adapting the white point to the ambient light. Unavailable while the screen
 * color mode is Disabled, which keeps the setting but does not apply it.
 */
public class ColorAdaptiveTile extends TileService {
    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        final ContentResolver cr = getContentResolver();
        if (ColorSettings.getMode(cr) != ColorSettings.MODE_DISABLED) {
            ColorSettings.setAdaptive(cr, !ColorSettings.isAdaptive(cr));
        }
        updateTile();
    }

    private void updateTile() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        final ContentResolver cr = getContentResolver();
        if (ColorSettings.getMode(cr) == ColorSettings.MODE_DISABLED) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(getText(R.string.tile_color_adaptive_unavailable));
        } else {
            tile.setState(ColorSettings.isAdaptive(cr) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            // The system's On / Off subtitle.
            tile.setSubtitle(null);
        }
        tile.updateTile();
    }
}
