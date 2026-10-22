/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.display;

/**
 * Refresh-rate limit of the foreground app's performance profile, handed from
 * PerformanceService to DisplayPolicyService.
 *
 * DisplayManager.requestMaxRefreshRate() keeps one request per process, so the app limit has to
 * be folded into DisplayPolicyService's single value instead of being requested separately.
 * Both services run in this process; the last value is kept so the order they start in does not
 * matter.
 */
public final class AppRefreshRateLimit {
    /** No limit. */
    public static final float NONE = RefreshRateArbiter.NO_LIMIT;

    /** Receives the new limit in Hz, {@link #NONE} for none. */
    interface Listener {
        void onAppRefreshRateLimitChanged(float hz);
    }

    private static float sLimitHz = NONE;
    private static Listener sListener;

    private AppRefreshRateLimit() {
    }

    /** Sets the limit, or clears it with {@link #NONE}. */
    public static void set(float hz) {
        final Listener listener;
        synchronized (AppRefreshRateLimit.class) {
            if (hz == sLimitHz) {
                return;
            }
            sLimitHz = hz;
            listener = sListener;
        }
        if (listener != null) {
            listener.onAppRefreshRateLimitChanged(hz);
        }
    }

    /** Registers the single listener (or clears it with null) and returns the current limit. */
    static synchronized float setListener(Listener listener) {
        sListener = listener;
        return sLimitHz;
    }
}
