package com.sjamthe.practiceplayer;

import static com.google.android.material.R.color.design_default_color_primary;
import static com.google.android.material.R.color.design_default_color_background;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

public class SwarView extends View {

    private float minCentAngle, maxCentAngle, curCentAngle;
    private Canvas canvas;

    public SwarView(Context context) {
        super(context);
        init(context);
    }

    public SwarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SwarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        this.minCentAngle = 0;
        this.maxCentAngle = 0;
        this.curCentAngle = 0;
    }

    public void setCentError(float minCentError, float maxCentError, float curCentError) {
        this.curCentAngle = centErrorToAngle(curCentError);
        this.minCentAngle = centErrorToAngle(minCentError);
        this.maxCentAngle = centErrorToAngle(maxCentError);
    }

    // 270 degree = 0 Error. 100 cent is max error possible (+- 50 actually)
    private float centErrorToAngle(float centError) {
        return centError*360/100 + 270F;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint paint = new Paint();
        //circle
        int circleWidth = 30;
        int x = getWidth();
        int y = getHeight();
        int radius = x/2 - circleWidth;

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getResources().getColor(design_default_color_background));
        canvas.drawPaint(paint);
        // Draw circle
        paint.setColor(Color.parseColor("#BFD7ED"));
        paint.setStrokeWidth(circleWidth);
        canvas.drawCircle(x/2, y/2, radius, paint);

        // Draw note spread arc
        RectF rectf = new RectF(circleWidth, circleWidth, x - circleWidth,
                y - circleWidth);
        paint.setColor(getResources().getColor(R.color.purple_200));
        canvas.drawArc(rectf, this.minCentAngle, (this.maxCentAngle-this.minCentAngle),
                false, paint);

        // Draw current cent position
        paint.setColor(getResources().getColor(design_default_color_primary));
        canvas.drawArc(rectf, this.curCentAngle, 1, false, paint);

        // Draw perfect cent position last so it is on the top
        paint.setColor(getResources().getColor(R.color.white));
        canvas.drawArc(rectf, 270F, (float) 1, false, paint);
    }
}
