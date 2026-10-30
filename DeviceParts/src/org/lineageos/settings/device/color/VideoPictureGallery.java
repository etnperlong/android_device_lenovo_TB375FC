/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

/**
 * The 16:9 player card at the top of the video picture page (see VideoGallery). Full screen
 * continues the clip where it is, and coming back the card continues where full screen left.
 */
public class VideoPictureGallery extends FrameLayout implements VideoGallery.Host {
    private static final int RATIO_WIDTH = 16;
    private static final int RATIO_HEIGHT = 9;
    private static final String RESULT_KEY = "video_picture_gallery";

    private VideoGallery mGallery;
    private ActivityResultLauncher<Intent> mFullScreen;

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
        if (mFullScreen == null) {
            return;
        }
        final Intent intent = new Intent(getContext(), VideoPictureFullScreenActivity.class)
                .putExtra(VideoPictureFullScreenActivity.EXTRA_INDEX, mGallery.getIndex())
                .putExtra(VideoPictureFullScreenActivity.EXTRA_POSITION, mGallery.getPosition())
                .putExtra(VideoPictureFullScreenActivity.EXTRA_PLAYING, mGallery.isPlaying());
        // Full screen opens its own decoder; this one goes first.
        mGallery.suspend();
        mFullScreen.launch(intent);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Registered with a fixed key, so a result for a recreated page still arrives.
        mFullScreen = ScreenColorGallery.findActivity(getContext()).getActivityResultRegistry()
                .register(RESULT_KEY, new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            final Intent data = result.getData();
                            if (data != null) {
                                mGallery.restore(
                                        data.getIntExtra(VideoPictureFullScreenActivity.EXTRA_INDEX,
                                                0),
                                        data.getIntExtra(
                                                VideoPictureFullScreenActivity.EXTRA_POSITION, 0),
                                        data.getBooleanExtra(
                                                VideoPictureFullScreenActivity.EXTRA_PLAYING,
                                                false));
                            }
                        });
    }

    @Override
    protected void onDetachedFromWindow() {
        mFullScreen.unregister();
        mFullScreen = null;
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
