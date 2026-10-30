/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device.color;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * The color temperature wheel of stock ZUI's ScreenTonePrefsX, drawn instead of stock's
 * bitmap: hues around the rim (ColorSettings.wheelHue), fading to white in the centre. A dot
 * marks the white point; tapping or dragging moves it. Points are relative to the wheel's
 * bounding square (0..1, centre 0.5), as ColorSettings stores them; like stock, the dot's centre
 * stays one dot radius inside the rim.
 */
public class ColorWheelView extends View {
    /** Called while the dot moves (done = false) and once more when the finger lifts. */
    public interface OnPointChangedListener {
        void onPointChanged(float x, float y, boolean done);
    }

    private static final int HUE_STOPS = 13;

    private final Paint mHuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mDotRadius;

    private float mCenterX;
    private float mCenterY;
    private float mRadius;
    private float mPointX = 0.5f;
    private float mPointY = 0.5f;
    private OnPointChangedListener mListener;

    public ColorWheelView(Context context) {
        this(context, null);
    }

    public ColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        final float density = getResources().getDisplayMetrics().density;
        mDotRadius = 12 * density;
        mDotRingPaint.setStyle(Paint.Style.STROKE);
        mDotRingPaint.setStrokeWidth(3 * density);
        mDotRingPaint.setColor(Color.WHITE);
        mDotOutlinePaint.setStyle(Paint.Style.STROKE);
        mDotOutlinePaint.setStrokeWidth(density);
        mDotOutlinePaint.setColor(0x61000000);
    }

    public void setOnPointChangedListener(OnPointChangedListener listener) {
        mListener = listener;
    }

    /** Moves the dot without notifying the listener. */
    public void setPoint(float x, float y) {
        mPointX = x;
        mPointY = y;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mCenterX = w / 2f;
        mCenterY = h / 2f;
        mRadius = Math.min(w, h) / 2f;
        if (mRadius <= 0) {
            return;
        }
        final int[] colors = new int[HUE_STOPS];
        final float[] positions = new float[HUE_STOPS];
        for (int i = 0; i < HUE_STOPS; i++) {
            positions[i] = i / (float) (HUE_STOPS - 1);
            // SweepGradient starts at 3 o'clock and runs clockwise.
            final float hue = ColorSettings.wheelHue(positions[i] * 360.0 + 90.0);
            colors[i] = Color.HSVToColor(
                    new float[] {hue, ColorSettings.WHEEL_RIM_SATURATION, 1f});
        }
        mHuePaint.setShader(new SweepGradient(mCenterX, mCenterY, colors, positions));
        mWhitePaint.setShader(new RadialGradient(mCenterX, mCenterY, mRadius,
                Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Square: the smaller of the two sizes.
        final int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        final int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRadius <= 0) {
            return;
        }
        final int alpha = isEnabled() ? 255 : 97;
        mHuePaint.setAlpha(alpha);
        mWhitePaint.setAlpha(alpha);
        mDotFillPaint.setColor(displayedColor(mPointX, mPointY));
        mDotFillPaint.setAlpha(alpha);
        mDotRingPaint.setAlpha(alpha);
        mDotOutlinePaint.setAlpha(isEnabled() ? 0x61 : 0x25);
        canvas.drawCircle(mCenterX, mCenterY, mRadius, mHuePaint);
        canvas.drawCircle(mCenterX, mCenterY, mRadius, mWhitePaint);

        final float x = mCenterX + (mPointX - 0.5f) * 2 * mRadius;
        final float y = mCenterY + (mPointY - 0.5f) * 2 * mRadius;
        canvas.drawCircle(x, y, mDotRadius, mDotFillPaint);
        canvas.drawCircle(x, y, mDotRadius, mDotRingPaint);
        canvas.drawCircle(x, y, mDotRadius + mDotRingPaint.getStrokeWidth() / 2, mDotOutlinePaint);
    }

    /** The wheel's color under a point, for the dot. */
    private static int displayedColor(float x, float y) {
        final double dx = x - 0.5;
        final double dy = y - 0.5;
        final double radius = Math.min(1.0, Math.hypot(dx, dy) / 0.5);
        final float hue = ColorSettings.wheelHue(Math.toDegrees(Math.atan2(dx, -dy)));
        return Color.HSVToColor(new float[] {
                hue, (float) (radius * ColorSettings.WHEEL_RIM_SATURATION), 1f});
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || mRadius <= 0) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (Math.hypot(event.getX() - mCenterX, event.getY() - mCenterY) > mRadius) {
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                moveTo(event.getX(), event.getY(), false);
                return true;
            case MotionEvent.ACTION_MOVE:
                moveTo(event.getX(), event.getY(), false);
                return true;
            case MotionEvent.ACTION_UP:
                moveTo(event.getX(), event.getY(), true);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                notifyListener(true);
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void moveTo(float touchX, float touchY, boolean done) {
        float dx = touchX - mCenterX;
        float dy = touchY - mCenterY;
        final float limit = mRadius - mDotRadius;
        final float distance = (float) Math.hypot(dx, dy);
        if (distance > limit && distance > 0) {
            dx *= limit / distance;
            dy *= limit / distance;
        }
        mPointX = 0.5f + dx / (2 * mRadius);
        mPointY = 0.5f + dy / (2 * mRadius);
        invalidate();
        notifyListener(done);
    }

    private void notifyListener(boolean done) {
        if (mListener != null) {
            mListener.onPointChanged(mPointX, mPointY, done);
        }
    }
}
