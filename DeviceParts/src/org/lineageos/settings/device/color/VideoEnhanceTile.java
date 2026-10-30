/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** QS toggle for MediaTek's video picture enhancements. */
public class VideoEnhanceTile extends TileService {
    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        final ContentResolver cr = getContentResolver();
        ColorSettings.setVideoEnhance(cr, !ColorSettings.isVideoEnhance(cr));
        updateTile();
    }

    private void updateTile() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(ColorSettings.isVideoEnhance(getContentResolver())
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
