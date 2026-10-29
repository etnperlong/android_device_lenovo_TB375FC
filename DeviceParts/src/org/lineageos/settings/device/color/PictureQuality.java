/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

/**
 * Client of the stock MediaTek picture quality service
 * (vendor.mediatek.hardware.pq_aidl.IPictureQuality_AIDL/default), written out by hand like
 * perf/MtkPower: no AIDL definition of it is in the tree. The transaction codes were read from
 * the generated vendor NDK stub (vendor.mediatek.hardware.pq_aidl-V7-ndk.so, AIBinder_transact
 * code of each BpPictureQuality_AIDL method); only methods of the V2 interface stock's
 * libjni_pq.so links are used, and later versions only added methods:
 *
 *  51 Result setColorMatrix3x3(int[3][3] matrix, int step)
 *  57 Result setGammaIndex(int index, int step)
 *  62 Result setRGBGain(int r, int g, int b, int step)
 *  68 Result enableChameleon(boolean enable, int step)
 *  77 Result setFeatureSwitch(PQFeatureID feature, int value)   (PQFeatureID is an int)
 *  87 Result setPQIndex(int level, int scenario, int tuningMode, int index, int step)
 *
 * A fixed-size array is written like a regular one, length first, on every dimension
 * (AParcel_writeParcelableArray of int32 arrays in the stub). Result is an int enum; 0 is OK in
 * the HIDL predecessor's types.hal, so other values are only logged.
 *
 * Not thread safe; ScreenColorService calls it from its handler thread only.
 */
final class PictureQuality {
    private static final String TAG = "PictureQuality";

    private static final String DESCRIPTOR =
            "vendor.mediatek.hardware.pq_aidl.IPictureQuality_AIDL";

    private static final int TRANSACTION_SET_COLOR_MATRIX_3X3 = 51;
    private static final int TRANSACTION_SET_GAMMA_INDEX = 57;
    private static final int TRANSACTION_SET_RGB_GAIN = 62;
    private static final int TRANSACTION_ENABLE_CHAMELEON = 68;
    private static final int TRANSACTION_SET_FEATURE_SWITCH = 77;
    private static final int TRANSACTION_SET_PQ_INDEX = 87;

    /** setPQIndex scenario and tuning mode libjni_pq.so passes for every index. */
    private static final int PQ_INDEX_SCENARIO = 2;
    private static final int PQ_INDEX_TUNING_MODE = 0;

    /** Called on a binder thread when the service dies. */
    interface DeathListener {
        void onServiceDied();
    }

    private final DeathListener mDeathListener;
    private IBinder mService;

    PictureQuality(DeathListener deathListener) {
        mDeathListener = deathListener;
    }

    /** Forgets the service, so the next call looks it up again. */
    void reset() {
        mService = null;
    }

    /** matrix is row-major, 3x3, in the panel's CCORR unit (ColorSettings.UNIT = 1.0). */
    boolean setColorMatrix(int[] matrix, int step) {
        final Parcel data = newCall();
        data.writeInt(3);
        for (int row = 0; row < 3; row++) {
            data.writeInt(3);
            for (int col = 0; col < 3; col++) {
                data.writeInt(matrix[row * 3 + col]);
            }
        }
        data.writeInt(step);
        return call(TRANSACTION_SET_COLOR_MATRIX_3X3, "setColorMatrix3x3", data);
    }

    boolean setGammaIndex(int index, int step) {
        final Parcel data = newCall();
        data.writeInt(index);
        data.writeInt(step);
        return call(TRANSACTION_SET_GAMMA_INDEX, "setGammaIndex", data);
    }

    /** Gains in the panel's unit (ColorSettings.UNIT = 1.0). */
    boolean setRgbGain(int[] gains, int step) {
        final Parcel data = newCall();
        data.writeInt(gains[0]);
        data.writeInt(gains[1]);
        data.writeInt(gains[2]);
        data.writeInt(step);
        return call(TRANSACTION_SET_RGB_GAIN, "setRGBGain", data);
    }

    boolean enableChameleon(boolean enable, int step) {
        final Parcel data = newCall();
        data.writeBoolean(enable);
        data.writeInt(step);
        return call(TRANSACTION_ENABLE_CHAMELEON, "enableChameleon", data);
    }

    /** feature is a PQFeatureID; value 1 turns it on and 0 off, as libjni_pq.so sends. */
    boolean setFeatureSwitch(int feature, int value) {
        final Parcel data = newCall();
        data.writeInt(feature);
        data.writeInt(value);
        return call(TRANSACTION_SET_FEATURE_SWITCH, "setFeatureSwitch(" + feature + ")", data);
    }

    boolean setPqIndex(int index, int level, int step) {
        final Parcel data = newCall();
        data.writeInt(level);
        data.writeInt(PQ_INDEX_SCENARIO);
        data.writeInt(PQ_INDEX_TUNING_MODE);
        data.writeInt(index);
        data.writeInt(step);
        return call(TRANSACTION_SET_PQ_INDEX, "setPQIndex(" + index + ")", data);
    }

    private static Parcel newCall() {
        final Parcel data = Parcel.obtain();
        data.writeInterfaceToken(DESCRIPTOR);
        return data;
    }

    /** Sends data (recycled here); false if the service is not there or the call failed. */
    private boolean call(int code, String name, Parcel data) {
        final Parcel reply = Parcel.obtain();
        try {
            final IBinder service = getService();
            if (service == null) {
                return false;
            }
            service.transact(code, data, reply, 0);
            reply.readException();
            final int result = reply.readInt();
            if (result != 0) {
                Log.w(TAG, name + " returned " + result);
            }
            return true;
        } catch (RemoteException | RuntimeException e) {
            Log.e(TAG, name + " failed", e);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private IBinder getService() {
        if (mService == null) {
            final IBinder binder = ServiceManager.checkService(DESCRIPTOR + "/default");
            if (binder == null) {
                Log.e(TAG, DESCRIPTOR + " is not running");
                return null;
            }
            try {
                binder.linkToDeath(mDeathListener::onServiceDied, 0);
            } catch (RemoteException e) {
                Log.e(TAG, DESCRIPTOR + " died", e);
                return null;
            }
            mService = binder;
        }
        return mService;
    }
}
