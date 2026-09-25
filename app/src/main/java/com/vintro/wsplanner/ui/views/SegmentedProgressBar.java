package com.vintro.wsplanner.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.vintro.wsplanner.R;

// horizontal segmented progress bar with rounded external corners and thin gaps
public class SegmentedProgressBar extends View {
    private int totalSegments = 1;
    private int completedSegments = 0;

    private int activeColor;
    private int inactiveColor;
    private float gapPx;
    private float cornerRadiusPx;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public SegmentedProgressBar(Context context) {
        super(context);
        init(context);
    }

    public SegmentedProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SegmentedProgressBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    // initialize default dimensions and segment colors
    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        gapPx = 2f * density;
        cornerRadiusPx = 3f * density;

        activeColor = ContextCompat.getColor(context, R.color.active_lesson_border);
        inactiveColor = ContextCompat.getColor(context, R.color.timeline_line_upcoming);
    }

    // update progress with completed vs total count
    public void setProgress(int completed, int total) {
        this.completedSegments = Math.max(0, completed);
        this.totalSegments = Math.max(1, total);
        invalidate();
    }

    // configure segment active and inactive colors
    public void setColors(int activeColor, int inactiveColor) {
        this.activeColor = activeColor;
        this.inactiveColor = inactiveColor;
        invalidate();
    }

    // draw segmented pill shapes with rounded outer edges
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int n = Math.max(1, totalSegments);
        float totalGap = (n - 1) * gapPx;
        float segWidth = (w - totalGap) / n;
        if (segWidth < 1f) segWidth = 1f;

        float r = Math.min(cornerRadiusPx, Math.min(segWidth / 2f, h / 2f));

        for (int i = 0; i < n; i++) {
            float left = i * (segWidth + gapPx);
            float right = (i == n - 1) ? w : (left + segWidth);

            rect.set(left, 0, right, h);
            path.reset();

            boolean isCompleted = i < completedSegments;
            paint.setColor(isCompleted ? activeColor : inactiveColor);

            float[] radii = new float[8];
            if (n == 1) {
                for (int k = 0; k < 8; k++) radii[k] = r;
            } else if (i == 0) {
                radii[0] = r; radii[1] = r; // top-left
                radii[6] = r; radii[7] = r; // bottom-left
            } else if (i == n - 1) {
                radii[2] = r; radii[3] = r; // top-right
                radii[4] = r; radii[5] = r; // bottom-right
            } else {
                float innerR = Math.min(1f, r);
                for (int k = 0; k < 8; k++) radii[k] = innerR;
            }

            path.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.drawPath(path, paint);
        }
    }
}
