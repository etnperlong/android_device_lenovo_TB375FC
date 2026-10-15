/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.lineageos.settings.device.audio.SpeakerRotationService;
import org.lineageos.settings.device.cover.CoverService;
import org.lineageos.settings.device.display.DisplayPolicyService;
import org.lineageos.settings.device.touch.TapToWakeService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            context.startService(new Intent(context, TapToWakeService.class));
            context.startService(new Intent(context, DisplayPolicyService.class));
            context.startService(new Intent(context, CoverService.class));
            context.startService(new Intent(context, SpeakerRotationService.class));
        }
    }
}
