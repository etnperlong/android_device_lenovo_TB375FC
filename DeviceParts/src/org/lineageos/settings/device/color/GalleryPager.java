/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.util.Log;
import android.util.LruCache;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.ArrayRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.lineageos.settings.device.R;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Pictures as horizontally swiped pages, one per page, with a row of dots for the current one,
 * always shown when there is more than one picture: the screen color sample pictures, inline
 * and in full screen, and the posters of the video picture sample clips.
 *
 * Pictures are decoded off the main thread, at the size they are shown at (never larger than
 * the file): the inline card needs far fewer pixels than the 2944 × 1840 files. The last few
 * decoded pictures are kept, so swiping back does not decode again.
 */
final class GalleryPager {
    private static final String TAG = "GalleryPager";
    /** Pictures kept decoded per pager: the current one and its neighbours. */
    private static final int CACHED_PICTURES = 3;
    private static final Executor DECODER = Executors.newSingleThreadExecutor();

    interface Callback {
        void onPageChanged(int page);

        void onPictureClicked();

        /** The page view now shows picture position (it may have shown another before). */
        default void onPageBound(View page, int position) {
        }
    }

    private final int[] mImages;
    @LayoutRes
    private final int mPageLayout;
    private final ImageView.ScaleType mScaleType;
    private final RecyclerView mPager;
    private final LinearLayout mDots;
    private final LinearLayoutManager mLayout;
    private final Callback mCallback;
    private final LruCache<Integer, Bitmap> mDecoded = new LruCache<>(CACHED_PICTURES);
    private int mCurrent;

    /**
     * images are drawable resources (see resourceIds). Each page is a plain ImageView, or with
     * a pageLayout other than 0, that layout with the picture in R.id.gallery_page_image.
     * scaleType is CENTER_CROP or FIT_CENTER; callback may be null.
     */
    GalleryPager(RecyclerView pager, LinearLayout dots, int[] images, @LayoutRes int pageLayout,
            ImageView.ScaleType scaleType, Callback callback) {
        final Context context = pager.getContext();
        mImages = images;
        mPageLayout = pageLayout;
        mScaleType = scaleType;
        mPager = pager;
        mDots = dots;
        mCallback = callback;
        mLayout = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        pager.setLayoutManager(mLayout);
        pager.setAdapter(new Adapter());
        final PagerSnapHelper snap = new PagerSnapHelper();
        snap.attachToRecyclerView(pager);
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    final View page = snap.findSnapView(mLayout);
                    if (page != null) {
                        showCurrent(mLayout.getPosition(page));
                    }
                }
            }
        });

        dots.setVisibility(mImages.length > 1 ? View.VISIBLE : View.GONE);
        final int size = context.getResources().getDimensionPixelSize(
                R.dimen.screen_color_gallery_dot_size);
        for (int i = 0; i < mImages.length; i++) {
            final ImageView dot = new ImageView(context);
            dot.setImageResource(R.drawable.screen_color_gallery_dot);
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginStart(i == 0 ? 0 : size);
            dots.addView(dot, params);
        }
        showCurrent(0);
    }

    /** The resource ids in a typed array resource, in order. */
    static int[] resourceIds(Context context, @ArrayRes int arrayRes) {
        final TypedArray array = context.getResources().obtainTypedArray(arrayRes);
        final int[] ids = new int[array.length()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = array.getResourceId(i, 0);
        }
        array.recycle();
        return ids;
    }

    int getCurrent() {
        return mCurrent;
    }

    int getCount() {
        return mImages.length;
    }

    void setCurrent(int index) {
        final int page = Math.max(0, Math.min(index, mImages.length - 1));
        mLayout.scrollToPosition(page);
        showCurrent(page);
    }

    /** The view of page position, or null while it is not laid out. */
    View findPage(int position) {
        return mLayout.findViewByPosition(position);
    }

    private void showCurrent(int page) {
        mCurrent = page;
        for (int i = 0; i < mDots.getChildCount(); i++) {
            mDots.getChildAt(i).setAlpha(i == page ? 1f : 0.45f);
        }
        if (mCallback != null) {
            mCallback.onPageChanged(page);
        }
    }

    /** Shows picture position in image, decoding it first unless it is cached. */
    private void load(ImageView image, int position) {
        final Bitmap cached = mDecoded.get(position);
        if (cached != null) {
            image.setImageBitmap(cached);
            return;
        }
        image.setImageDrawable(null);
        final int width = mPager.getWidth();
        final int height = mPager.getHeight();
        if (width == 0 || height == 0) {
            // Not laid out yet; the page size is the pager's.
            mPager.post(() -> {
                if (isShowing(image, position)) {
                    load(image, position);
                }
            });
            return;
        }
        final Resources res = image.getResources();
        final int resId = mImages[position];
        DECODER.execute(() -> {
            final Bitmap bitmap = decode(res, resId, width, height);
            if (bitmap == null) {
                return;
            }
            image.post(() -> {
                mDecoded.put(position, bitmap);
                if (isShowing(image, position)) {
                    image.setImageBitmap(bitmap);
                }
            });
        });
    }

    /** Whether the recycled page view is still bound to that picture. */
    private static boolean isShowing(ImageView image, int position) {
        return image.getTag() instanceof Integer && (Integer) image.getTag() == position;
    }

    /** The picture scaled down, as the scale type will show it in width × height. */
    private Bitmap decode(Resources res, int resId, int width, int height) {
        try {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(res, resId),
                    (decoder, info, source) -> {
                        final Size size = info.getSize();
                        final float scaleX = (float) width / size.getWidth();
                        final float scaleY = (float) height / size.getHeight();
                        final float scale = Math.min(1f,
                                mScaleType == ImageView.ScaleType.CENTER_CROP
                                        ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY));
                        decoder.setTargetSize(Math.round(size.getWidth() * scale),
                                Math.round(size.getHeight() * scale));
                    });
        } catch (IOException e) {
            Log.e(TAG, "Cannot decode picture " + res.getResourceEntryName(resId), e);
            return null;
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final ImageView image;

        PageHolder(View page, ImageView image) {
            super(page);
            this.image = image;
        }
    }

    private final class Adapter extends RecyclerView.Adapter<PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            final View page;
            final ImageView image;
            if (mPageLayout != 0) {
                page = LayoutInflater.from(parent.getContext()).inflate(mPageLayout, parent, false);
                image = page.findViewById(R.id.gallery_page_image);
            } else {
                image = new ImageView(parent.getContext());
                page = image;
            }
            page.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            image.setScaleType(mScaleType);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (mCallback != null) {
                page.setOnClickListener(v -> mCallback.onPictureClicked());
            }
            return new PageHolder(page, image);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            // The tag tells a finished decode whether the view still shows that picture.
            holder.image.setTag(position);
            load(holder.image, position);
            if (mCallback != null) {
                mCallback.onPageBound(holder.itemView, position);
            }
        }

        @Override
        public int getItemCount() {
            return mImages.length;
        }
    }
}
