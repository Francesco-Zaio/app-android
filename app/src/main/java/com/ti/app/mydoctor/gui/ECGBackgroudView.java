package com.ti.app.mydoctor.gui;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import com.ti.app.telemed.core.common.ECGDrawData;

public class ECGBackgroudView extends View {

    public static int viewWidth;
    public static int viewHeight;
    public static DisplayMetrics dm;

    private int backgroundColor = 0;

    private Paint mPaint;

    public ECGBackgroudView(Context context) {
        super(context);
        initScreen(context);
    }

    public ECGBackgroudView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initScreen(context);
    }

    public ECGBackgroudView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initScreen(context);
    }

    private void initScreen(Context context) {
        WindowManager wmManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        dm = new DisplayMetrics();
        wmManager.getDefaultDisplay().getMetrics(dm);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStrokeWidth(1);
        backgroundColor = Color.WHITE;
    }

    /**
     * Impostare il colore di sfondo
     */
    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(backgroundColor);
        if (ECGDrawData.nLead < 2) {
            return;
        }
        mPaint.setStrokeWidth(1);
        mPaint.setColor(Color.rgb(0xba, 0xba, 0xba));

        for (int j = 0; j < ECGDrawData.nLead; j++) {
            int y = viewHeight * (j + 1) / ECGDrawData.nLead;
            canvas.drawLine(0, y, viewWidth, y, mPaint);
        }

        /*
        // Disegna l'ordinata
        for (float i = 0; i < width; i += gridHeigh) {
            canvas.drawLine(fMMgetPxforX(i), 0, fMMgetPxforX(i), mHeight,
                    mPaint);
        }

        int i = gridCnt / 2;
        for (int j = 0; j < i; j++) {
            canvas.drawLine(0, fMMgetPxfory(fPXgetMMforY(mHeight / 2)
                            - gridHeigh * j), mWidth,
                    fMMgetPxfory(fPXgetMMforY(mHeight / 2) - gridHeigh * j),
                    mPaint);
        }

        for (int j = 0; j < i; j++) {
            canvas.drawLine(0, fMMgetPxfory(fPXgetMMforY(mHeight / 2)
                            + gridHeigh * j), mWidth,
                    fMMgetPxfory(fPXgetMMforY(mHeight / 2) + gridHeigh * j),
                    mPaint);
        }

        drawScale(canvas);
        */
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        postInvalidate();
    }
}

