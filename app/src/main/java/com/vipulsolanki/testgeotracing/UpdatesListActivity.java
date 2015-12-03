package com.vipulsolanki.testgeotracing;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vipulsolanki.testgeotracing.model.LocationUpdateModel;

import java.util.concurrent.TimeUnit;

import io.realm.Realm;
import io.realm.RealmResults;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class UpdatesListActivity extends AppCompatActivity {
    LinearLayout dataList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates_list);

        dataList = (LinearLayout) findViewById(R.id.data_list);

        refreshUI();
    }

    private void refreshUI() {
        LayoutInflater inflator = LayoutInflater.from(getApplicationContext());
        Realm realm = Realm.getDefaultInstance();
        dataList.removeAllViews();
        RealmResults<LocationUpdateModel> results = realm.where(LocationUpdateModel.class).findAll();
        LocationUpdateModel firstModel = results.get(0);
        long startTime = firstModel.getTimestamp();
        for (LocationUpdateModel model : results) {
            ViewGroup rowView = (ViewGroup) inflator.inflate(R.layout.data_row, null);
            TextView time = (TextView) rowView.findViewById(R.id.time);
            TextView latitude = (TextView) rowView.findViewById(R.id.latitude);
            TextView longitude = (TextView) rowView.findViewById(R.id.longitude);
            TextView accuracy = (TextView) rowView.findViewById(R.id.accuracy);

            long timestamp = model.getTimestamp()-startTime;
            if (timestamp > 0) {
                long seconds = SECONDS.convert(timestamp, MILLISECONDS);
                time.setText(String.valueOf(seconds)+"s");
            } else {
                time.setText("?");
            }

            latitude.setText(String.valueOf(model.getLatitude()));
            longitude.setText(String.valueOf(model.getLongitude()));
            accuracy.setText(String.valueOf(model.getAccuracy()));

            dataList.addView(rowView);
        }
        realm.close();
    }

    public static Intent getLaunchIntent(Context context) {
        Intent intent = new Intent(context, UpdatesListActivity.class);
        return intent;
    }
}
