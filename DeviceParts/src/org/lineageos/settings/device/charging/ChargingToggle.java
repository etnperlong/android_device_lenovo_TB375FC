/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.charging;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import vendor.lineage.health.IChargingControl;

/**
 * The charging on/off toggle of the LineageOS health HAL (vendor.lineage.health-service.tb375fc,
 * /sys/class/wt-battery/charging_enabled). Off stops charging the battery while the adapter
 * keeps powering the tablet, which is bypass charging. The framework's charging control uses
 * the HAL's limit instead, a separate setting of the charger driver, so the two do not undo
 * each other.
 *
 * Not thread safe; ChargingService calls it from its handler thread only.
 */
final class ChargingToggle {
    private static final String TAG = "ChargingToggle";
    private static final String INSTANCE = IChargingControl.DESCRIPTOR + "/default";

    /** Called on a binder thread when the HAL dies. */
    interface DeathListener {
        void onServiceDied();
    }

    private final DeathListener mDeathListener;
    private IChargingControl mService;

    ChargingToggle(DeathListener deathListener) {
        mDeathListener = deathListener;
    }

    /** Forgets the service, so the next call looks it up again. */
    void reset() {
        mService = null;
    }

    boolean setChargingEnabled(boolean enabled) {
        final IChargingControl service = getService();
        if (service == null) {
            return false;
        }
        try {
            service.setChargingEnabled(enabled);
            return true;
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, "setChargingEnabled(" + enabled + ") failed", e);
            return false;
        }
    }

    private IChargingControl getService() {
        if (mService != null) {
            return mService;
        }
        if (!ServiceManager.isDeclared(INSTANCE)) {
            Log.e(TAG, INSTANCE + " is not declared");
            return null;
        }
        final IBinder binder = ServiceManager.waitForDeclaredService(INSTANCE);
        if (binder == null) {
            return null;
        }
        try {
            binder.linkToDeath(mDeathListener::onServiceDied, 0);
        } catch (RemoteException e) {
            // Already dead; the next call tries again.
            return null;
        }
        mService = IChargingControl.Stub.asInterface(binder);
        return mService;
    }
}
