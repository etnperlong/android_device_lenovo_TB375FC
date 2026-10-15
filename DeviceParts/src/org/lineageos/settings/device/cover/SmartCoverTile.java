/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.cover;

import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** QS toggle for cover sleep/wake (stock: ZUI Settings "smart cover", System zui_lid_enable). */
public class SmartCoverTile extends TileService {
    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        Settings.Secure.putInt(getContentResolver(), CoverPolicy.KEY_SMART_COVER,
                isEnabled() ? 0 : 1);
        updateTile();
    }

    private boolean isEnabled() {
        return Settings.Secure.getInt(getContentResolver(), CoverPolicy.KEY_SMART_COVER, 1) != 0;
    }

    private void updateTile() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(isEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
