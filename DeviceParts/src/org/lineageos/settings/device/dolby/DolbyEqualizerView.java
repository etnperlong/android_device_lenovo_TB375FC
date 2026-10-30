/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.dolby;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import org.lineageos.settings.device.R;

/**
 * The graphic equalizer as a response curve, drawn like LineageOS AudioFX's: one handle per band
 * of stock's GeqView (every other DAX band), dragged in whole dB within GEQ_MAX_DB, low to high
 * frequencies from left to right. Writes the gains while dragging (at most every
 * DRAG_WRITE_INTERVAL_MS) and on release; DolbyService applies them. Gains written elsewhere,
 * such as the equalizer page's reset to flat, show as soon as nothing is being dragged.
 */
public class DolbyEqualizerView extends View {
    private static final long DRAG_WRITE_INTERVAL_MS = 80;
    private static final String[] GRID_LABELS = {"+6", "+3", "0", "-3", "-6"};
    private static final int[] GRID_VALUES = {6, 3, 0, -3, -6};

    private final ContentResolver mResolver;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver mGainsObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            if (!mDragging && !mHasPendingWrite) {
                setGains(DolbySettings.getGeqGains(mResolver));
            }
        }
    };
    private final Runnable mWritePending = this::writePending;
    private final int[] mGains = new int[DolbySettings.GEQ_BANDS];
    private final float[] mPointX = new float[DolbySettings.GEQ_BANDS];
    private final float[] mPointY = new float[DolbySettings.GEQ_BANDS];
    private final String[] mFrequencyLabels = new String[DolbySettings.GEQ_BANDS];
    private final Paint mGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHandleOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mCurvePath = new Path();
    private final Path mFillPath = new Path();
    private final float mDensity;
    private final int mAccentColor;
    private final int mOutlineColor;
    private final int mSurfaceColor;
    private final int mLabelColor;
    private final float mGridLabelWidth;

    private float mPlotLeft;
    private float mPlotRight;
    private float mPlotTop;
    private float mPlotBottom;
    private long mLastWriteUptime;
    private boolean mHasPendingWrite;
    private boolean mDragging;
    private int mActiveBand = -1;
    private String mActiveGainLabel;

    public DolbyEqualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mResolver = context.getContentResolver();
        mDensity = context.getResources().getDisplayMetrics().density;
        mAccentColor = context.getColor(R.color.option_card_checked_stroke);
        mOutlineColor = context.getColor(R.color.swatch_outline);
        mSurfaceColor = context.getColor(R.color.card_surface);
        mLabelColor = context.getColor(R.color.card_secondary_text);
        mTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10,
                context.getResources().getDisplayMetrics()));
        float labelWidth = 0;
        for (String label : GRID_LABELS) {
            labelWidth = Math.max(labelWidth, mTextPaint.measureText(label));
        }
        mGridLabelWidth = labelWidth;
        mTextPaint.setColor(mLabelColor);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mGridPaint.setStrokeWidth(dp(1));
        mGridPaint.setColor(mOutlineColor);
        mCurvePaint.setStrokeWidth(dp(2));
        mCurvePaint.setStrokeCap(Paint.Cap.ROUND);
        mCurvePaint.setStrokeJoin(Paint.Join.ROUND);
        mCurvePaint.setStyle(Paint.Style.STROKE);
        mHandlePaint.setStyle(Paint.Style.FILL);
        mHandleOutlinePaint.setStyle(Paint.Style.STROKE);
        mHandleOutlinePaint.setStrokeWidth(dp(2));
        for (int i = 0; i < DolbySettings.GEQ_BANDS; i++) {
            final int hz = DolbySettings.GEQ_FREQUENCIES[i];
            mFrequencyLabels[i] = hz < 1000
                    ? context.getString(R.string.dolby_geq_band_hz, hz)
                    : context.getString(R.string.dolby_geq_band_khz, hz / 1000f);
        }
        setClickable(true);
        setFocusable(true);
        setContentDescription(context.getString(R.string.dolby_geq_description));
    }

    /** Replaces the displayed values without writing them. */
    public void setGains(int[] gains) {
        System.arraycopy(gains, 0, mGains, 0, DolbySettings.GEQ_BANDS);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mResolver.registerContentObserver(
                Settings.Secure.getUriFor(DolbySettings.KEY_GEQ_GAINS), false, mGainsObserver);
        if (!mDragging) {
            setGains(DolbySettings.getGeqGains(mResolver));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mResolver.unregisterContentObserver(mGainsObserver);
        if (mHasPendingWrite) {
            mHandler.removeCallbacks(mWritePending);
            writePending();
        }
        mDragging = false;
        mActiveBand = -1;
        setPressed(false);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && mDragging) {
            mDragging = false;
            setPressed(false);
            mActiveBand = -1;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            if (mHasPendingWrite) {
                mHandler.removeCallbacks(mWritePending);
                writePending();
            }
        }
        super.setEnabled(enabled);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updatePlotBounds();
        final boolean enabled = isEnabled();
        final int textAlpha = enabled ? 255 : 92;
        final int gridAlpha = enabled ? 112 : 56;
        mTextPaint.setColor(mLabelColor);
        mTextPaint.setAlpha(textAlpha);
        mGridPaint.setAlpha(gridAlpha);

        mTextPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i < GRID_VALUES.length; i++) {
            final float y = yForGain(GRID_VALUES[i]);
            canvas.drawLine(mPlotLeft, y, mPlotRight, y, mGridPaint);
            canvas.drawText(GRID_LABELS[i], mPlotLeft - dp(6), centeredTextBaseline(y), mTextPaint);
        }
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        for (int i = 0; i < DolbySettings.GEQ_BANDS; i++) {
            mPointX[i] = xForBand(i);
            mPointY[i] = yForGain(mGains[i]);
        }
        buildCurve();
        mFillPaint.setColor(withAlpha(mAccentColor, enabled ? 42 : 15));
        canvas.drawPath(mFillPath, mFillPaint);
        mCurvePaint.setColor(withAlpha(mAccentColor, enabled ? 255 : 92));
        canvas.drawPath(mCurvePath, mCurvePaint);

        mHandlePaint.setColor(withAlpha(mAccentColor, enabled ? 255 : 92));
        mHandleOutlinePaint.setColor(withAlpha(mSurfaceColor, enabled ? 255 : 160));
        for (int i = 0; i < DolbySettings.GEQ_BANDS; i++) {
            final boolean active = enabled && i == mActiveBand;
            canvas.drawCircle(mPointX[i], mPointY[i], dp(active ? 7 : 4), mHandlePaint);
            canvas.drawCircle(mPointX[i], mPointY[i], dp(active ? 7 : 4), mHandleOutlinePaint);
        }

        final float labelY = getHeight() - getPaddingBottom();
        for (int i = 0; i < DolbySettings.GEQ_BANDS; i++) {
            if (i == 0) {
                mTextPaint.setTextAlign(Paint.Align.LEFT);
            } else if (i == DolbySettings.GEQ_BANDS - 1) {
                mTextPaint.setTextAlign(Paint.Align.RIGHT);
            } else {
                mTextPaint.setTextAlign(Paint.Align.CENTER);
            }
            canvas.drawText(mFrequencyLabels[i], mPointX[i], labelY, mTextPaint);
        }
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        if (enabled && mDragging && mActiveGainLabel != null) {
            final int band = mActiveBand;
            if (band >= 0) {
                mTextPaint.setColor(mAccentColor);
                final float labelYPosition = mPointY[band] - dp(12);
                final float visibleLabelY = labelYPosition < mPlotTop + dp(10)
                        ? Math.min(mPlotBottom, mPointY[band] + dp(22)) : labelYPosition;
                canvas.drawText(mActiveGainLabel, mPointX[band], visibleLabelY, mTextPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                updatePlotBounds();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                mDragging = true;
                mActiveBand = nearestBand(event.getX());
                setPressed(true);
                updateFromTouch(event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mDragging) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    updateFromTouch(event.getY());
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDragging) {
                    updateFromTouch(event.getY(), true);
                    mDragging = false;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    setPressed(false);
                    mActiveBand = -1;
                    mActiveGainLabel = null;
                    mHasPendingWrite = true;
                    mHandler.removeCallbacks(mWritePending);
                    writePending();
                    invalidate();
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        performClick();
                    }
                    return true;
                }
                return false;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateFromTouch(float touchY) {
        updateFromTouch(touchY, false);
    }

    private void updateFromTouch(float touchY, boolean finishing) {
        if (mActiveBand < 0) {
            return;
        }
        final int gain = DolbySettings.clamp(Math.round(
                DolbySettings.GEQ_MAX_DB - (touchY - mPlotTop)
                        * (2f * DolbySettings.GEQ_MAX_DB) / (mPlotBottom - mPlotTop)),
                -DolbySettings.GEQ_MAX_DB, DolbySettings.GEQ_MAX_DB);
        final String gainValue = (gain > 0 ? "+" : "") + gain;
        mActiveGainLabel = getContext().getString(R.string.dolby_geq_gain_db, gainValue);
        if (mGains[mActiveBand] == gain) {
            if (finishing) {
                mHasPendingWrite = true;
            }
            invalidate();
            return;
        }
        mGains[mActiveBand] = gain;
        if (finishing) {
            mHasPendingWrite = true;
        } else {
            scheduleWrite();
        }
        invalidate();
    }

    private void scheduleWrite() {
        mHasPendingWrite = true;
        mHandler.removeCallbacks(mWritePending);
        final long sinceLastWrite = SystemClock.uptimeMillis() - mLastWriteUptime;
        if (sinceLastWrite >= DRAG_WRITE_INTERVAL_MS) {
            writePending();
        } else {
            mHandler.postDelayed(mWritePending, DRAG_WRITE_INTERVAL_MS - sinceLastWrite);
        }
    }

    private void writePending() {
        if (!mHasPendingWrite) {
            return;
        }
        mHandler.removeCallbacks(mWritePending);
        mHasPendingWrite = false;
        writeNow();
    }

    private void writeNow() {
        mLastWriteUptime = SystemClock.uptimeMillis();
        DolbySettings.putGeqGains(mResolver, mGains);
    }

    private void updatePlotBounds() {
        mPlotLeft = getPaddingLeft() + mGridLabelWidth + dp(8);
        mPlotRight = getWidth() - getPaddingRight() - dp(8);
        mPlotTop = getPaddingTop() + dp(12);
        if (mPlotRight <= mPlotLeft) {
            mPlotRight = mPlotLeft + dp(1);
        }
        mPlotBottom = getHeight() - getPaddingBottom() - mTextPaint.getTextSize() - dp(18);
        if (mPlotBottom <= mPlotTop) {
            mPlotBottom = mPlotTop + dp(1);
        }
    }

    private void buildCurve() {
        mCurvePath.reset();
        mCurvePath.moveTo(mPointX[0], mPointY[0]);
        appendCurveSegments(mCurvePath);

        mFillPath.reset();
        final float zeroDbY = yForGain(0);
        mFillPath.moveTo(mPointX[0], zeroDbY);
        mFillPath.lineTo(mPointX[0], mPointY[0]);
        appendCurveSegments(mFillPath);
        mFillPath.lineTo(mPointX[DolbySettings.GEQ_BANDS - 1], zeroDbY);
        mFillPath.close();
    }

    private void appendCurveSegments(Path path) {
        for (int i = 0; i < DolbySettings.GEQ_BANDS - 1; i++) {
            final int previous = Math.max(0, i - 1);
            final int next = i + 1;
            final int nextNext = Math.min(DolbySettings.GEQ_BANDS - 1, i + 2);
            final float lowY = Math.min(mPointY[i], mPointY[next]);
            final float highY = Math.max(mPointY[i], mPointY[next]);
            final float control1X = mPointX[i] + (mPointX[next] - mPointX[previous]) / 6f;
            final float control1Y = clamp(
                    mPointY[i] + (mPointY[next] - mPointY[previous]) / 6f, lowY, highY);
            final float control2X = mPointX[next] - (mPointX[nextNext] - mPointX[i]) / 6f;
            final float control2Y = clamp(
                    mPointY[next] - (mPointY[nextNext] - mPointY[i]) / 6f, lowY, highY);
            path.cubicTo(control1X, control1Y, control2X, control2Y,
                    mPointX[next], mPointY[next]);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float xForBand(int band) {
        return mPlotLeft + (mPlotRight - mPlotLeft) * band / (DolbySettings.GEQ_BANDS - 1f);
    }

    private float yForGain(int gain) {
        return mPlotTop + (DolbySettings.GEQ_MAX_DB - gain)
                * (mPlotBottom - mPlotTop) / (2f * DolbySettings.GEQ_MAX_DB);
    }

    private int nearestBand(float x) {
        final float step = (mPlotRight - mPlotLeft) / (DolbySettings.GEQ_BANDS - 1f);
        return DolbySettings.clamp(Math.round((x - mPlotLeft) / step), 0,
                DolbySettings.GEQ_BANDS - 1);
    }

    private float centeredTextBaseline(float centerY) {
        return centerY - (mTextPaint.ascent() + mTextPaint.descent()) / 2f;
    }

    private float dp(float value) {
        return value * mDensity;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }
}
