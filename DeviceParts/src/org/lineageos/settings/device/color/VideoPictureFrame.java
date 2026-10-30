/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * The largest 16:9 box within the space given, for a sample clip and its poster: the clips are
 * 16:9, and a SurfaceView stretches the video to its own shape.
 */
public class VideoPictureFrame extends FrameLayout {
    private static final int RATIO_WIDTH = 16;
    private static final int RATIO_HEIGHT = 9;

    public VideoPictureFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (width * RATIO_HEIGHT > height * RATIO_WIDTH) {
            width = height * RATIO_WIDTH / RATIO_HEIGHT;
        } else {
            height = width * RATIO_HEIGHT / RATIO_WIDTH;
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
