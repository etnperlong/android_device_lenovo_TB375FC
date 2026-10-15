/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.cover;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

/**
 * Feeds lid switch changes to {@link CoverPolicy}. PhoneWindowManager sends
 * lineageos.intent.action.LID_STATE_CHANGED from applyLidSwitchState() on every lid change.
 */
public class CoverService extends Service {
    // WindowManagerFuncs.LID_ABSENT / LID_CLOSED / LID_OPEN
    private static final int LID_ABSENT = -1;
    private static final int LID_CLOSED = 0;
    private static final int LID_OPEN = 1;

    private HandlerThread mThread;
    private BroadcastReceiver mLidReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread("SmartCover");
        mThread.start();
        final Handler handler = new Handler(mThread.getLooper());
        final CoverPolicy policy = new CoverPolicy(this, handler);
        mLidReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final int state = intent.getIntExtra(
                        lineageos.content.Intent.EXTRA_LID_STATE, LID_ABSENT);
                if (state == LID_CLOSED || state == LID_OPEN) {
                    policy.onLidChanged(state == LID_CLOSED);
                }
            }
        };
        registerReceiver(mLidReceiver,
                new IntentFilter(lineageos.content.Intent.ACTION_LID_STATE_CHANGED), null, handler,
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mLidReceiver);
        mThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
