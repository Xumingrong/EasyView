package com.meteor.view;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.meteor.view.picker.EasyPickerView;
import com.meteor.view.picker.EasyStringPickerView;
import com.meteor.view.seekbar.EasySeekBarView;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements EasyPickerView.OnSelectedListener, View.OnClickListener, EasySeekBarView.OnProgressValueListener {
    private ImageView pickerPrevious;
    private EasyStringPickerView stringPickerView;
    private ImageView pickerNext;
    private EasySeekBarView seekBarView;
    private TextView seekBarValue;

    private String[] data = new String[]{"1000", "1100", "1200", "1300", "1400", "1500", "1600", "1700", "1800", "1900", "2000", "2100", "2200", "2300", "2400", "2500", "2600"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        pickerPrevious = (ImageView) findViewById(R.id.picker_previous);
        stringPickerView = (EasyStringPickerView) findViewById(R.id.string_picker_view);
        pickerNext = (ImageView) findViewById(R.id.picker_next);
        seekBarView = (EasySeekBarView) findViewById(R.id.seek_bar_view);
        seekBarValue = (TextView) findViewById(R.id.seek_bar_value);
        pickerPrevious.setOnClickListener(this);
        stringPickerView.setOnSelectedListener(this);
        pickerNext.setOnClickListener(this);
        seekBarView.setOnProgressValueListener(this);
        stringPickerView.setData(new ArrayList<CharSequence>(Arrays.asList(data)));
        stringPickerView.setSelectedPosition(2);

        seekBarView.setNumericalEquivalence(100);


    }

    @Override
    public void onSelected(EasyPickerView easyPickerView, int position) {
        Toast.makeText(MainActivity.this, "" + (position + 1), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.picker_previous:
                stringPickerView.setPrePosition();
                break;
            case R.id.picker_next:
                stringPickerView.setNextPosition();
                break;
        }
    }

    @Override
    public void onProgressValue(final int value) {
        Log.e("onProgressValue", "" + value);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                seekBarValue.setText("" + value);
            }
        });
    }
}
