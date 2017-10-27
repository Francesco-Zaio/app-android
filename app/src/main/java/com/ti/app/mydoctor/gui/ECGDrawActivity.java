package com.ti.app.mydoctor.gui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ti.app.mydoctor.R;
import com.ti.app.telemed.core.common.ECGDrawData;


public class ECGDrawActivity extends Activity {
    private ECGDrawView drawRunnable;

    private static ECGDrawActivity activity;

    private ProgressBar pb;
    private TextView tv;


    public static ECGDrawActivity getInstance() {
        return activity;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.ecg_draw);
        activity = this;

        Button okButton = (Button) findViewById(R.id.okButton);
        Button cancelButton = (Button) findViewById(R.id.cancelButton);
        pb = (ProgressBar) findViewById(R.id.ecgProgressBar);
        tv = (TextView) findViewById(R.id.ecg_tv_msg);
        pb.setProgress(0);
        drawRunnable = (ECGDrawView) findViewById(R.id.ecg_view_draw);
        drawRunnable.setProgressBar(pb);
        drawRunnable.setTextViev(tv);
        /*
        okButton.setOnClickListener(listener);
        cancelButton.setOnClickListener(listener);
        */
    }
    @Override
    public void onPause() {
        super.onPause();
        if (!drawRunnable.isPause()) {
            drawRunnable.Pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (drawRunnable.isPause()) {
            drawRunnable.Continue();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activity = null;
        if (!drawRunnable.isStop()) {
            drawRunnable.Stop();
        }
    }

}
