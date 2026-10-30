/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.lineageos.settings.device.R;

/**
 * The 16:9 picture card at the top of the screen color page: the sample pictures to swipe
 * through, the page dots and a button that shows them in full screen. Coming back from full
 * screen, the card shows the picture that was shown there last.
 */
public class ScreenColorGallery extends FrameLayout {
    private static final int RATIO_WIDTH = 16;
    private static final int RATIO_HEIGHT = 9;
    private static final String RESULT_KEY = "screen_color_gallery";

    private GalleryPager mPager;
    private ActivityResultLauncher<Intent> mFullScreen;

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
            if (mFullScreen != null) {
                mFullScreen.launch(new Intent(getContext(), ScreenColorGalleryActivity.class)
                        .putExtra(ScreenColorGalleryActivity.EXTRA_INDEX, mPager.getCurrent()));
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Registered with a fixed key, so a result for a recreated page still arrives.
        mFullScreen = findActivity(getContext()).getActivityResultRegistry().register(
                RESULT_KEY, new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getData() != null) {
                        mPager.setCurrent(result.getData().getIntExtra(
                                ScreenColorGalleryActivity.EXTRA_INDEX, mPager.getCurrent()));
                    }
                });
    }

    @Override
    protected void onDetachedFromWindow() {
        mFullScreen.unregister();
        mFullScreen = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                width * RATIO_HEIGHT / RATIO_WIDTH, MeasureSpec.EXACTLY));
    }

    /** The activity a view's context belongs to. */
    static ComponentActivity findActivity(Context context) {
        while (!(context instanceof ComponentActivity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return (ComponentActivity) context;
    }
}
