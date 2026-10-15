/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.touch;

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

/**
 * The Novatek touch "game mode" (/proc/game_mode, "0" = normal, "1" = game). Stock
 * ZuiModeManagerService writes the same values while a game is in front, but only on another
 * product; here it is a manual toggle. The driver keeps the mode across suspend
 * (nvt_restore_cmd_func re-sends it) but not across reboot, so callers read the node instead of
 * keeping their own state.
 */
public final class TouchGameMode {
    private static final String TAG = "TouchGameMode";
    private static final String GAME_MODE_PATH = "/proc/game_mode";

    private TouchGameMode() {}

    /** Returns true if game mode is on, false if off, or null if the node is unusable. */
    public static Boolean read() {
        try (BufferedReader reader = new BufferedReader(new FileReader(GAME_MODE_PATH))) {
            final String line = reader.readLine();
            return line == null ? null : Integer.parseInt(line.trim()) != 0;
        } catch (IOException | NumberFormatException e) {
            Log.e(TAG, "Failed to read " + GAME_MODE_PATH, e);
            return null;
        }
    }

    public static boolean write(boolean enabled) {
        try (FileOutputStream out = new FileOutputStream(GAME_MODE_PATH)) {
            out.write(enabled ? '1' : '0');
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write " + GAME_MODE_PATH, e);
            return false;
        }
    }
}
