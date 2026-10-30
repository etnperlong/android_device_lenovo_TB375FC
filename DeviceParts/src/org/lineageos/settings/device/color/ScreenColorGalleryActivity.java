/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lineageos.settings.device.R;

/**
 * The screen color sample pictures in full screen, whole and swipeable, with the system bars
 * hidden until swiped in. The close button, the page dots and the credit of the current picture
 * (R.array.screen_color_gallery_credits) are shown until the picture is tapped, which toggles
 * them. The picture shown last is returned to the inline gallery.
 */
public class ScreenColorGalleryActivity extends Activity implements GalleryPager.Callback {
    /** Picture to start with, and the one shown last in the result. */
    static final String EXTRA_INDEX = "index";
    private static final String STATE_CHROME = "chrome";

    private String[] mCredits;
    private View mClose;
    private LinearLayout mDots;
    private TextView mCredit;
    private boolean mChromeShown = true;
    private GalleryPager mPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_color_gallery);

        final WindowInsetsController insets = getWindow().getInsetsController();
        if (insets != null) {
            insets.hide(WindowInsets.Type.systemBars());
            insets.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        mCredits = getResources().getStringArray(R.array.screen_color_gallery_credits);
        mClose = findViewById(R.id.screen_color_gallery_close);
        mClose.setOnClickListener(v -> finish());
        mDots = findViewById(R.id.screen_color_gallery_dots);
        mCredit = findViewById(R.id.screen_color_gallery_credit);
        if (savedInstanceState != null) {
            mChromeShown = savedInstanceState.getBoolean(STATE_CHROME, true);
        }

        mPager = new GalleryPager(findViewById(R.id.screen_color_gallery_pager), mDots,
                GalleryPager.resourceIds(this, R.array.screen_color_gallery), 0,
                ImageView.ScaleType.FIT_CENTER, this);
        mPager.setCurrent(savedInstanceState != null
                ? savedInstanceState.getInt(EXTRA_INDEX)
                : getIntent().getIntExtra(EXTRA_INDEX, 0));
        showChrome(false);
    }

    @Override
    public void onPageChanged(int page) {
        final boolean known = page < mCredits.length && !mCredits[page].isEmpty();
        mCredit.setText(known
                ? getString(R.string.screen_color_gallery_credit, mCredits[page]) : "");
        if (mPager != null) {
            showChrome(false);
        }
    }

    @Override
    public void onPictureClicked() {
        mChromeShown = !mChromeShown;
        showChrome(true);
    }

    /** Shows or hides each control as mChromeShown says, if it has anything to show. */
    private void showChrome(boolean animate) {
        GalleryChrome.show(mClose, mChromeShown, animate);
        GalleryChrome.show(mDots, mChromeShown && mPager.getCount() > 1, animate);
        GalleryChrome.show(mCredit, mChromeShown && mCredit.length() > 0, animate);
    }

    @Override
    public void finish() {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_INDEX, mPager.getCurrent()));
        super.finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_INDEX, mPager.getCurrent());
        outState.putBoolean(STATE_CHROME, mChromeShown);
    }
}
