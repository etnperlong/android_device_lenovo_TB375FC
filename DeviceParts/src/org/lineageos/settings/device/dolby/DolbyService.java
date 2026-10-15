/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

/**
 * Applies the Dolby settings chosen in DolbyFragment to the DAX effect.
 *
 * The mode is written to daxService's Global profile key, the path stock ZUI's AudioService
 * uses; daxService applies it and saves it. The Music and Movie parameters go to the effect
 * directly, for their own profile so they apply whenever that profile is active. They are
 * re-applied at boot and after audioserver restarts because daxService restores only part of
 * them (not the dialogue enhancer switch). On first start the Secure keys are filled from the
 * effect so the screens show the tuning defaults.
 */
public class DolbyService extends Service {
    private static final String TAG = "DolbyService";

    private HandlerThread mThread;
    private Handler mHandler;
    private ContentResolver mResolver;
    private AudioManager mAudioManager;

    private ContentObserver mObserver;

    private final AudioManager.AudioServerStateCallback mAudioServerCallback =
            new AudioManager.AudioServerStateCallback() {
                @Override
                public void onAudioServerUp() {
                    applyAll();
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mResolver = getContentResolver();
        mAudioManager = getSystemService(AudioManager.class);

        mObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                onSettingChanged(uri);
            }
        };
        mHandler.post(() -> {
            applyAll();
            for (String key : new String[] {DolbySettings.KEY_PROFILE,
                    DolbySettings.KEY_IEQ_PRESET, DolbySettings.KEY_GEQ_GAINS,
                    DolbySettings.KEY_DIALOGUE_LEVEL}) {
                mResolver.registerContentObserver(
                        Settings.Secure.getUriFor(key), false, mObserver);
            }
        });
        mAudioManager.setAudioServerStateCallback(mHandler::post, mAudioServerCallback);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mAudioManager.clearAudioServerStateCallback();
        mResolver.unregisterContentObserver(mObserver);
        mThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onSettingChanged(Uri uri) {
        final String key = uri.getLastPathSegment();
        if (DolbySettings.KEY_PROFILE.equals(key)) {
            updateProfile();
            return;
        }
        final DolbyEffect effect = DolbyEffect.open();
        if (effect == null) {
            return;
        }
        try {
            if (DolbySettings.KEY_IEQ_PRESET.equals(key)) {
                applyIeqPreset(effect);
            } else if (DolbySettings.KEY_GEQ_GAINS.equals(key)) {
                applyGeqGains(effect);
            } else if (DolbySettings.KEY_DIALOGUE_LEVEL.equals(key)) {
                applyDialogueLevel(effect);
            }
        } finally {
            effect.release();
        }
    }

    private void applyAll() {
        final DolbyEffect effect = DolbyEffect.open();
        if (effect != null) {
            try {
                seedFromEffect(effect);
                applyIeqPreset(effect);
                applyGeqGains(effect);
                applyDialogueLevel(effect);
            } finally {
                effect.release();
            }
        }
        updateProfile();
    }

    private void updateProfile() {
        final int profile = DolbySettings.getProfile(mResolver);
        if (Settings.Global.getInt(mResolver, DolbySettings.GLOBAL_DAP_PROFILE, -1) != profile) {
            Settings.Global.putInt(mResolver, DolbySettings.GLOBAL_DAP_PROFILE, profile);
            Log.d(TAG, "profile " + profile);
        }
    }

    /** Fills unset keys from the effect, i.e. from dax-default.xml or daxService's last state. */
    private void seedFromEffect(DolbyEffect effect) {
        try {
            if (Settings.Secure.getString(mResolver, DolbySettings.KEY_PROFILE) == null) {
                Settings.Secure.putInt(mResolver, DolbySettings.KEY_PROFILE,
                        DolbySettings.sanitizeProfile(Settings.Global.getInt(mResolver,
                                DolbySettings.GLOBAL_DAP_PROFILE,
                                DolbySettings.PROFILE_DYNAMIC)));
            }
            if (Settings.Secure.getString(mResolver, DolbySettings.KEY_IEQ_PRESET) == null) {
                // daxService saves the speaker port value, so read that one.
                final int preset = effect.getForPort(DolbySettings.PROFILE_MUSIC,
                        DolbyEffect.PORT_INTERNAL_SPEAKER, DolbyEffect.PARAM_IEQ_PRESET, 1)[0];
                Settings.Secure.putInt(mResolver, DolbySettings.KEY_IEQ_PRESET,
                        DolbySettings.clamp(preset, 0, DolbySettings.IEQ_PRESET_MAX));
            }
            if (Settings.Secure.getString(mResolver, DolbySettings.KEY_GEQ_GAINS) == null) {
                DolbySettings.putGeqGains(mResolver, DolbySettings.fromDapGeqGains(
                        effect.get(DolbySettings.PROFILE_MUSIC, DolbyEffect.PARAM_GEQ_BAND_GAINS,
                                DolbySettings.DAP_GEQ_BANDS)));
            }
            if (Settings.Secure.getString(mResolver, DolbySettings.KEY_DIALOGUE_LEVEL) == null) {
                final boolean enabled = effect.get(DolbySettings.PROFILE_MOVIE,
                        DolbyEffect.PARAM_DIALOG_ENHANCER_ENABLE, 1)[0] != 0;
                final int amount = effect.get(DolbySettings.PROFILE_MOVIE,
                        DolbyEffect.PARAM_DIALOG_ENHANCER_AMOUNT, 1)[0];
                Settings.Secure.putInt(mResolver, DolbySettings.KEY_DIALOGUE_LEVEL, enabled
                        ? DolbySettings.clamp(amount, 1, DolbySettings.DIALOGUE_MAX) : 0);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to read the Dolby defaults", e);
        }
    }

    /**
     * Stock ZUI Settings sets the preset for the profile; daxService saves and restores the
     * speaker and headphone port values, so set all three.
     */
    private void applyIeqPreset(DolbyEffect effect) {
        final int[] preset = {DolbySettings.getIeqPreset(mResolver)};
        try {
            effect.set(DolbySettings.PROFILE_MUSIC, DolbyEffect.PARAM_IEQ_PRESET, preset);
            effect.setForPort(DolbySettings.PROFILE_MUSIC, DolbyEffect.PORT_INTERNAL_SPEAKER,
                    DolbyEffect.PARAM_IEQ_PRESET, preset);
            effect.setForPort(DolbySettings.PROFILE_MUSIC, DolbyEffect.PORT_HEADPHONE,
                    DolbyEffect.PARAM_IEQ_PRESET, preset);
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to set the equalizer preset", e);
        }
    }

    private void applyGeqGains(DolbyEffect effect) {
        try {
            effect.set(DolbySettings.PROFILE_MUSIC, DolbyEffect.PARAM_GEQ_BAND_GAINS,
                    DolbySettings.toDapGeqGains(DolbySettings.getGeqGains(mResolver)));
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to set the graphic equalizer", e);
        }
    }

    /** Stock: level 0 turns the enhancer off and keeps the amount. */
    private void applyDialogueLevel(DolbyEffect effect) {
        final int level = DolbySettings.getDialogueLevel(mResolver);
        try {
            effect.set(DolbySettings.PROFILE_MOVIE, DolbyEffect.PARAM_DIALOG_ENHANCER_ENABLE,
                    new int[] {level > 0 ? 1 : 0});
            if (level > 0) {
                effect.set(DolbySettings.PROFILE_MOVIE, DolbyEffect.PARAM_DIALOG_ENHANCER_AMOUNT,
                        new int[] {level});
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to set the dialogue enhancer", e);
        }
    }
}
