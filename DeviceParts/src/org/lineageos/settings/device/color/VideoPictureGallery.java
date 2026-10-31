/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * The 16:9 player card at the top of the video picture page (see VideoGallery). Full screen
 * continues the clip where it is, and coming back the card continues where full screen left
 * (see GalleryHandoff).
 */
public class VideoPictureGallery extends FrameLayout implements VideoGallery.Host {
    private static final int RATIO_WIDTH = 16;
    private static final int RATIO_HEIGHT = 9;

    private VideoGallery mGallery;

    public VideoPictureGallery(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mGallery = new VideoGallery(this, false, this);
    }

    @Override
    public void onFullScreenClicked() {
        final Bundle extras = new Bundle();
        extras.putInt(VideoPictureFullScreenActivity.EXTRA_INDEX, mGallery.getIndex());
        extras.putInt(VideoPictureFullScreenActivity.EXTRA_POSITION, mGallery.getPosition());
        extras.putBoolean(VideoPictureFullScreenActivity.EXTRA_PLAYING, mGallery.isPlaying());
        // Full screen opens its own decoder; this one goes first.
        mGallery.suspend();
        GalleryHandoff.launch(getContext(), VideoPictureFullScreenActivity.class, extras);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            final Bundle state = GalleryHandoff.take(VideoPictureFullScreenActivity.class);
            if (state != null) {
                mGallery.restore(state.getInt(VideoPictureFullScreenActivity.EXTRA_INDEX),
                        state.getInt(VideoPictureFullScreenActivity.EXTRA_POSITION),
                        state.getBoolean(VideoPictureFullScreenActivity.EXTRA_PLAYING));
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mGallery.release();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                width * RATIO_HEIGHT / RATIO_WIDTH, MeasureSpec.EXACTLY));
    }
}
