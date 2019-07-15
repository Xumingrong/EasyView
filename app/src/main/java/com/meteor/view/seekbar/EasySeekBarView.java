package com.meteor.view.seekbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v7.widget.AppCompatSeekBar;
import android.util.AttributeSet;
import android.widget.SeekBar;

import com.meteor.view.R;
import com.meteor.view.utils.DensityUtils;

/**
 * 作者：Meteor on 2019/7/15 09:23
 * 邮箱：15537171227@163.com
 */
public class EasySeekBarView extends AppCompatSeekBar implements SeekBar.OnSeekBarChangeListener {
    private Context context;
    private Paint paint;

    private int minimumValue = 100;//最小数值
    private int numericalEquivalence = 100;//数值等差
    private int progressValue;//滑动的指定位置的值

    private OnProgressValueListener valueListener;


    public EasySeekBarView(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public EasySeekBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    public EasySeekBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(getResources().getColor(R.color.white));
        paint.setStrokeWidth(DensityUtils.dp2px(context, (float) 1));
        setOnSeekBarChangeListener(this);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        canvas.drawLine(getPaddingLeft(), getHeight() / 2, getPaddingLeft(), -(getHeight() / 2), paint);
        canvas.drawLine(getWidth() - getPaddingRight(), getHeight() / 2, getWidth() - getPaddingRight(), -(getHeight() / 2), paint);
        super.onDraw(canvas);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        this.progressValue = progress + minimumValue;
        seekBar.setProgress(numericalEquivalence * (progress / numericalEquivalence));
        if (valueListener != null) {
            valueListener.onProgressValue(progressValue);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    //设置数值等差
    public void setNumericalEquivalence(int numericalEquivalence) {
        this.numericalEquivalence = numericalEquivalence;
    }

    public void setOnProgressValueListener(OnProgressValueListener valueListener) {
        this.valueListener = valueListener;
    }

    //获取滑动的指定位置的值
    public interface OnProgressValueListener {
        void onProgressValue(int value);
    }
}
