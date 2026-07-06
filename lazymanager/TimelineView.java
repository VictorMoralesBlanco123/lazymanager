package com.example.lazymanager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.lazymanagerv21.R;

import java.util.ArrayList;
import java.util.List;

/**
 * "The day, sideways" — one column per half-hour tick.
 * Each column stacks one dot per person (lunch = peach, break = sage, on floor = ink),
 * with the head count underneath, hour labels above, a faint line at the current time,
 * and a halo around the selected column.
 *
 * Tap a column to inspect that time; tap the same column again to jump back to now
 * (MainActivity owns that logic via {@link OnTickTapListener}).
 */
public class TimelineView extends View {

    public interface OnTickTapListener {
        void onTickTapped(int tick);
    }

    private List<Worker> workers = new ArrayList<>();
    private int nowTick = Ticks.DAY_START;
    private int selectedTick = Ticks.DAY_START;
    private OnTickTapListener listener;

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int cInk, cSub, cSage, cPeach, cSurface, cLine;
    private final float density;
    private float downX, downY;

    public TimelineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        cInk = ContextCompat.getColor(context, R.color.ink);
        cSub = ContextCompat.getColor(context, R.color.sub);
        cSage = ContextCompat.getColor(context, R.color.sage);
        cPeach = ContextCompat.getColor(context, R.color.peach);
        cSurface = ContextCompat.getColor(context, R.color.surface);
        cLine = ContextCompat.getColor(context, R.color.line);

        labelPaint.setTypeface(Typeface.MONOSPACE);
        labelPaint.setTextSize(dp(10));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        countPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        countPaint.setTextSize(dp(11));
        countPaint.setTextAlign(Paint.Align.CENTER);

        boxFill.setColor(cSurface);
        boxStroke.setStyle(Paint.Style.STROKE);
        boxStroke.setStrokeWidth(dp(1));
        boxStroke.setColor(cLine);

        nowPaint.setColor(cInk);
        nowPaint.setAlpha(46);   // ~18%
        nowPaint.setStrokeWidth(dp(2));
    }

    private float dp(float v) {
        return v * density;
    }

    public int columnWidthPx() {
        return (int) dp(40);
    }

    public void setData(List<Worker> workers, int nowTick, int selectedTick) {
        this.workers = workers;
        this.nowTick = nowTick;
        this.selectedTick = selectedTick;
        invalidate();
    }

    public void setOnTickTapListener(OnTickTapListener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int cols = Ticks.DAY_END - Ticks.DAY_START;
        int w = cols * columnWidthPx();
        int h = (int) dp(158);
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int colW = columnWidthPx();
        float labelBaseline = dp(14);
        float boxTop = dp(22);
        float boxBottom = getHeight() - dp(4);
        float dot = dp(5.5f);
        float gap = dp(2);

        for (int t = Ticks.DAY_START; t < Ticks.DAY_END; t++) {
            int i = t - Ticks.DAY_START;
            float left = i * colW;
            float cx = left + colW / 2f;
            boolean isSel = t == selectedTick;
            boolean isNow = t == nowTick;

            // Now indicator — a faint vertical line behind the column
            if (isNow) {
                canvas.drawLine(cx, boxTop, cx, boxBottom, nowPaint);
            }

            // Selection halo
            if (isSel) {
                RectF r = new RectF(left + dp(3), boxTop, left + colW - dp(3), boxBottom);
                canvas.drawRoundRect(r, dp(10), dp(10), boxFill);
                canvas.drawRoundRect(r, dp(10), dp(10), boxStroke);
            }

            // Hourly label (every other tick)
            if (t % 2 == 0) {
                labelPaint.setColor(isSel ? cInk : cSub);
                labelPaint.setFakeBoldText(isSel);
                canvas.drawText(Ticks.fmtShort(t), cx, labelBaseline, labelPaint);
            }

            // Count the coverage at this tick
            int onFloor = 0, onBreak = 0, onLunch = 0;
            for (Worker w : workers) {
                String s = w.statusAt(t, nowTick);
                if (Worker.ST_ON.equals(s)) onFloor++;
                else if (Worker.ST_BREAK.equals(s)) onBreak++;
                else if (Worker.ST_LUNCH.equals(s)) onLunch++;
            }
            int total = onFloor + onBreak + onLunch;

            // Head count under the dots
            float countBaseline = boxBottom - dp(8);
            countPaint.setColor(isSel ? cInk : cSub);
            if (total > 0) {
                canvas.drawText(String.valueOf(total), cx, countBaseline, countPaint);
            }

            // Stacked dots, 3 per row, anchored to the bottom.
            // Draw order matches the prototype: lunch on top, then break, then on-floor.
            if (total > 0) {
                int rows = (total + 2) / 3;
                float rowH = dot + gap;
                float gridBottom = countBaseline - dp(14);
                float gridTop = Math.max(boxTop + dp(4), gridBottom - rows * rowH);
                float gridLeft = cx - (3 * dot + 2 * gap) / 2f;

                for (int idx = 0; idx < total; idx++) {
                    int row = idx / 3;
                    int col = idx % 3;
                    float x = gridLeft + col * (dot + gap) + dot / 2f;
                    float y = gridTop + row * rowH + dot / 2f;
                    if (y < boxTop + dp(2)) continue;   // safety for very crowded ticks

                    if (idx < onLunch) {
                        dotPaint.setColor(cPeach);
                        dotPaint.setAlpha(255);
                    } else if (idx < onLunch + onBreak) {
                        dotPaint.setColor(cSage);
                        dotPaint.setAlpha(255);
                    } else {
                        dotPaint.setColor(cInk);
                        dotPaint.setAlpha(178);   // ~70%
                    }
                    canvas.drawCircle(x, y, dot / 2f, dotPaint);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (Math.abs(event.getX() - downX) < dp(10) && Math.abs(event.getY() - downY) < dp(10)) {
                    int col = (int) (event.getX() / columnWidthPx());
                    int max = Ticks.DAY_END - Ticks.DAY_START - 1;
                    int tick = Ticks.DAY_START + Math.max(0, Math.min(col, max));
                    if (listener != null) listener.onTickTapped(tick);
                    performClick();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
