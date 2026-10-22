/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.perf;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

/**
 * Client of the two stock MediaTek power AIDL services, written out by hand: no AIDL definition
 * of them is in the tree, and both are frozen V1 interfaces whose transactions were read from
 * the generated vendor NDK stubs (vendor.mediatek.hardware.mtkpower-V1-ndk.so and
 * vendor.mediatek.hardware.mtkpower_applist-V1-ndk.so, AIBinder_transact code and flags):
 *
 * IMtkpower_applist
 *   1 oneway notifyAppState(String pack, String act, int pid, int state, int uid)
 * IMtkPowerService
 *   2 int perfLockAcquire(int hdl, int duration, int[] boostList, int pid, int reserved)
 *   3 oneway perfLockRelease(int hdl, int reserved)
 *  10 int setSysInfo(int cmd, String data)
 *
 * Not thread safe; PerformanceService calls it from its handler thread only.
 */
final class MtkPower {
    private static final String TAG = "MtkPower";

    private static final String APPLIST_DESCRIPTOR =
            "vendor.mediatek.hardware.mtkpower_applist.IMtkpower_applist";
    private static final String POWER_DESCRIPTOR =
            "vendor.mediatek.hardware.mtkpower.IMtkPowerService";

    private static final int TRANSACTION_NOTIFY_APP_STATE = IBinder.FIRST_CALL_TRANSACTION;
    private static final int TRANSACTION_PERF_LOCK_ACQUIRE = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int TRANSACTION_PERF_LOCK_RELEASE = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int TRANSACTION_SET_SYS_INFO = IBinder.FIRST_CALL_TRANSACTION + 9;

    /** notifyAppState state values (lib_power_applist logs them as RESUMED / PAUSED). */
    static final int STATE_PAUSED = 0;
    static final int STATE_RESUMED = 1;

    /**
     * setSysInfo commands lib_power_applist sends for the foreground app (notifyNetdUID and
     * notifyPID): its uid and pid, as decimal strings.
     */
    static final int SYS_INFO_FOREGROUND_UID = 22;
    static final int SYS_INFO_FOREGROUND_PID = 24;

    /** Called on a binder thread when one of the services dies. */
    interface DeathListener {
        void onServiceDied();
    }

    private final DeathListener mDeathListener;
    private IBinder mAppList;
    private IBinder mPower;

    MtkPower(DeathListener deathListener) {
        mDeathListener = deathListener;
    }

    /** Forgets both services, so the next call looks them up again. */
    void reset() {
        mAppList = null;
        mPower = null;
    }

    boolean notifyAppState(String pkg, String activity, int pid, int state, int uid) {
        final IBinder binder = getAppList();
        if (binder == null) {
            return false;
        }
        final Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(APPLIST_DESCRIPTOR);
            data.writeString(pkg);
            data.writeString(activity);
            data.writeInt(pid);
            data.writeInt(state);
            data.writeInt(uid);
            binder.transact(TRANSACTION_NOTIFY_APP_STATE, data, null, IBinder.FLAG_ONEWAY);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "notifyAppState failed", e);
            return false;
        } finally {
            data.recycle();
        }
    }

    /** Acquires a lock without timeout; returns its handle, or 0 on failure. */
    int perfLockAcquire(int[] resources, int pid) {
        final IBinder binder = getPower();
        if (binder == null) {
            return 0;
        }
        final Parcel data = Parcel.obtain();
        final Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(POWER_DESCRIPTOR);
            data.writeInt(0); // new lock
            data.writeInt(0); // no timeout
            data.writeIntArray(resources);
            data.writeInt(pid);
            data.writeInt(0);
            binder.transact(TRANSACTION_PERF_LOCK_ACQUIRE, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, "perfLockAcquire failed", e);
            return 0;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    void perfLockRelease(int handle) {
        final IBinder binder = getPower();
        if (binder == null) {
            return;
        }
        final Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(POWER_DESCRIPTOR);
            data.writeInt(handle);
            data.writeInt(0);
            binder.transact(TRANSACTION_PERF_LOCK_RELEASE, data, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException e) {
            Log.e(TAG, "perfLockRelease failed", e);
        } finally {
            data.recycle();
        }
    }

    boolean setSysInfo(int cmd, String value) {
        final IBinder binder = getPower();
        if (binder == null) {
            return false;
        }
        final Parcel data = Parcel.obtain();
        final Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(POWER_DESCRIPTOR);
            data.writeInt(cmd);
            data.writeString(value);
            binder.transact(TRANSACTION_SET_SYS_INFO, data, reply, 0);
            reply.readException();
            return true;
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, "setSysInfo(" + cmd + ") failed", e);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private IBinder getAppList() {
        if (mAppList == null) {
            mAppList = lookUp(APPLIST_DESCRIPTOR + "/default");
        }
        return mAppList;
    }

    private IBinder getPower() {
        if (mPower == null) {
            mPower = lookUp(POWER_DESCRIPTOR + "/default");
        }
        return mPower;
    }

    private IBinder lookUp(String name) {
        final IBinder binder = ServiceManager.checkService(name);
        if (binder == null) {
            Log.e(TAG, name + " is not running");
            return null;
        }
        try {
            binder.linkToDeath(mDeathListener::onServiceDied, 0);
        } catch (RemoteException e) {
            Log.e(TAG, name + " died", e);
            return null;
        }
        return binder;
    }
}
