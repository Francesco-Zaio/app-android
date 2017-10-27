package com.ti.app.mydoctor.gui;

import android.graphics.Color;
import android.graphics.Path;
import android.util.Log;
import android.view.View;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ti.app.telemed.core.common.ECGDrawData;

public class ECGDrawView extends View implements Runnable {

    private static final int MAX_LEAD_NR = 12;
    private static final String TAG = "ECGDrawView";

    private Thread drawThread = null;
    private boolean stop = false;
    private boolean pause = false;

    private TextView tv;
    private String msg = "";
    private ProgressBar pb;

    /** Salva una serie di dati a schermo intero */
    private int[][] data2draw;
    private int[] yOffset;

    /** I due punti sull'asse X */
    private float stepx = 2;

    private DisplayMetrics dm;

    /** Punto di inserimento corrente dell'array */
    private int arraycnt = 0;

    /** L'altezza della vista corrente (px) */
    private int height = 0;
    private int width = 0;

    private int nlead = 1;
    private int npoints = 1;
    private int maxVal = 4095;
    private int baseline = 2048;
    private int leadDrawHeigth = 100;
    private int sleepInterval;

    private Paint paint;

    private CornerPathEffect cornerPathEffect = new CornerPathEffect(20);

    /** Guadagno di forma d'onda corrente */
    private int gain = 1;

    //private final List<int[]> ecgData = new ArrayList<>();
    private Path path = new Path();


    public ECGDrawView(Context context) {
        super(context);
        init(context);
    }

    public ECGDrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ECGDrawView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        WindowManager wmManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        dm = new DisplayMetrics();
        wmManager.getDefaultDisplay().getMetrics(dm);
        paint = new Paint();
        paint.setAntiAlias(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        height = h;
        width = w;
        nlead = ECGDrawData.nLead < MAX_LEAD_NR ? ECGDrawData.nLead:MAX_LEAD_NR;
        baseline = ECGDrawData.baseline;
        maxVal = ECGDrawData.maxVal;
        if (ECGDrawData.samplingRate > 0 && ECGDrawData.samplingRate < 500)
            sleepInterval = 1000/ECGDrawData.samplingRate;
        else
            sleepInterval = 10;
        stepx = dm.density;
        npoints = (int)(w/stepx);

        if (nlead>0)
            leadDrawHeigth = height/nlead;
        if(isInEditMode()){
            return;
        }

        yOffset = new int[nlead];
        data2draw = new int[npoints][nlead];
        for (int j = 0; j < nlead; j++) {
            yOffset[j] = height*j/nlead+leadDrawHeigth/2;
            for (int i = 0; i < npoints; i++) {
                data2draw[i][j] = yOffset[j];
            }
        }
        if (drawThread == null) {
            drawThread = new Thread(this, "DrawECGThread");
            drawThread.start();
        }
    }


    public void setProgressBar(ProgressBar pb) {
        this.pb = pb;
    }

    public void setTextViev(TextView tv) {
        this.tv = tv;
    }

    public void setGain(int gain) {
        this.gain = gain == 0 ? 1 : gain;
    }

    public void drawMsg(String msg) {
        this.msg = msg;
        postInvalidate();
    }

    public void Stop() {
        this.stop = true;
    }

    public void Pause() {
        this.pause = true;
    }

    public boolean isPause() {
        return this.pause;
    }

    public boolean isStop() {
        return this.stop;
    }

    public synchronized void Continue() {
        this.pause = false;
        this.notify();
        cleanWaveData();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(isInEditMode()){
            return;
        }

        if (tv != null && !ECGDrawData.message.equals(msg)) {
            msg = ECGDrawData.message;
            tv.setText(msg);
            //drawMsg(canvas);
        }
        for (int j=0; j<nlead; j++) {
            path.rewind();
            //path.reset();
            paint.setPathEffect(cornerPathEffect);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.RED);
            paint.setStrokeWidth(dm.density);
            path.moveTo(0, data2draw[0][j]);
            for (int i = 1; i < npoints; i++) {
                path.lineTo(i*stepx, data2draw[i][j]);
            }
            canvas.drawPath(path, paint);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(dm.density*3);
            canvas.drawLine(arraycnt*stepx , j*leadDrawHeigth+1, arraycnt*stepx, (j+1)*leadDrawHeigth-1, paint);
        }
        if (pb != null) {
            pb.setProgress(ECGDrawData.progress);
            //pb.invalidate();
        }
    }

    private void drawMsg(Canvas canvas) {
        Paint mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStrokeWidth(dm.density * 2);
        mPaint.setColor(Color.BLACK);
        mPaint.setTextSize(dm.density * 20);
        canvas.drawText(msg, (width - mPaint.measureText(msg)) / 2, height / 2, mPaint);
    }

    @Override
    public void run() {
        synchronized (this) {
            while (!stop) {
                try {
                    if (pause) {
                        this.wait();
                    }
                    int size = ECGDrawData.size();
                    if (size > 0) {
                        gain = ECGDrawData.gain;
                        addData(ECGDrawData.popData());
                    }
                    if (size <= 1)
                        Thread.sleep(sleepInterval);
                    else
                        Thread.sleep(sleepInterval-1);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
            cleanWaveData();
            drawThread = null;
        }
    }

    /**
     * Aggiungere i dati che devono essere disegnati nell'array
     */
    public void addData(int[] data) {
        if (data2draw != null && data != null && data.length == nlead) {
            for (int j=0; j<nlead;j++) {
                int v = (data[j] - baseline)*gain;
                data2draw[arraycnt][j] = yOffset[j] - leadDrawHeigth*v/maxVal;
            }
            //System.arraycopy(data, 0, data2draw[arraycnt], 0, data.length);
            arraycnt = (arraycnt + 1) % data2draw.length;
            postInvalidate();
        }
    }

    /**
     * Cancellare la forma d'onda disegnata
     */
    public void cleanWaveData() {
        int i,j;
        ECGDrawData.clearData();
        if (data2draw == null)
            return;
        arraycnt = 0;
        for (i = 0; i < data2draw.length; i++) {
            for (j=0; j<nlead; j++)
            data2draw[i][j] = -1;
        }
        postInvalidate();
    }
}
