/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.pen;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.util.Locale;

/**
 * Serves the picture of each official Lenovo pen, content://AUTHORITY/<model>.png, which
 * {@link PenDetails} sets as the pen's BluetoothDevice.METADATA_MAIN_ICON.
 *
 * Settings loads it for the device details header and the device list, after taking a
 * persistable read grant (AdvancedBluetoothDetailsHeaderController.loadIcon,
 * BluetoothUtils.getBtDrawableWithDescription); SystemUI reads it without a grant, so the
 * provider is exported. The pictures are public product images and nothing else is served.
 */
public class PenImageProvider extends ContentProvider {
    private static final String AUTHORITY = "org.lineageos.settings.device.pen";
    private static final String SUFFIX = ".png";

    /** The content URI of the picture of a pen model. */
    static Uri uriFor(PenModel model) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(model.name().toLowerCase(Locale.ROOT) + SUFFIX)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only: " + uri);
        }
        final String segment = uri.getLastPathSegment();
        if (segment != null && segment.endsWith(SUFFIX)) {
            final String name =
                    segment.substring(0, segment.length() - SUFFIX.length()).toUpperCase(Locale.ROOT);
            for (PenModel model : PenModel.values()) {
                if (model.name().equals(name)) {
                    // res/raw PNGs are stored uncompressed, so they open as a file descriptor.
                    return getContext().getResources().openRawResourceFd(model.image);
                }
            }
        }
        throw new FileNotFoundException(uri.toString());
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
