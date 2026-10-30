/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.content.ContentResolver;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** QS toggle for applying the app performance profiles (the Performance page's main switch). */
public class PerformanceTile extends TileService {
    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        final ContentResolver cr = getContentResolver();
        PerfSettings.setEnabled(cr, !PerfSettings.isEnabled(cr));
        updateTile();
    }

    private void updateTile() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(PerfSettings.isEnabled(getContentResolver())
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
