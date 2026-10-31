/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.lineageos.settings.device.R;

/**
 * The 16:9 picture card at the top of the screen color page: the sample pictures to swipe
 * through, the page dots and a button that shows them in full screen. Coming back from full
 * screen, the card shows the picture that was shown there last (see GalleryHandoff).
 */
public class ScreenColorGallery extends FrameLayout {
    private static final int RATIO_WIDTH = 16;
    private static final int RATIO_HEIGHT = 9;

    private GalleryPager mPager;

    public ScreenColorGallery(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPager = new GalleryPager(findViewById(R.id.screen_color_gallery_pager),
                findViewById(R.id.screen_color_gallery_dots),
                GalleryPager.resourceIds(getContext(), R.array.screen_color_gallery), 0,
                ImageView.ScaleType.CENTER_CROP, null);
        findViewById(R.id.screen_color_gallery_fullscreen).setOnClickListener(v -> {
            final Bundle extras = new Bundle();
            extras.putInt(ScreenColorGalleryActivity.EXTRA_INDEX, mPager.getCurrent());
            GalleryHandoff.launch(getContext(), ScreenColorGalleryActivity.class, extras);
        });
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            final Bundle state = GalleryHandoff.take(ScreenColorGalleryActivity.class);
            if (state != null) {
                mPager.setCurrent(state.getInt(ScreenColorGalleryActivity.EXTRA_INDEX));
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                width * RATIO_HEIGHT / RATIO_WIDTH, MeasureSpec.EXACTLY));
    }
}
