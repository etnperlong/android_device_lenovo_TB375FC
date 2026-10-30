/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.view.View;

/** Shows and hides the controls laid over the sample pictures and clips. */
final class GalleryChrome {
    private static final long FADE_MS = 150;

    private GalleryChrome() {
    }

    /** Shows or hides view, fading from wherever an earlier fade left it if animate. */
    static void show(View view, boolean show, boolean animate) {
        view.animate().cancel();
        if (!animate) {
            view.setAlpha(1f);
            view.setVisibility(show ? View.VISIBLE : View.GONE);
        } else if (show) {
            if (view.getVisibility() != View.VISIBLE) {
                view.setAlpha(0f);
                view.setVisibility(View.VISIBLE);
            }
            view.animate().alpha(1f).setDuration(FADE_MS);
        } else if (view.getVisibility() == View.VISIBLE) {
            view.animate().alpha(0f).setDuration(FADE_MS)
                    .withEndAction(() -> view.setVisibility(View.GONE));
        }
    }
}
