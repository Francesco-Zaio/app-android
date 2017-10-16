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
    public static float gridHeight;

    public static DisplayMetrics dm;

    /** Quante griglie sono richieste in totale? */
    public static int gridCnt = 1;

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
        gridCnt = ECGDrawData.nLead;
    }

    /**
     * Impostare il colore di sfondo
     */
    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    private boolean isDrawBG = true;

    /**
     * Imposta se disegnare lo sfondo
     *
     * @param isDrawBG
     */
    public void setDrawBG(boolean isDrawBG) {
        this.isDrawBG = isDrawBG;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isDrawBG)
            return;
        canvas.drawColor(backgroundColor);
        if (gridCnt < 2) {
            return;
        }
        mPaint.setStrokeWidth(1);
        mPaint.setColor(Color.rgb(0xba, 0xba, 0xba));
        for (int j=0; j<gridCnt; j++)
            canvas.drawLine(0, j*gridHeight, viewWidth, j*gridHeight, mPaint);

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

    /**
     * Se disegnare una scala di guadagno
     */
    private boolean isDrawScale = false;

    /**
     * Imposta se disegnare la scala del guadagno
     */
    public void setDrawScale(boolean isDrawScale) {
        this.isDrawScale = isDrawScale;
    }

    /** Disegna il righello */
    private void drawScale(Canvas canvas) {
        if (gridHeight > 1 && isDrawScale) {
            int h = viewHeight / gridCnt;// 一格的高度
            mPaint.setColor(Color.BLUE);
            mPaint.setStrokeWidth(dm.density);
            float i = (h * gain) / 2f;
            canvas.drawLine(0, viewHeight / 2 - i, h / 2, viewHeight / 2 - i, mPaint);
            canvas.drawLine(0, viewHeight / 2 + i, h / 2, viewHeight / 2 + i, mPaint);
            canvas.drawLine(h / 4, viewHeight / 2 - i, h / 4, viewHeight / 2 + i,
                    mPaint);
        }
    }

    private float gain = 2;

    public float getGain() {
        return gain;
    }

    public void setGain(float gain) {
        if (gain == 0) {
            this.gain = 0.5f;
        } else
            this.gain = gain;
        postInvalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        setViewHeight(w, h);
    }

    public void setViewHeight(int w, int h) {
        // Converte i pixel dello schermo a millimetri
        viewHeight = h;
        viewWidth = w;
        gridCnt = ECGDrawData.nLead;
        gridHeight = (float) (viewHeight / gridCnt);
        postInvalidate();
    }

    /** Ottieni un totale di quante griglie hai bisogno */
    public int getGridCnt() {
        return gridCnt;
    }

    /** Impostare il numero totale di griglie richieste */
    public void setGridCnt(int gridCnt) {
        ECGBackgroudView.gridCnt = gridCnt;
    }

    /** Ottenere l'altezza effettiva della scala di fondo (mm) */
    public float getGridHeigh() {
        return gridHeight;
    }

    /** Ottenere l'altezza effettiva della scala di fondo (mm) */
    public void setGridHeigh(float gridHeight) {
        ECGBackgroudView.gridHeight = gridHeight;
    }
}

