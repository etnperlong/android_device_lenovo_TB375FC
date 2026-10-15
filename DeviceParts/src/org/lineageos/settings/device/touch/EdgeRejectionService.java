/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.touch;

import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Tells the Novatek touch controller which way the screen is rotated, so its edge rejection
 * (ignoring touches near the edges the hands hold) follows the orientation.
 *
 * Stock ZUI's EdgeInhibitionInputPolicy passes the default display rotation (Surface.ROTATION_*,
 * 0..3) unchanged to the Lenovo touchscreen HAL, which writes it to /proc/panel_direction.
 * nt36532.ko (nvt_edge_reject_store/_set) accepts 0..3 and sends one controller command per
 * value; it re-sends the last value on resume, so only rotation changes need writing.
 */
public class EdgeRejectionService extends Service {
    private static final String TAG = "EdgeRejection";
    private static final String PANEL_DIRECTION_PATH = "/proc/panel_direction";

    private DisplayManager mDisplayManager;
    private int mWrittenRotation = -1;

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
                        update();
                    }
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        mDisplayManager = getSystemService(DisplayManager.class);
        mDisplayManager.registerDisplayListener(mDisplayListener,
                new Handler(Looper.getMainLooper()));
        update();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void update() {
        final Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return;
        }
        final int rotation = display.getRotation();
        if (rotation == mWrittenRotation) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(PANEL_DIRECTION_PATH)) {
            out.write('0' + rotation);
            mWrittenRotation = rotation;
            Log.d(TAG, "panel_direction=" + rotation);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write " + PANEL_DIRECTION_PATH, e);
        }
    }
}
