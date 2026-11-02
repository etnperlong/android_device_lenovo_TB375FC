/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.charging;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Game sessions of DerpFest's GameSpace. Its GameBroadcastReceiver passes the framework's
 * GAME_START (with the game's package) and GAME_STOP on to every other app that receives them,
 * guarded by MANAGE_GAME_MODE; ChargingService then uses bypass charging for the game if
 * ChargingSettings.KEY_BYPASS_GAMESPACE is on.
 */
public class GameSpaceReceiver extends BroadcastReceiver {
    static final String ACTION_GAME_START = "io.chaldeaprjkt.gamespace.action.GAME_START";
    static final String ACTION_GAME_STOP = "io.chaldeaprjkt.gamespace.action.GAME_STOP";
    /** GameSpace's SessionService.EXTRA_PACKAGE_NAME. */
    static final String EXTRA_PACKAGE_NAME = "package_name";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (!ACTION_GAME_START.equals(action) && !ACTION_GAME_STOP.equals(action)) {
            return;
        }
        final Intent service = new Intent(context, ChargingService.class).setAction(action);
        if (ACTION_GAME_START.equals(action)) {
            service.putExtra(EXTRA_PACKAGE_NAME, intent.getStringExtra(EXTRA_PACKAGE_NAME));
        }
        context.startService(service);
    }
}
