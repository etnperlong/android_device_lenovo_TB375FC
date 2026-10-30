/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.touch;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Palm rejection level of the Novatek touch controller, shown as touch sensitivity.
 *
 * nt36532.ko takes /proc/game_mode "0" (normal, controller command 0x79 0x00), "1" (pen mode,
 * enhanced palm rejection, 0x79 0x02) or "2" (game mode, palm rejection removed, 0x79 0x01) and
 * ignores other values. These are the three levels the Lenovo touchscreen HAL offers through
 * setBigPalm, which writes its argument to the same node; stock's game assistant labels the
 * removed rejection as the most responsive one.
 *
 * The driver re-sends the level on resume but starts at normal on every boot, so the level is
 * kept in a Secure setting and written back by the boot receiver.
 */
public final class TouchSensitivity {
    private static final String TAG = "TouchSensitivity";
    private static final String GAME_MODE_PATH = "/proc/game_mode";

    /** Secure setting, one of the LEVEL_* values. */
    public static final String KEY_LEVEL = "tb375fc_touch_sensitivity";

    public static final int LEVEL_LOW = 0;
    public static final int LEVEL_MEDIUM = 1;
    public static final int LEVEL_HIGH = 2;
    public static final int LEVEL_DEFAULT = LEVEL_MEDIUM;

    /** /proc/game_mode value of each level, indexed by LEVEL_*. */
    private static final char[] NODE_VALUES = {'1', '0', '2'};

    private TouchSensitivity() {}

    public static boolean isAvailable() {
        final File node = new File(GAME_MODE_PATH);
        return node.canRead() && node.canWrite();
    }

    public static int getLevel(Context context) {
        final int level = Settings.Secure.getInt(context.getContentResolver(), KEY_LEVEL,
                LEVEL_DEFAULT);
        return level >= LEVEL_LOW && level <= LEVEL_HIGH ? level : LEVEL_DEFAULT;
    }

    /** Applies the level and saves it if the driver took it. */
    public static boolean setLevel(Context context, int level) {
        if (level < LEVEL_LOW || level > LEVEL_HIGH || !write(level)) {
            return false;
        }
        final ContentResolver cr = context.getContentResolver();
        return Settings.Secure.putInt(cr, KEY_LEVEL, level);
    }

    /** Writes the saved level to the driver, which starts at normal after boot. */
    public static void restore(Context context) {
        write(getLevel(context));
    }

    private static boolean write(int level) {
        try (FileOutputStream out = new FileOutputStream(GAME_MODE_PATH)) {
            out.write(NODE_VALUES[level]);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write " + GAME_MODE_PATH, e);
            return false;
        }
    }
}
