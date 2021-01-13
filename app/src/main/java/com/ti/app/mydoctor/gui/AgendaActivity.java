package com.ti.app.mydoctor.gui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.net.Uri;
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
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.Appointment;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserMeasure;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE;

public class AgendaActivity extends AppCompatActivity implements View.OnClickListener{

    private TableLayout mTableLayout;
    private int appointmentsOffset;
    ArrayList<Appointment> appointments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Inizializza l'ActionBAr
        ActionBar customActionBar = getSupportActionBar();
        if (customActionBar != null) {
            //Setta il gradiente di sfondo della action bar
            Drawable cd = ContextCompat.getDrawable(this, R.drawable.action_bar_background_color);
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
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void loadData() {

        int leftRowMargin=0;
        int topRowMargin=0;
        int rightRowMargin=0;
        int bottomRowMargin = 0;
        int textSize, smallTextSize;

        User user = UserManager.getUserManager().getCurrentUser();

        textSize = (int) getResources().getDimension(R.dimen.font_size_medium);
        smallTextSize = (int) getResources().getDimension(R.dimen.font_size_small);
        DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());

        List<UserMeasure> data = MeasureManager.getMeasureManager().getBiometricUserMeasures(user);

        mTableLayout.removeAllViews();

        int i = 0;
        for(UserMeasure row:data) {
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
            tv2.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
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
            i++;
        }

        appointmentsOffset = i;

        // delete appointments oder than 2 days
        MeasureManager.getMeasureManager().deleteOldUserAppoinments(user.getId(),
                java.lang.System.currentTimeMillis() - 1000*60*60*24*2);
        List<Appointment> apps = MeasureManager.getMeasureManager().getUserApointments(user.getId());

        long startTime = atStartOfDay(false);
        long endTime = atStartOfDay(true);

        for (Appointment app:apps) {

            long time = app.getTimestamp();
            if ((time < startTime) || (time > endTime))
                continue;

            appointments.add(app);
            // data columns
            final ImageView iv = new ImageView(this);
            iv.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT));
            iv.setPadding(10, 10, 10, 10);
            iv.setBackgroundColor(Color.parseColor("#f8f8f8"));
            iv.setImageResource(R.drawable.small_televisit_icon);

            final TextView tv2 = new TextView(this);
            tv2.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT));
            tv2.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            tv2.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            tv2.setPadding(5, 10, 0, 10);
            tv2.setBackgroundColor(Color.parseColor("#f8f8f8"));
            tv2.setTextColor(Color.parseColor("#000000"));
            tv2.setTypeface(Typeface.create("roboto_condensed", Typeface.NORMAL));
            tv2.setText(app.getTitle());
            //tv2.setText(AppResourceManager.getResource().getString("AppointmentType." + app.getType()));

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
            tv3.setText(df.format(new Date(app.getTimestamp())));

            // add table row
            final TableRow tr = new TableRow(this);
            tr.setId(i);
            tr.setGravity(Gravity.CENTER_VERTICAL);
            TableLayout.LayoutParams trParams = new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT);
            trParams.setMargins(leftRowMargin, topRowMargin, rightRowMargin, bottomRowMargin);
            tr.setPadding(0,0,0,0);
            tr.setLayoutParams(trParams);
            tr.setClickable(true);
            tr.setOnClickListener(this);
            tr.addView(iv);
            tr.addView(tv2);
            tr.addView(tv3);
            mTableLayout.addView(tr, trParams);

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
            i++;
        }
    }

    private long atStartOfDay(boolean tomorrow) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (tomorrow)
            calendar.add(Calendar.DATE, 1);
        return calendar.getTime().getTime();
    }

    @SuppressLint("ResourceType")
    @Override
    public void onClick(View v) {
        if (v.getId() < appointmentsOffset)
            return;
        Appointment app = appointments.get(v.getId() - appointmentsOffset);
        showAppointmentDialog(app);
    }

    private void showAppointmentDialog(final Appointment app) {
        setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(app.getData());
        builder.setTitle(app.getTitle());
        if ((System.currentTimeMillis() > (app.getTimestamp() - 1000*60*10)) && (System.currentTimeMillis() < (app.getTimestamp() + 1000*60*60)))
            builder.setPositiveButton(R.string.start, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(app.getUrl()));
                startActivity(intent);
            }
        });
        builder.setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }
}
