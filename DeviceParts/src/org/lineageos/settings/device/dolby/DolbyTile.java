/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import org.lineageos.settings.device.R;
import org.lineageos.settings.device.TileChoiceDialog;

/**
 * QS tile for the Dolby Atmos mode: shows it and picks Dynamic, Movie, Music or Off in a dialog
 * over the shade, like the mode cards of the Dolby page. Active while Dolby is on.
 *
 * It replaces daxService's own tile, which only switches Dolby on and off, so that tile is
 * hidden.
 */
public class DolbyTile extends TileService {
    private static final String TAG = "DolbyTile";
    private static final ComponentName VENDOR_TILE = new ComponentName(
            "com.dolby.daxservice", "com.dolby.daxservice.DaxTileService");

    /** Dialog entries: the DAX profiles by their PROFILE_* value, then Off. */
    private static final int[] ENTRIES = {
            R.string.dolby_profile_dynamic,
            R.string.dolby_profile_movie,
            R.string.dolby_profile_music,
            R.string.dolby_mode_off,
    };
    private static final int[] ICONS = {
            R.drawable.ic_dolby_dynamic,
            R.drawable.ic_dolby_movie,
            R.drawable.ic_dolby_music,
            R.drawable.ic_dolby_off,
    };
    private static final int OFF = ENTRIES.length - 1;

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
        // The tile sets the default mode; app modes still win while their app plays.
        final CharSequence note = DolbySettings.isAppProfilesEnabled(cr)
                ? getText(R.string.tile_dolby_app_note) : null;
        TileChoiceDialog.show(this, R.string.tile_dolby, entries, getChoice(cr), note,
                choice -> {
                    if (choice == OFF) {
                        DolbySettings.putDolbyOn(cr, false);
                    } else {
                        DolbySettings.putProfile(cr, choice);
                        DolbySettings.putDolbyOn(cr, true);
                    }
                    updateTile();
                });
    }

    /** Hides daxService's on/off tile; SystemUI drops it from the panel once it is disabled. */
    public static void hideVendorTile(Context context) {
        final PackageManager pm = context.getPackageManager();
        try {
            if (pm.getComponentEnabledSetting(VENDOR_TILE)
                    != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(VENDOR_TILE,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "daxService is not installed", e);
        }
    }

    private static int getChoice(ContentResolver cr) {
        return DolbySettings.isDolbyOn(cr) ? DolbySettings.getProfile(cr) : OFF;
    }

    private void updateTile() {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        final int choice = getChoice(getContentResolver());
        tile.setState(choice == OFF ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.setSubtitle(getText(ENTRIES[choice]));
        tile.setIcon(Icon.createWithResource(this, ICONS[choice]));
        tile.updateTile();
    }
}
