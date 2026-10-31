/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Takes a full-screen gallery out of the Settings split and brings back what it showed last.
 *
 * Opened from a page embedded in the Settings split, an activity of the same task lands in the
 * same pane, next to the Settings menu, and an embedded activity cannot turn the screen. So full
 * screen runs in a task of its own, which cannot return an activity result: it leaves its state
 * here when it finishes, and the inline gallery takes it once its window shows again (or, if the
 * page was recreated meanwhile, once it is attached). Both run in this process, on the main
 * thread.
 */
final class GalleryHandoff {
    private static Class<? extends Activity> sFrom;
    private static Bundle sState;

    private GalleryHandoff() {
    }

    /** Opens full screen in its own task, replacing one left in the background. */
    static void launch(Context context, Class<? extends Activity> activity, Bundle extras) {
        context.startActivity(new Intent(context, activity)
                .putExtras(extras)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    /** Leaves the state the full-screen activity from ends with. */
    static void leave(Class<? extends Activity> from, Bundle state) {
        sFrom = from;
        sState = state;
    }

    /** The state the full-screen activity from left, once; null if there is none. */
    static Bundle take(Class<? extends Activity> from) {
        if (from != sFrom) {
            return null;
        }
        final Bundle state = sState;
        sFrom = null;
        sState = null;
        return state;
    }
}
