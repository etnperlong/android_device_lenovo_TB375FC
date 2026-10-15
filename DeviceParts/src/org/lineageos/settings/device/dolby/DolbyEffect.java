/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import android.media.audiofx.AudioEffect;
import android.util.Log;

import java.util.UUID;

/**
 * Minimal client for the Dolby Audio Processing (DAX3) effect on the global output mix.
 *
 * The parameter layout is the one of com.dolby.dax.DolbyAudioEffect inside the vendor daxService
 * APK, which is not a shared library: every request is AudioEffect parameter 5 with a
 * little-endian int32 payload.
 *   set (profile):       [0x01000000, n + 1, profile, param, values[n]]
 *   set (profile, port): [0x02000000, n + 1, profile, port, param, values[n]]
 *   get (profile):       parameter 0x01000005 + (param << 16) + (profile << 8)
 *   get (profile, port): parameter 0x02000005 + (param << 16) + (profile << 12) + (port << 8)
 * Each instance is short-lived, like the stock ZUI Settings controller: open, apply, release.
 */
final class DolbyEffect {
    private static final String TAG = "DolbyEffect";

    /** Implementation UUID registered by audio_effects.xml for libswdap.so. */
    private static final UUID EFFECT_UUID_DAP =
            UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa");

    /** Same priority as daxService's own short-lived instances. */
    private static final int PRIORITY = 1;

    private static final int PARAM = 5;
    private static final int CMD_PROFILE = 0x01000000;
    private static final int CMD_PROFILE_PORT = 0x02000000;

    // com.dolby.dax.DsParams ids.
    static final int PARAM_IEQ_PRESET = 104;
    static final int PARAM_DIALOG_ENHANCER_ENABLE = 105;
    static final int PARAM_DIALOG_ENHANCER_AMOUNT = 108;
    static final int PARAM_GEQ_BAND_GAINS = 110;

    // com.dolby.dax.DsTuning ids.
    static final int PORT_INTERNAL_SPEAKER = 0;
    static final int PORT_HEADPHONE = 3;

    private final AudioEffect mEffect;

    private DolbyEffect(AudioEffect effect) {
        mEffect = effect;
    }

    /** Returns null if the effect cannot be created, e.g. while audioserver restarts. */
    static DolbyEffect open() {
        try {
            return new DolbyEffect(
                    new AudioEffect(AudioEffect.EFFECT_TYPE_NULL, EFFECT_UUID_DAP, PRIORITY, 0));
        } catch (RuntimeException e) {
            Log.w(TAG, "Dolby audio processing is unavailable", e);
            return null;
        }
    }

    void release() {
        mEffect.release();
    }

    void set(int profile, int param, int[] values) {
        check(mEffect.setParameter(PARAM, setPayload(profile, param, values)), "set", param);
    }

    void setForPort(int profile, int port, int param, int[] values) {
        check(mEffect.setParameter(PARAM, setPayload(profile, port, param, values)), "set", param);
    }

    int[] get(int profile, int param, int count) {
        final byte[] reply = new byte[(count + 2) * 4];
        check(mEffect.getParameter(getRequest(profile, param), reply), "get", param);
        return getInts(reply, count);
    }

    int[] getForPort(int profile, int port, int param, int count) {
        final byte[] reply = new byte[(count + 2) * 4];
        check(mEffect.getParameter(getRequest(profile, port, param), reply), "get", param);
        return getInts(reply, count);
    }

    static byte[] setPayload(int profile, int param, int[] values) {
        final byte[] payload = new byte[(values.length + 4) * 4];
        final int offset = putInts(payload, 0, CMD_PROFILE, values.length + 1, profile, param);
        putInts(payload, offset, values);
        return payload;
    }

    static byte[] setPayload(int profile, int port, int param, int[] values) {
        final byte[] payload = new byte[(values.length + 5) * 4];
        final int offset =
                putInts(payload, 0, CMD_PROFILE_PORT, values.length + 1, profile, port, param);
        putInts(payload, offset, values);
        return payload;
    }

    static int getRequest(int profile, int param) {
        return CMD_PROFILE + PARAM + (param << 16) + (profile << 8);
    }

    static int getRequest(int profile, int port, int param) {
        return CMD_PROFILE_PORT + PARAM + (param << 16) + ((profile << 12) | (port << 8));
    }

    static int putInts(byte[] buffer, int offset, int... values) {
        for (int value : values) {
            buffer[offset++] = (byte) value;
            buffer[offset++] = (byte) (value >>> 8);
            buffer[offset++] = (byte) (value >>> 16);
            buffer[offset++] = (byte) (value >>> 24);
        }
        return offset;
    }

    static int[] getInts(byte[] buffer, int count) {
        final int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            final int o = i * 4;
            values[i] = (buffer[o] & 0xff) | (buffer[o + 1] & 0xff) << 8
                    | (buffer[o + 2] & 0xff) << 16 | (buffer[o + 3] & 0xff) << 24;
        }
        return values;
    }

    private static void check(int status, String op, int param) {
        if (status < 0) {
            throw new IllegalStateException(op + " param " + param + " failed: " + status);
        }
    }
}
