/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;

import org.lineageos.settings.device.R;

/**
 * The video picture sample clips in full screen, landscape and in a task of its own (out of the
 * Settings split, see GalleryHandoff), with the system bars hidden until swiped in, and who
 * filmed the current clip (R.array.video_picture_credits) shown with the controls. It starts and
 * ends with the clip, position and play state of the inline player.
 */
public class VideoPictureFullScreenActivity extends Activity implements VideoGallery.Host {
    /** Clip, position in ms and play state to start with, and the ones left in the handoff. */
    static final String EXTRA_INDEX = "index";
    static final String EXTRA_POSITION = "position";
    static final String EXTRA_PLAYING = "playing";

    private String[] mCredits;
    private TextView mCredit;
    private VideoGallery mGallery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_picture_fullscreen);

        final WindowInsetsController insets = getWindow().getInsetsController();
        if (insets != null) {
            insets.hide(WindowInsets.Type.systemBars());
            insets.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        mCredits = getResources().getStringArray(R.array.video_picture_credits);
        mCredit = findViewById(R.id.video_picture_credit);
        mGallery = new VideoGallery(findViewById(android.R.id.content), true, this);
        mGallery.addChrome(mCredit);
        final Bundle state = savedInstanceState != null ? savedInstanceState
                : getIntent().getExtras() != null ? getIntent().getExtras() : Bundle.EMPTY;
        mGallery.restore(state.getInt(EXTRA_INDEX), state.getInt(EXTRA_POSITION),
                state.getBoolean(EXTRA_PLAYING));
        onClipChanged(mGallery.getIndex());
    }

    @Override
    public void onClipChanged(int page) {
        mCredit.setText(page < mCredits.length
                ? getString(R.string.video_picture_credit, mCredits[page]) : "");
    }

    @Override
    public void onFullScreenClicked() {
        finish();
    }

    @Override
    public void finish() {
        final Bundle state = new Bundle();
        state.putInt(EXTRA_INDEX, mGallery.getIndex());
        state.putInt(EXTRA_POSITION, mGallery.getPosition());
        state.putBoolean(EXTRA_PLAYING, mGallery.isPlaying());
        GalleryHandoff.leave(VideoPictureFullScreenActivity.class, state);
        // The inline player opens its own decoder as soon as it is back.
        mGallery.release();
        super.finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_INDEX, mGallery.getIndex());
        outState.putInt(EXTRA_POSITION, mGallery.getPosition());
        outState.putBoolean(EXTRA_PLAYING, mGallery.isPlaying());
    }

    @Override
    protected void onDestroy() {
        mGallery.release();
        super.onDestroy();
    }
}
