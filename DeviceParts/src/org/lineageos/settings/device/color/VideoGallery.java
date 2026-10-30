/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.slider.Slider;

import org.lineageos.settings.device.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The video picture sample clips (R.array.video_picture_samples) as swiped pages, inline on the
 * video picture page and in full screen: each page shows the clip's poster until the clip is
 * played or scrubbed, over it a play / pause button, and below the elapsed time, a position
 * slider, the length and the full screen button (exit in full screen). A tap on the clip shows
 * or hides the controls; while playing they hide by themselves.
 *
 * The clips play with MediaPlayer onto a SurfaceView, so they are decoded by the hardware
 * decoder and reach the composer as a video layer of their own, as in any video app, and the
 * video picture switches change them live. Nothing must make the composer draw that layer with
 * the GPU, which bypasses MediaTek's video processing: no TextureView, and no rounded corners
 * or other effects on the SurfaceView (the card's rounded corners only clip the hole the window
 * leaves for it).
 *
 * Only the current clip has a player: created when it is played or scrubbed, released when
 * another page is shown, when its surface goes away (scrolled off, page left) and before full
 * screen takes over, so there is at most one hardware decoder. Released, the clip keeps its
 * position and shows that frame paused once its surface is back; losing the surface pauses it.
 * Showing another page starts that clip from the beginning. Clips loop.
 */
final class VideoGallery implements GalleryPager.Callback {
    private static final String TAG = "VideoGallery";
    private static final long PROGRESS_INTERVAL_MS = 100;
    private static final long CHROME_TIMEOUT_MS = 3000;
    private static final Executor METADATA = Executors.newSingleThreadExecutor();

    interface Host {
        /** The full screen button was tapped (the exit button in full screen). */
        void onFullScreenClicked();

        /** Now showing clip page. */
        default void onClipChanged(int page) {
        }
    }

    private final Context mContext;
    private final Host mHost;
    private final int[] mClips;
    /** Length of each clip in ms, 0 until known. */
    private final int[] mDurations;
    private final ImageButton mPlay;
    private final View mBar;
    private final Slider mSlider;
    private final TextView mTime;
    private final TextView mDuration;
    private final LinearLayout mDots;
    private final List<View> mExtraChrome = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mProgress = this::onProgress;
    private final Runnable mHideChrome = () -> setChromeShown(false, true);
    private final GalleryPager mPager;

    /** The current clip, its position (ms) and whether it should be playing. */
    private int mIndex;
    private int mPosition;
    private boolean mPlaying;
    /** Whether the clip shows video rather than its poster once it has a surface. */
    private boolean mActive;

    /** The surface of the current page, while it exists. */
    private SurfaceHolder mHolder;
    private MediaPlayer mPlayer;
    private boolean mPrepared;
    private boolean mSeeking;
    /** Where to seek once the seek in progress is done, or -1. */
    private int mPendingSeek = -1;
    /** Whether the current page shows the video instead of its poster. */
    private boolean mVideoShown;
    private boolean mTracking;
    private boolean mChromeShown = true;

    /**
     * root contains R.layout.video_picture_player; fullScreen picks how posters are scaled and
     * makes the full screen button an exit button.
     */
    VideoGallery(View root, boolean fullScreen, Host host) {
        mContext = root.getContext();
        mHost = host;
        mClips = GalleryPager.resourceIds(mContext, R.array.video_picture_samples);
        mDurations = new int[mClips.length];

        mPlay = root.findViewById(R.id.video_picture_play);
        mPlay.setOnClickListener(v -> {
            if (mPlaying) {
                pause();
            } else {
                play();
            }
        });
        mBar = root.findViewById(R.id.video_picture_bar);
        mTime = root.findViewById(R.id.video_picture_time);
        mDuration = root.findViewById(R.id.video_picture_duration);
        mSlider = root.findViewById(R.id.video_picture_slider);
        mSlider.setLabelFormatter(value -> formatTime((int) value));
        mSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                scrub((int) value);
            }
        });
        mSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                mTracking = true;
                mHandler.removeCallbacks(mHideChrome);
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                mTracking = false;
                scheduleHideChrome();
            }
        });
        final ImageButton fullScreenButton = root.findViewById(R.id.video_picture_fullscreen);
        if (fullScreen) {
            fullScreenButton.setImageResource(R.drawable.ic_video_fullscreen_exit);
            fullScreenButton.setContentDescription(
                    mContext.getString(R.string.screen_color_gallery_close));
            final int inset = mContext.getResources().getDimensionPixelSize(
                    R.dimen.video_picture_fullscreen_bar_inset);
            mBar.setPaddingRelative(mBar.getPaddingStart() + inset, mBar.getPaddingTop(),
                    mBar.getPaddingEnd() + inset, mBar.getPaddingBottom() + inset);
        }
        fullScreenButton.setOnClickListener(v -> mHost.onFullScreenClicked());

        mDots = root.findViewById(R.id.video_picture_dots);
        mPager = new GalleryPager(root.findViewById(R.id.video_picture_pager), mDots,
                GalleryPager.resourceIds(mContext, R.array.video_picture_posters),
                R.layout.video_picture_page,
                fullScreen ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP,
                this);
        loadDurations();
        updateControls();
        setChromeShown(true, false);
    }

    int getIndex() {
        return mIndex;
    }

    /** Position of the current clip in ms. */
    int getPosition() {
        if (mPrepared && !mSeeking) {
            mPosition = mPlayer.getCurrentPosition();
        }
        return mPosition;
    }

    boolean isPlaying() {
        return mPlaying;
    }

    /** Shows clip index at position (ms), playing or paused, as full screen or inline left it. */
    void restore(int index, int position, boolean playing) {
        mPager.setCurrent(index);
        closePlayer();
        mPosition = Math.max(0, position);
        mPlaying = playing;
        mActive = playing || position > 0;
        if (mActive) {
            openPlayer();
        }
        updateControls();
        setChromeShown(true, false);
    }

    /** Releases the player, keeping the clip's position, e.g. before full screen takes over. */
    void suspend() {
        getPosition();
        mPlaying = false;
        closePlayer();
        updateControls();
    }

    /** Stops for good: the view is going away. */
    void release() {
        suspend();
        mHandler.removeCallbacksAndMessages(null);
    }

    /** Shows or hides the controls laid over the clip along with them. */
    void addChrome(View view) {
        mExtraChrome.add(view);
        GalleryChrome.show(view, mChromeShown, false);
    }

    @Override
    public void onPageChanged(int page) {
        if (mPager == null || page == mIndex) {
            // Called from the GalleryPager constructor for the first page, already current.
            return;
        }
        closePlayer();
        mIndex = page;
        mPosition = 0;
        mPlaying = false;
        mActive = false;
        mHolder = surfaceOf(mPager.findPage(page));
        updateControls();
        setChromeShown(true, true);
        mHost.onClipChanged(page);
    }

    @Override
    public void onPictureClicked() {
        setChromeShown(!mChromeShown, true);
    }

    @Override
    public void onPageBound(View page, int position) {
        final SurfaceView surface = page.findViewById(R.id.video_picture_surface);
        if (surface.getTag() == null) {
            surface.getHolder().addCallback(new PageSurface(surface));
        }
        surface.setTag(position);
        final SurfaceHolder holder = surface.getHolder();
        if (holder == mHolder && position != mIndex) {
            // The page view of the current clip now shows another one.
            closePlayer();
            mHolder = null;
        } else if (position == mIndex && mHolder == null && hasSurface(holder)) {
            attachSurface(holder);
        }
        page.findViewById(R.id.gallery_page_image).setVisibility(
                holder == mHolder && mVideoShown ? View.INVISIBLE : View.VISIBLE);
    }

    private void attachSurface(SurfaceHolder holder) {
        mHolder = holder;
        if (mActive) {
            openPlayer();
        }
    }

    /** The surface of a page view, if it exists. */
    private static SurfaceHolder surfaceOf(View page) {
        if (page == null) {
            return null;
        }
        final SurfaceHolder holder =
                ((SurfaceView) page.findViewById(R.id.video_picture_surface)).getHolder();
        return hasSurface(holder) ? holder : null;
    }

    private static boolean hasSurface(SurfaceHolder holder) {
        final Surface surface = holder.getSurface();
        return surface != null && surface.isValid();
    }

    private void play() {
        mPlaying = true;
        mActive = true;
        if (mPlayer == null) {
            // Without a surface yet, it starts once the surface exists.
            openPlayer();
        } else if (mPrepared) {
            mPlayer.start();
        }
        updateControls();
        startProgress();
        scheduleHideChrome();
    }

    private void pause() {
        getPosition();
        mPlaying = false;
        if (mPrepared) {
            mPlayer.pause();
        }
        updateControls();
        setChromeShown(true, true);
    }

    /** The slider was moved to position: show that frame, opening the clip if it is not yet. */
    private void scrub(int position) {
        mPosition = position;
        mTime.setText(formatTime(position));
        if (!mActive) {
            mActive = true;
            openPlayer();
        } else {
            seek(position);
        }
    }

    private void openPlayer() {
        if (mPlayer != null || mHolder == null) {
            return;
        }
        final MediaPlayer player = new MediaPlayer();
        try (AssetFileDescriptor clip = mContext.getResources().openRawResourceFd(
                mClips[mIndex])) {
            player.setDataSource(clip);
        } catch (IOException e) {
            Log.e(TAG, "Cannot open clip " + mIndex, e);
            player.release();
            mActive = false;
            mPlaying = false;
            updateControls();
            return;
        }
        player.setDisplay(mHolder);
        player.setScreenOnWhilePlaying(true);
        player.setLooping(true);
        player.setOnPreparedListener(mp -> onPrepared());
        player.setOnSeekCompleteListener(mp -> onSeekComplete());
        player.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                showVideo(true);
            }
            return false;
        });
        player.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "Clip " + mIndex + " failed: " + what + "/" + extra);
            mPlaying = false;
            mActive = false;
            closePlayer();
            updateControls();
            setChromeShown(true, true);
            return true;
        });
        mPlayer = player;
        mPrepared = false;
        player.prepareAsync();
    }

    private void onPrepared() {
        mPrepared = true;
        if (mPlayer.getDuration() > 0) {
            mDurations[mIndex] = mPlayer.getDuration();
        }
        if (mPosition > 0) {
            // Paused, the frame shows once the seek is done; playing, once rendering starts.
            seek(mPosition);
        }
        if (mPlaying) {
            mPlayer.start();
            startProgress();
        }
        updateControls();
    }

    /** Seeks to position (ms) once the player is ready and no other seek is in progress. */
    private void seek(int position) {
        mPosition = position;
        if (!mPrepared) {
            return;
        }
        if (mSeeking) {
            mPendingSeek = position;
            return;
        }
        mSeeking = true;
        mPendingSeek = -1;
        mPlayer.seekTo(position, MediaPlayer.SEEK_CLOSEST);
    }

    private void onSeekComplete() {
        mSeeking = false;
        if (mPendingSeek >= 0) {
            seek(mPendingSeek);
        } else if (!mPlaying) {
            // Paused, the player shows the frame it sought to.
            showVideo(true);
        }
    }

    /** Releases the player, keeping the position, and puts the poster back. */
    private void closePlayer() {
        mHandler.removeCallbacks(mProgress);
        if (mPlayer == null) {
            return;
        }
        getPosition();
        mPlayer.release();
        mPlayer = null;
        mPrepared = false;
        mSeeking = false;
        mPendingSeek = -1;
        showVideo(false);
    }

    /** Shows the video of the current page, or its poster over it. */
    private void showVideo(boolean shown) {
        mVideoShown = shown;
        final View page = mPager.findPage(mIndex);
        if (page != null) {
            page.findViewById(R.id.gallery_page_image).setVisibility(
                    shown ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void startProgress() {
        mHandler.removeCallbacks(mProgress);
        mHandler.post(mProgress);
    }

    private void onProgress() {
        if (!mTracking) {
            getPosition();
            updatePosition();
        }
        if (mPlaying && mPlayer != null) {
            mHandler.postDelayed(mProgress, PROGRESS_INTERVAL_MS);
        }
    }

    private void updateControls() {
        mPlay.setImageResource(mPlaying ? R.drawable.ic_video_pause : R.drawable.ic_video_play);
        mPlay.setContentDescription(mContext.getString(
                mPlaying ? R.string.video_picture_pause : R.string.video_picture_play));
        final int duration = mDurations[mIndex];
        final float valueTo = Math.max(1, duration);
        if (mSlider.getValueTo() != valueTo) {
            // The value has to stay within the range at any time.
            mSlider.setValue(0);
            mSlider.setValueTo(valueTo);
        }
        mSlider.setEnabled(duration > 0);
        mDuration.setText(formatTime(duration));
        updatePosition();
    }

    private void updatePosition() {
        if (!mTracking) {
            mSlider.setValue(Math.min(mPosition, mSlider.getValueTo()));
        }
        mTime.setText(formatTime(mPosition));
    }

    private void setChromeShown(boolean shown, boolean animate) {
        mChromeShown = shown;
        GalleryChrome.show(mPlay, shown, animate);
        GalleryChrome.show(mBar, shown, animate);
        GalleryChrome.show(mDots, shown && mPager != null && mPager.getCount() > 1, animate);
        for (View view : mExtraChrome) {
            GalleryChrome.show(view, shown, animate);
        }
        scheduleHideChrome();
    }

    /** Hides the controls after a while if the clip plays and nobody is using them. */
    private void scheduleHideChrome() {
        mHandler.removeCallbacks(mHideChrome);
        if (mChromeShown && mPlaying && !mTracking) {
            mHandler.postDelayed(mHideChrome, CHROME_TIMEOUT_MS);
        }
    }

    /** Reads the length of every clip off the main thread, for the slider before playing. */
    private void loadDurations() {
        final Resources res = mContext.getResources();
        final int[] clips = mClips;
        METADATA.execute(() -> {
            final int[] durations = new int[clips.length];
            for (int i = 0; i < clips.length; i++) {
                durations[i] = readDuration(res, clips[i]);
            }
            mHandler.post(() -> {
                for (int i = 0; i < durations.length; i++) {
                    if (mDurations[i] == 0) {
                        mDurations[i] = durations[i];
                    }
                }
                updateControls();
            });
        });
    }

    private static int readDuration(Resources res, int clip) {
        try (AssetFileDescriptor file = res.openRawResourceFd(clip);
                MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getFileDescriptor(), file.getStartOffset(),
                    file.getLength());
            final String duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);
            return duration != null ? Integer.parseInt(duration) : 0;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Cannot read the length of " + res.getResourceEntryName(clip), e);
            return 0;
        }
    }

    /** m:ss */
    private static String formatTime(int ms) {
        final int seconds = Math.max(0, ms) / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    /** Hands the surface of a page view to the gallery while that page is the current one. */
    private final class PageSurface implements SurfaceHolder.Callback {
        private final SurfaceView mSurface;

        PageSurface(SurfaceView surface) {
            mSurface = surface;
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            if (mHolder == null && Integer.valueOf(mIndex).equals(mSurface.getTag())) {
                attachSurface(holder);
            }
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            if (holder == mHolder) {
                getPosition();
                mPlaying = false;
                closePlayer();
                mHolder = null;
                updateControls();
                setChromeShown(true, false);
            }
        }
    }
}
