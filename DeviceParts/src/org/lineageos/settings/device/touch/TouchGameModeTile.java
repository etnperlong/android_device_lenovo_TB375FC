/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.touch;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** QS toggle for {@link TouchGameMode}. */
public class TouchGameModeTile extends TileService {
    @Override
    public void onStartListening() {
        updateTile(TouchGameMode.read());
    }

    @Override
    public void onClick() {
        final Boolean enabled = TouchGameMode.read();
        if (enabled != null && TouchGameMode.write(!enabled)) {
            updateTile(!enabled);
        } else {
            updateTile(TouchGameMode.read());
        }
    }

    private void updateTile(Boolean enabled) {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        if (enabled == null) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }
}
