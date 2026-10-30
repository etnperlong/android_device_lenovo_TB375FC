/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.ContentResolver;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import org.lineageos.settings.device.R;
import org.lineageos.settings.device.TileChoiceDialog;

/**
 * QS tile for the screen color mode: shows it and picks Standard, Vivid or Disabled in a dialog
 * over the shade, like the mode cards of the Screen color page. Active unless Disabled.
 */
public class ScreenColorTile extends TileService {
    /** Dialog entries, in the order of the Screen color page's cards. */
    private static final int[] MODES = {
            ColorSettings.MODE_STANDARD, ColorSettings.MODE_VIVID, ColorSettings.MODE_DISABLED,
    };
    private static final int[] ENTRIES = {
            R.string.color_mode_standard, R.string.color_mode_vivid, R.string.color_mode_disabled,
    };

    @Override
    public void onStartListening() {
        updateTile();
    }

    @Override
    public void onClick() {
        final ContentResolver cr = getContentResolver();
        final CharSequence[] entries = new CharSequence[ENTRIES.length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = getText(ENTRIES[i]);
        }
        // The tile sets the screen color settings; app settings still win while their app shows.
        final CharSequence note = ColorSettings.isAppProfilesEnabled(cr)
                ? getText(R.string.tile_color_app_note) : null;
        TileChoiceDialog.show(this, R.string.screen_color_title, entries,
                indexOf(ColorSettings.getMode(cr)), note, index -> {
                    ColorSettings.putMode(cr, MODES[index]);
                    updateTile();
                });
    }

    private static int indexOf(int mode) {
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i] == mode) {
                return i;
            }
        }
        return 0;
    }

    private void updateTile() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        final int mode = ColorSettings.getMode(getContentResolver());
        tile.setState(mode == ColorSettings.MODE_DISABLED
                ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.setSubtitle(getText(ENTRIES[indexOf(mode)]));
        tile.updateTile();
    }
}
