package com.ti.app.mydoctor.gui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;

import com.ti.app.mydoctor.R;


public class ECGDrawActivity extends Activity {
    private ECGDrawView drawRunnable;

    private static ECGDrawActivity activity;

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
        drawRunnable = (ECGDrawView) findViewById(R.id.ecg_view_draw);
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
