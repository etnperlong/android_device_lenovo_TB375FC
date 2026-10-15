/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.taptowake;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

/**
 * QS toggle for the Novatek touch "game mode" (/proc/game_mode, "0" = normal, "1" = game).
 * Stock ZuiModeManagerService writes the same values while a game is in front, but only on
 * another product; here it is a manual toggle. The driver keeps the mode across suspend
 * (nvt_restore_cmd_func re-sends it) but not across reboot, so the tile reads the node instead of
 * keeping its own state.
 */
public class TouchGameModeTile extends TileService {
    private static final String TAG = "TouchGameModeTile";
    private static final String GAME_MODE_PATH = "/proc/game_mode";

    @Override
    public void onStartListening() {
        updateTile(readMode());
    }

    @Override
    public void onClick() {
        final int mode = readMode();
        if (mode < 0) {
            updateTile(mode);
            return;
        }
        final int newMode = mode == 0 ? 1 : 0;
        updateTile(writeMode(newMode) ? newMode : readMode());
    }

    private void updateTile(int mode) {
        final Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        if (mode < 0) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            tile.setState(mode != 0 ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        tile.updateTile();
    }

    /** Returns the current mode, or -1 if the node is unusable. */
    private static int readMode() {
        try (BufferedReader reader = new BufferedReader(new FileReader(GAME_MODE_PATH))) {
            final String line = reader.readLine();
            return line == null ? -1 : Integer.parseInt(line.trim());
        } catch (IOException | NumberFormatException e) {
            Log.e(TAG, "Failed to read " + GAME_MODE_PATH, e);
            return -1;
        }
    }

    private static boolean writeMode(int mode) {
        try (FileOutputStream out = new FileOutputStream(GAME_MODE_PATH)) {
            out.write(mode == 0 ? '0' : '1');
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write " + GAME_MODE_PATH, e);
            return false;
        }
    }
}
