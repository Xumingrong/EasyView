package com.meteor.view.picker;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;

import com.meteor.view.R;
import com.meteor.view.utils.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 作者：Meteor on 2019/7/12 15:28
 * 邮箱：15537171227@163.com
 */
public class EasyStringPickerView extends EasyPickerView<CharSequence> {
    private TextPaint textPaint;
    private int minTextSize = 24; // 最小的字体
    private int maxTextSize = 32; // 最大的字体
    private int startColor = Color.BLACK;//中间选中item的颜色
    private int endColor = Color.GRAY;//上下（左右）两边的颜色
    private int maxLineWidth = -1;//最大的行宽,默认为itemWidth.超过后文字自动换行
    private Layout.Alignment alignment = Layout.Alignment.ALIGN_CENTER; // 对齐方式,默认居中

    public EasyStringPickerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EasyStringPickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        //初始化画笔
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.BLACK);
        init(attrs);
    }

    //初始化属性集合
    private void init(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.EasyStringPickerView);
            minTextSize = typedArray.getDimensionPixelSize(R.styleable.EasyStringPickerView_m_min_text_size, minTextSize);
            maxTextSize = typedArray.getDimensionPixelSize(R.styleable.EasyStringPickerView_m_max_text_size, maxTextSize);
            startColor = typedArray.getColor(R.styleable.EasyStringPickerView_m_start_color, startColor);
            endColor = typedArray.getColor(R.styleable.EasyStringPickerView_m_end_color, endColor);
            maxLineWidth = typedArray.getDimensionPixelSize(R.styleable.EasyStringPickerView_m_max_line_width, maxLineWidth);
            int align = typedArray.getInt(R.styleable.EasyStringPickerView_m_alignment, 1);
            if (align == 2) {
                alignment = Layout.Alignment.ALIGN_NORMAL;
            } else if (align == 3) {
                alignment = Layout.Alignment.ALIGN_OPPOSITE;
            } else {
                alignment = Layout.Alignment.ALIGN_CENTER;
            }
            typedArray.recycle();
        }

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (maxLineWidth < 0) {
            maxLineWidth = getItemWidth();
        }
    }

    @Override
    public void drawItem(Canvas canvas, List<CharSequence> data, int position, int relative, float moveLength, float top) {
        CharSequence text = data.get(position);
        int itemSize = getItemSize();

        // 设置文字大小
        if (relative == -1) { // 上一个
            if (moveLength < 0) { // 向上滑动
                textPaint.setTextSize(minTextSize);
            } else { // 向下滑动
                textPaint.setTextSize(minTextSize + (maxTextSize - minTextSize)
                        * moveLength / itemSize);
            }
        } else if (relative == 0) { // 中间item,当前选中
            textPaint.setTextSize(minTextSize + (maxTextSize - minTextSize)
                    * (itemSize - Math.abs(moveLength)) / itemSize);
        } else if (relative == 1) { // 下一个
            if (moveLength > 0) { // 向下滑动
                textPaint.setTextSize(minTextSize);
            } else { // 向上滑动
                textPaint.setTextSize(minTextSize + (maxTextSize - minTextSize)
                        * -moveLength / itemSize);
            }
        } else { // 其他
            textPaint.setTextSize(minTextSize);
        }

        StaticLayout layout = new StaticLayout(text, 0, text.length(), textPaint, maxLineWidth, alignment, 1.0F, 0.0F, true, null, 0);
        float x = 0;
        float y = 0;
        float lineWidth = layout.getWidth();

        if (isHorizontalScroll()) { // 水平滚动
            x = top + (getItemWidth() - lineWidth) / 2;
            y = (getItemHeight() - layout.getHeight()) / 2;
        } else { // 垂直滚动
            x = (getItemWidth() - lineWidth) / 2;
            y = top + (getItemHeight() - layout.getHeight()) / 2;
        }
        // 计算渐变颜色
        computeColor(relative, itemSize, moveLength);
        canvas.save();
        canvas.translate(x, y);
        layout.draw(canvas);
        canvas.restore();
    }

    private void computeColor(int relative, int itemSize, float moveLength) {
        int color = endColor; //其他默认为ｍEndColor
        if (relative == -1 || relative == 1) { // 上一个或下一个
            // 处理上一个item且向上滑动　或者　处理下一个item且向下滑动　，颜色为mEndColor
            if ((relative == -1 && moveLength < 0) || (relative == 1 && moveLength > 0)) {
                color = endColor;
            } else { // 计算渐变的颜色
                float rate = (itemSize - Math.abs(moveLength)) / itemSize;
                color = ColorUtil.computeGradientColor(startColor, endColor, rate);
            }
        } else if (relative == 0) { // 中间item
            float rate = Math.abs(moveLength) / itemSize;
            color = ColorUtil.computeGradientColor(startColor, endColor, rate);
        }
        textPaint.setColor(color);
    }

    //设置颜色：正中间的颜色，上下两边的颜色
    public void setColor(int startColor, int endColor) {
        this.startColor = startColor;
        this.endColor = endColor;
        invalidate();
    }

    //设置文字大小：沒有被选中时的最小文字，沒有被选中时的最小文字
    public void setTextSize(int minText, int maxText) {
        minTextSize = minText;
        maxTextSize = maxText;
        invalidate();
    }

    //最大的行宽,默认为itemWidth.超过后文字自动换行
    public void setMaxLineWidth(int maxLineWidth) {
        this.maxLineWidth = maxLineWidth;
    }

    //最大的行宽,默认为itemWidth.超过后文字自动换行
    public void setAlignment(Layout.Alignment alignment) {
        this.alignment = alignment;
    }
}
