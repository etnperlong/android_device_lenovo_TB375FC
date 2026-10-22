/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.display;

/**
 * Pure state machine deciding the single max-refresh-rate request this process owns.
 *
 * DisplayManager.requestMaxRefreshRate() is keyed by one binder token per process
 * (DisplayManagerGlobal.mToken), so every limit this app wants must be folded into one value.
 * Votes from other priorities (battery saver, thermal, proximity, UDFPS) still intersect with it
 * inside DisplayModeDirector; this class only decides our own contribution.
 */
final class RefreshRateArbiter {
    /** Value understood by requestMaxRefreshRate() as "remove the restriction". */
    static final float NO_LIMIT = 0f;

    private final float mStylusCapHz;
    private final float mReadingCapHz;

    private boolean mScreenOn = true;
    private boolean mStylusActive;
    private boolean mReadingMode;
    private boolean mReadingLowRefresh;
    private float mUserMinHz;
    private float mAppCapHz = NO_LIMIT;

    RefreshRateArbiter(float stylusCapHz, float readingCapHz) {
        mStylusCapHz = stylusCapHz;
        mReadingCapHz = readingCapHz;
    }

    void setScreenOn(boolean on) {
        mScreenOn = on;
        if (!on) {
            // No stylus events can arrive while the panel is off; never carry a stale
            // stylus limit across a sleep/wake cycle.
            mStylusActive = false;
        }
    }

    void setStylusActive(boolean active) {
        mStylusActive = active && mScreenOn;
    }

    void setReadingMode(boolean readingMode, boolean lowRefreshEnabled) {
        mReadingMode = readingMode;
        mReadingLowRefresh = lowRefreshEnabled;
    }

    /** Settings.System.MIN_REFRESH_RATE; may be 0 (unset) or +Infinity ("highest"). */
    void setUserMinHz(float minHz) {
        mUserMinHz = minHz;
    }

    /** Limit of the foreground app's performance profile, or {@link #NO_LIMIT}. */
    void setAppCapHz(float hz) {
        mAppCapHz = hz;
    }

    boolean isStylusActive() {
        return mStylusActive;
    }

    /** Returns the value to pass to requestMaxRefreshRate(), or {@link #NO_LIMIT}. */
    float computeCapHz() {
        if (!mScreenOn) {
            return NO_LIMIT;
        }
        float cap = Float.POSITIVE_INFINITY;
        if (mStylusActive) {
            cap = Math.min(cap, mStylusCapHz);
        }
        if (mReadingMode && mReadingLowRefresh) {
            // Stock ZUI votes render [0, max(min_refresh_rate, reading_mode_require_fps)].
            cap = Math.min(cap, Math.max(mUserMinHz, mReadingCapHz));
        }
        if (mAppCapHz > 0f) {
            // An explicit per-app choice, so unlike reading mode it is not raised to the user's
            // minimum refresh rate.
            cap = Math.min(cap, mAppCapHz);
        }
        return Float.isInfinite(cap) ? NO_LIMIT : cap;
    }
}
