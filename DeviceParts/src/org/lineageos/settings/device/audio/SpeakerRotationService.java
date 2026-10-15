/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.audio;

import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

/**
 * Keeps the four-speaker channel layout in step with the display rotation.
 *
 * The MediaTek audio HAL (AudioALSAHardware::setParameters) reads "gsensor_rotation" and passes
 * it to the Awinic amplifier driver (aw_spin_set_mode), which remaps the speaker channels. It does
 * not handle AOSP's "rotation" parameter. Stock ZUI's AudioService (LgsiFeatures
 * ZuiChannelRotation) sends "gsensor_rotation" on every display rotation and after audioserver
 * restarts, with the display rotation shifted by 270 degrees; this service does the same.
 */
public class SpeakerRotationService extends Service {
    private static final String TAG = "SpeakerRotation";

    private HandlerThread mThread;
    private Handler mHandler;
    private DisplayManager mDisplayManager;
    private AudioManager mAudioManager;
    private int mSentDegrees = -1;

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        update(false);
                    }
                }
            };

    private final AudioManager.AudioServerStateCallback mAudioServerCallback =
            new AudioManager.AudioServerStateCallback() {
                @Override
                public void onAudioServerUp() {
                    // A restarted HAL starts from its default channel layout.
                    mHandler.post(() -> update(true));
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mDisplayManager = getSystemService(DisplayManager.class);
        mAudioManager = getSystemService(AudioManager.class);
        mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
        mAudioManager.setAudioServerStateCallback(mHandler::post, mAudioServerCallback);
        mHandler.post(() -> update(true));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        mAudioManager.clearAudioServerStateCallback();
        mThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void update(boolean force) {
        final Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return;
        }
        final int degrees = toHalDegrees(display.getRotation());
        if (!force && degrees == mSentDegrees) {
            return;
        }
        mAudioManager.setParameters("gsensor_rotation=" + degrees);
        mSentDegrees = degrees;
        Log.d(TAG, "gsensor_rotation=" + degrees);
    }

    /** Stock mapping: display rotation 0/90/180/270 -> 270/0/90/180. */
    static int toHalDegrees(int surfaceRotation) {
        switch (surfaceRotation) {
            case Surface.ROTATION_90:
                return 0;
            case Surface.ROTATION_180:
                return 90;
            case Surface.ROTATION_270:
                return 180;
            case Surface.ROTATION_0:
            default:
                return 270;
        }
    }
}
