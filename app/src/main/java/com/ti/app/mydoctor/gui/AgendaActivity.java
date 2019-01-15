package com.ti.app.mydoctor.gui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.mydoctor.R;

import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.util.AppUtil;
import com.ti.app.telemed.core.common.UserMeasure;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.usermodule.UserManager;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AgendaActivity extends AppCompatActivity {

    private TableLayout mTableLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Inizializza l'ActionBAr
        ActionBar customActionBar = getSupportActionBar();
        if (customActionBar != null) {
            //Setta il gradiente di sfondo della action bar
            Drawable cd = this.getResources().getDrawable(R.drawable.action_bar_background_color);
            customActionBar.setBackgroundDrawable(cd);

            customActionBar.setDisplayShowCustomEnabled(true);
            customActionBar.setDisplayShowTitleEnabled(false);
            //Setta l'icon
            customActionBar.setIcon(R.drawable.icon_action_bar);

            //Settare il font e il titolo della Activity
            LayoutInflater inflator = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View titleView = inflator.inflate(R.layout.actionbar_title, null);
            customActionBar.setCustomView(titleView);

            //L'icona dell'App diventa tasto per tornare nella Home
            customActionBar.setHomeButtonEnabled(true);
            customActionBar.setDisplayHomeAsUpEnabled(true);

            GWTextView titleTV = titleView.findViewById(R.id.actionbar_title_label);
            titleTV.setText(getResources().getString(R.string.today_agenda));
        }

        setContentView(R.layout.activity_agenda);

        // setup the table
        mTableLayout = findViewById(R.id.tableAgenda);
        loadData();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void loadData() {

        int leftRowMargin=0;
        int topRowMargin=0;
        int rightRowMargin=0;
        int bottomRowMargin = 0;
        int textSize, smallTextSize;

        textSize = (int) getResources().getDimension(R.dimen.font_size_medium);
        smallTextSize = (int) getResources().getDimension(R.dimen.font_size_small);
        DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());

        List<UserMeasure> data = MeasureManager.getMeasureManager().getBiometricUserMeasures(UserManager.getUserManager().getCurrentUser().getId());

        int rows = data.size();
        //getSupportActionBar().setTitle("Invoices (" + String.valueOf(rows) + ")");

        mTableLayout.removeAllViews();

        for(int i = 0; i < rows; i ++) {
            UserMeasure row = data.get(i);
            if (row.getSchedule() == null || row.getSchedule().isEmpty())
                continue;
            List<Date> events = row.getTodaySchedule();
            if (events.size() == 0)
                continue;

            // data columns
            final ImageView iv = new ImageView(this);
            iv.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT));
            iv.setPadding(10, 10, 10, 10);
            iv.setBackgroundColor(Color.parseColor("#f8f8f8"));
            iv.setImageResource(AppUtil.getSmallIconId(row.getMeasure()));

            final TextView tv2 = new TextView(this);
            tv2.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT));
            tv2.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            tv2.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            tv2.setPadding(5, 10, 0, 10);
            tv2.setBackgroundColor(Color.parseColor("#f8f8f8"));
            tv2.setTextColor(Color.parseColor("#000000"));
            tv2.setTypeface(Typeface.create("roboto_condensed", Typeface.NORMAL));
            tv2.setText(AppResourceManager.getResource().getString("measureType." + row.getMeasure()));

            final LinearLayout laySchedule = new LinearLayout(this);
            laySchedule.setOrientation(LinearLayout.VERTICAL);
            laySchedule.setPadding(0, 10, 0, 10);
            laySchedule.setBackgroundColor(Color.parseColor("#f8f8f8"));

            for (int j=0; j<events.size(); j++) {
                final TextView tv3 = new TextView(this);
                tv3.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
                        TableRow.LayoutParams.MATCH_PARENT));
                tv3.setPadding(5, 0, 0, 5);
                tv3.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize);
                tv3.setGravity(Gravity.CENTER_VERTICAL);
                tv3.setBackgroundColor(Color.parseColor("#f8f8f8"));
                tv3.setTextColor(Color.parseColor("#000000"));
                tv3.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
                tv3.setTypeface(Typeface.create("roboto_condensed", Typeface.NORMAL));
                tv3.setText(df.format(events.get(j)));
                laySchedule.addView(tv3);
            }
/*
            final TextView tv3b = new TextView(this);
            tv3b.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT));
            tv3b.setGravity(Gravity.RIGHT);
            tv3b.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize);
            tv3b.setPadding(5, 1, 0, 5);
            tv3b.setTextColor(Color.parseColor("#aaaaaa"));
            tv3b.setBackgroundColor(Color.parseColor("#f8f8f8"));
            tv3b.setText(row.getSchedule());
            laySchedule.addView(tv3b);
*/
            // add table row
            final TableRow tr = new TableRow(this);
            tr.setId(i);
            tr.setGravity(Gravity.CENTER_VERTICAL);
            TableLayout.LayoutParams trParams = new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT);
            trParams.setMargins(leftRowMargin, topRowMargin, rightRowMargin, bottomRowMargin);
            tr.setPadding(0,0,0,0);
            tr.setLayoutParams(trParams);
            tr.addView(iv);
            tr.addView(tv2);
            tr.addView(laySchedule);
            mTableLayout.addView(tr, trParams);

            if (i > 0) {
                // add separator row
                final TableRow trSep = new TableRow(this);
                TableLayout.LayoutParams trParamsSep = new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT);
                trParamsSep.setMargins(leftRowMargin, topRowMargin, rightRowMargin, bottomRowMargin);

                trSep.setLayoutParams(trParamsSep);
                TextView tvSep = new TextView(this);
                TableRow.LayoutParams tvSepLay = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
                        TableRow.LayoutParams.WRAP_CONTENT);
                tvSepLay.span = 4;
                tvSep.setLayoutParams(tvSepLay);
                tvSep.setBackgroundColor(Color.parseColor("#d9d9d9"));
                tvSep.setHeight(1);

                trSep.addView(tvSep);
                mTableLayout.addView(trSep, trParamsSep);
            }
        }
    }
}
