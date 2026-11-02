/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.display;

/**
 * Whether an official Lenovo pen is connected over Bluetooth, handed from PenService to
 * DisplayPolicyService.
 *
 * The NVT controller does not report the active pen at all at 144 Hz, so a cap that waits for
 * stylus MotionEvents never engages there; the Bluetooth link is the only pen signal that does not
 * depend on the touch scan. Both services run in this process; the last value is kept so the
 * order they start in does not matter.
 */
public final class PenConnectionState {
    /** Receives the new connection state. */
    interface Listener {
        void onPenConnectionChanged(boolean connected);
    }

    private static boolean sConnected;
    private static Listener sListener;

    private PenConnectionState() {
    }

    /** Sets whether at least one Lenovo pen is connected. */
    public static void set(boolean connected) {
        final Listener listener;
        synchronized (PenConnectionState.class) {
            if (connected == sConnected) {
                return;
            }
            sConnected = connected;
            listener = sListener;
        }
        if (listener != null) {
            listener.onPenConnectionChanged(connected);
        }
    }

    /** Registers the single listener (or clears it with null) and returns the current state. */
    static synchronized boolean setListener(Listener listener) {
        sListener = listener;
        return sConnected;
    }
}
