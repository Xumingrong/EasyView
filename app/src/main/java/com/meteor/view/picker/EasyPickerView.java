package com.meteor.view.picker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.widget.Scroller;

import com.meteor.view.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者：Meteor on 2019/7/11 14:53
 * 邮箱：15537171227@163.com
 */
public abstract class EasyPickerView<T> extends View {
    private GestureDetector gestureDetector;//手势监听类
    private Scroller scroller;//封装了滑动
    private OnSelectedListener listener;

    private boolean isDisallowInterceptTouch = false;//是否允许父组件拦截触摸：true为不允许拦截，false为允许拦截
    private boolean isFling;//是否在惯性滑动
    private boolean isMovingCenter;//是否正在滑向中间
    private boolean isAutoScrolling = false;//是否正在自动滑动
    private boolean disallowTouch = false;//不允许触摸

    private int lastScrollX = 0;//Scroller的坐标X
    private int lastScrollY = 0;//Scroller的坐标Y

    private float lastMoveX;//触摸的坐标X
    private float lastMoveY;//触摸的坐标Y

    private float moveLength = 0;//item移动长度，负数表示向上/左移动，正数表示向下/右移动


    private int selected;//当前选中的item下标
    private List<T> data;
    private int itemHeight = 0; // 每个条目的高度,当垂直滚动时，高度=measureHeight／visibleItemCount
    private int itemWidth = 0; // 每个条目的宽度，当水平滚动时，宽度=measureWidth／visibleItemCount
    private int itemSize;//当垂直滚动时，mItemSize = mItemHeight;水平滚动时，mItemSize = mItemWidth
    private int centerPosition = -1;//中间item的位置，0<=centerPosition＜visibleItemCount，默认为 visibleItemCount / 2
    private int centerX;//中间item的起始坐标x(不考虑偏移),当垂直滚动时，x = mCenterPosition*mItemWidth
    private int centerY;//中间item的起始坐标y(不考虑偏移),当垂直滚动时，y= mCenterPosition*mItemHeight
    private int centerPoint;//当垂直滚动时，mCenterPoint = mCenterY;水平滚动时，mCenterPoint = mCenterX

    private boolean isHorizontalScroll = false;//是否水平滑动
    private boolean canTap = true;//单击切换选项或触发点击监听器

    private int visibleItemCount = 5;//可见item个数
    private boolean isInertiaScroll = true;//快速滑动时：是否惯性滚动一段距离
    private boolean isCirculation = true;//是否循环滚动

    private ValueAnimator autoValueAnimator;//属性动画
    private final static SlotInterpolator autoScrollInterpolator = new SlotInterpolator();

    private Paint paint;
    private Drawable centerItemBackground = null;//中间选中item的背景色
    private boolean drawAllItem = false; // 是否绘制每个item(包括在边界外的item)
    private boolean hasCallSelectedListener = false; // 用于标志第一次设置selected时把事件通知给监听器

    private int selectedOnTouch;//触摸选择


    public EasyPickerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EasyPickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        gestureDetector = new GestureDetector(getContext(), new FlingOnGestureListener());
        scroller = new Scroller(getContext());
        autoValueAnimator = ValueAnimator.ofInt(0, 0);

        //初始化画笔样式
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        init(attrs);
    }

    //初始化属性集合
    private void init(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.EasyPickerView);
            //设置居中item背景色
            if (typedArray.hasValue(R.styleable.EasyPickerView_m_center_item_background)) {
                setCenterItemBackground(typedArray.getDrawable(R.styleable.EasyPickerView_m_center_item_background));
            }
            setVisibleItemCount(typedArray.getInt(R.styleable.EasyPickerView_m_visible_item_count, getVisibleItemCount())); //可见item个数
            setCenterPosition(typedArray.getInt(R.styleable.EasyPickerView_m_center_item_position, getCenterPosition()));//设置中间位置
            setIsCirculation(typedArray.getBoolean(R.styleable.EasyPickerView_m_is_circulation, isIsCirculation()));//设置是否循环滚动
            setDisallowInterceptTouch(typedArray.getBoolean(R.styleable.EasyPickerView_m_disallow_intercept_touch, isDisallowInterceptTouch()));//父类拦截事件
            setHorizontal(typedArray.getInt(R.styleable.EasyPickerView_m_orientation, isHorizontalScroll() ? 1 : 2) == 1);//滑动方向
            typedArray.recycle();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        reset();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (data == null || data.size() <= 0) {
            return;
        }
        // 选中item的背景色
        if (centerItemBackground != null) {
            centerItemBackground.draw(canvas);
        }

        //只绘制可见的item
        int length = Math.max(centerPosition + 1, visibleItemCount - centerPosition);
        int position;
        int start = Math.min(length, data.size());
        if (drawAllItem) {
            start = data.size();
        }

        //上下两边
        for (int i = start; i >= 1; i--) { // 先从远离中间位置的item绘制，当item内容偏大时，较近的item覆盖在较远的上面
            if (drawAllItem || i <= centerPosition + 1) {  // 上面的items,相对位置为 -i
                position = selected - i < 0 ? data.size() + selected - i : selected - i;
                // 传入位置信息，绘制item
                if (isCirculation) {
                    drawItem(canvas, data, position, -i, moveLength, centerPoint + moveLength - i * itemSize);
                } else if (selected - i >= 0) { // 非循环滚动
                    drawItem(canvas, data, position, -i, moveLength, centerPoint + moveLength - i * itemSize);
                }
            }
            if (drawAllItem || i <= visibleItemCount - centerPosition) {  // 下面的items,相对位置为 i
                position = selected + i >= data.size() ? selected + i - data.size() : selected + i;
                // 传入位置信息，绘制item
                if (isCirculation) {
                    drawItem(canvas, data, position, i, moveLength, centerPoint + moveLength + i * itemSize);
                } else if (selected + i < data.size()) { // 非循环滚动
                    drawItem(canvas, data, position, i, moveLength, centerPoint + moveLength + i * itemSize);
                }
            }
        }
        // 选中的item
        drawItem(canvas, data, selected, 0, moveLength, centerPoint + moveLength);

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (disallowTouch) {
            return true;
        }
        // 按下监听
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selectedOnTouch = selected;
                break;
        }

        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (isHorizontalScroll) {
                    if (Math.abs(event.getX() - lastMoveX) < 0.1f) {
                        return true;
                    }
                    moveLength += event.getX() - lastMoveX;
                } else {
                    if (Math.abs(event.getY() - lastMoveY) < 0.1f) {
                        return true;
                    }
                    moveLength += event.getY() - lastMoveY;
                }
                lastMoveX = event.getX();
                lastMoveY = event.getY();
                checkCirculation();
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                lastMoveX = event.getX();
                lastMoveY = event.getY();
                if (moveLength == 0) {
                    if (selectedOnTouch != selected) { //前后发生变化
                        notifySelected();
                    }
                } else {
                    moveToCenter(); // 滚动到中间位置
                }
                break;
        }
        return true;
    }

    /**
     * 绘制item
     *
     * @param canvas
     * @param data       　数据集
     * @param position   在data数据集中的位置
     * @param relative   相对中间item的位置,relative==0表示中间item,relative<0表示上（左）边的item,relative>0表示下(右)边的item
     * @param moveLength 中间item滚动的距离，moveLength<0则表示向上（右）滚动的距离，moveLength＞0则表示向下（左）滚动的距离
     * @param top        当前绘制item的坐标,当垂直滚动时为顶部y的坐标；当水平滚动时为item最左边x的坐标
     */
    public abstract void drawItem(Canvas canvas, List<T> data, int position, int relative, float moveLength, float top);


    /*
        快速滑动时，惯性滑动一段距离
         */
    private class FlingOnGestureListener extends GestureDetector.SimpleOnGestureListener {
        private boolean isScrollingLastTime;//上次是否从滚动状态终止

        //用户按下屏幕的时候的回调
        @Override
        public boolean onDown(MotionEvent e) {
            if (isDisallowInterceptTouch) {
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
            }
            isScrollingLastTime = isScrolling();
            cancelScroll();//点击取消所有滚动效果
            lastMoveX = e.getX();
            lastMoveY = e.getY();
            return true;
        }

        //用户执行抛操作之后的回调
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (isInertiaScroll) {
                cancelScroll();
                if (isHorizontalScroll) {
                    fling(moveLength, velocityX);
                } else {
                    fling(moveLength, velocityY);
                }
            }
            return true;
        }

        //用户手指松开（UP事件）的时候如果没有执行onScroll()和onLongPress()这两个回调的话，就会回调这个，说明这是一个点击抬起事件，但是不能区分是否双击事件的抬起。
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            lastMoveX = e.getX();
            lastMoveY = e.getY();
            float lastMove = 0;
            if (isHorizontalScroll) {
                centerPoint = centerX;
                lastMove = lastMoveX;
            } else {
                centerPoint = centerY;
                lastMove = lastMoveY;
            }
            if (canTap && !isScrollingLastTime) {
                if (lastMove >= centerPoint && lastMove <= centerPoint + itemSize) {//点击中间item，回调点击事件
                    performClick();
                } else if (lastMove < centerPoint) {
                    int move = itemSize;
                    autoScrollTo(move, 150, autoScrollInterpolator, false);
                } else {
                    int move = -itemSize;
                    autoScrollTo(move, 150, autoScrollInterpolator, false);
                }
            } else {
                moveToCenter();
            }
            return true;
        }
    }

    private static class SlotInterpolator implements Interpolator {

        @Override
        public float getInterpolation(float input) {
            return (float) (Math.cos((input + 1) * Math.PI) / 2.0f) + 0.5f;
        }
    }

    //是否正在滚动
    public boolean isScrolling() {
        return isFling || isAutoScrolling || isMovingCenter;
    }

    //是否默认滚动
    public boolean isAutoScrolling() {
        return isAutoScrolling;
    }

    //取消滚动
    public void cancelScroll() {
        lastScrollX = 0;
        lastScrollY = 0;
        isFling = false;
        isMovingCenter = false;
        scroller.abortAnimation();//停止动画
        stopAutoScroll();

    }

    //停止自动滚动
    public void stopAutoScroll() {
        isAutoScrolling = false;
        autoValueAnimator.cancel();
    }

    //惯性滑动
    private void fling(float from, float vel) {
        if (isHorizontalScroll) {
            lastScrollX = (int) from;
            scroller.fling((int) from, 0, (int) vel, 0, -10 * itemWidth, 10 * itemWidth, 0, 0);//最多可以惯性滑动10个item
        } else {
            lastScrollY = (int) from;
            scroller.fling(0, (int) from, 0, (int) vel, 0, 0, -10 * itemHeight, 10 * itemHeight);//最多可以惯性滑动10个item
        }
        isFling = true;
        invalidate();
    }

    /**
     * @param endY         　需要滚动到的位置
     * @param duration     　滚动时间
     * @param interpolator
     * @param canIntercept 能否终止滚动，比如触摸屏幕终止滚动
     */
    public void autoScrollTo(final int endY, long duration, final Interpolator interpolator, boolean canIntercept) {
        if (isAutoScrolling) {
            return;
        }
        final boolean temp = disallowTouch;
        disallowTouch = !canIntercept;
        isAutoScrolling = true;
        autoValueAnimator.cancel();
        autoValueAnimator.setIntValues(0, endY);//动画结束时属性的值。
        autoValueAnimator.setInterpolator(interpolator);//插值器，与补间动画中的作用相同，可以共用。
        autoValueAnimator.setDuration(duration);//执行时间
        autoValueAnimator.removeAllUpdateListeners();//activity退出后cancel掉动画，activity依然无法被释放(加一句监听器的移除)
        autoValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float rate = animation.getCurrentPlayTime() * 1f / animation.getAnimatedFraction();
                computeScroll((int) animation.getAnimatedValue(), endY, rate);
            }
        });
        autoValueAnimator.removeAllUpdateListeners();
        autoValueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                isAutoScrolling = false;
                disallowTouch = temp;
            }
        });
        autoValueAnimator.start();
    }

    /**
     * 计算滚动
     *
     * @param curr
     * @param end
     * @param rate:当前速度
     */
    public void computeScroll(int curr, int end, float rate) {
        if (rate < 1) {//正在滚动
            if (isHorizontalScroll) {
                // 可以把scroller看做模拟的触屏滑动操作，lastScrollX为上次滑动的坐标
                moveLength = moveLength + curr - lastScrollX;
                lastScrollX = curr;
            } else {
                // 可以把scroller看做模拟的触屏滑动操作，lastScrollY为上次滑动的坐标
                moveLength = moveLength + curr - lastScrollY;
                lastScrollY = curr;
            }
            checkCirculation();
            invalidate();
        } else {//滚动完毕
            isMovingCenter = false;
            lastScrollX = 0;
            lastScrollY = 0;
            // 直接居中，不通过动画
            if (moveLength > 0) { //// 向下滑动
                if (moveLength < itemSize / 2) {
                    moveLength = 0;
                } else {
                    moveLength = itemSize;
                }
            } else {
                if (-moveLength < itemSize / 2) {
                    moveLength = 0;
                } else {
                    moveLength = -itemSize;
                }
            }
            checkCirculation();
            notifySelected();
            invalidate();
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) { // 正在滚动
            if (isHorizontalScroll) {
                // 可以把scroller看做模拟的触屏滑动操作，mLastScrollX为上次滑动的坐标
                moveLength = moveLength + scroller.getCurrX() - lastScrollX;
            } else {
                // 可以把scroller看做模拟的触屏滑动操作，mLastScrollY为上次滑动的坐标
                moveLength = moveLength + scroller.getCurrY() - lastScrollY;
            }
            lastScrollY = scroller.getCurrY();
            lastScrollX = scroller.getCurrX();
            checkCirculation(); //　检测当前选中的item
            invalidate();
        } else { // 滚动完毕
            if (isFling) {
                isFling = false;
                if (moveLength == 0) { //惯性滑动后的位置刚好居中的情况
                    notifySelected();
                } else {
                    moveToCenter(); // 滚动到中间位置
                }
            } else if (isMovingCenter) { // 选择完成，回调给监听器
                notifySelected();
            }
        }
    }

    // 检测当前选择的item位置
    private void checkCirculation() {
        if (moveLength >= itemSize) { // 向下滑动
            // 该次滚动距离中越过的item数量
            int span = (int) (moveLength / itemSize);
            selected -= span;
            if (selected < 0) {  // 滚动顶部，判断是否循环滚动
                if (isCirculation) {
                    do {
                        selected = data.size() + selected;
                    } while (selected < 0); // 当越过的item数量超过一圈时
                    moveLength = (moveLength - itemSize) % itemSize;
                } else { // 非循环滚动
                    selected = 0;
                    moveLength = itemSize;
                    if (isFling) { // 停止惯性滑动，根据computeScroll()中的逻辑，下一步将调用moveToCenter()
                        scroller.forceFinished(true);
                    }
                    if (isMovingCenter) { //  移回中间位置
                        scroll(moveLength, 0);
                    }
                }
            } else {
                moveLength = (moveLength - itemSize) % itemSize;
            }

        } else if (moveLength <= -itemSize) { // 向上滑动
            // 该次滚动距离中越过的item数量
            int span = (int) (-moveLength / itemSize);
            selected += span;
            if (selected >= data.size()) { // 滚动末尾，判断是否循环滚动
                if (isCirculation) {
                    do {
                        selected = selected - data.size();
                    } while (selected >= data.size()); // 当越过的item数量超过一圈时
                    moveLength = (moveLength + itemSize) % itemSize;
                } else { // 非循环滚动
                    selected = data.size() - 1;
                    moveLength = -itemSize;
                    if (isFling) { // 停止惯性滑动，根据computeScroll()中的逻辑，下一步将调用moveToCenter()
                        scroller.forceFinished(true);
                    }
                    if (isMovingCenter) { //  移回中间位置
                        scroll(moveLength, 0);
                    }
                }
            } else {
                moveLength = (moveLength + itemSize) % itemSize;
            }
        }
    }

    //平滑滑动
    private void scroll(float from, int to) {
        if (isHorizontalScroll) {
            lastScrollX = (int) from;
            isMovingCenter = true;
            scroller.startScroll((int) from, 0, 0, 0);
            scroller.setFinalX(to);
        } else {
            lastScrollY = (int) from;
            isMovingCenter = true;
            scroller.startScroll(0, (int) from, 0, 0);
            scroller.setFinalY(to);
        }
        invalidate();
    }

    private void moveToCenter() {
        if (!scroller.isFinished() || isFling || moveLength == 0) {
            return;
        }
        cancelScroll();

        //向下/右滑动
        if (moveLength > 0) {
            if (isHorizontalScroll) {
                if (moveLength < itemWidth / 2) {
                    scroll(moveLength, 0);
                } else {
                    scroll(moveLength, itemWidth);
                }
            } else {
                if (moveLength < itemHeight / 2) {
                    scroll(moveLength, 0);
                } else {
                    scroll(moveLength, itemHeight);
                }
            }
        } else {//向上/左滑动
            if (isHorizontalScroll) {
                if (-moveLength < itemWidth / 2) {
                    scroll(moveLength, 0);
                } else {
                    scroll(moveLength, -itemWidth);
                }
            } else {
                if (-moveLength < itemHeight / 2) {
                    scroll(moveLength, 0);
                } else {
                    scroll(moveLength, -itemHeight);
                }
            }
        }
    }

    //重置
    private void reset() {
        if (centerPosition < 0) {
            centerPosition = visibleItemCount / 2;
        }

        if (isHorizontalScroll) {
            itemHeight = getMeasuredHeight();
            itemWidth = getMeasuredWidth() / visibleItemCount;

            centerY = 0;
            centerX = centerPosition * itemWidth;

            itemSize = itemWidth;
            centerPoint = centerX;
        } else {
            itemHeight = getMeasuredHeight() / visibleItemCount;
            itemWidth = getMeasuredWidth();

            centerY = centerPosition * itemHeight;
            centerX = 0;

            itemSize = itemHeight;
            centerPoint = centerY;
        }

        if (centerItemBackground != null) {
            centerItemBackground.setBounds(centerX, centerY, centerX + itemWidth, centerY + itemHeight);
        }
    }

    public void autoScrollFast(final int position, long duration, float speed, final Interpolator interpolator) {
        if (isAutoScrolling || !isCirculation) {
            return;
        }
        cancelScroll();
        isAutoScrolling = true;

        int length = (int) (speed * duration);
        int circle = (int) (length * 1f / (data.size() * itemSize) + 0.5f); // 圈数
        circle = circle <= 0 ? 1 : circle;

        int aPlan = circle * (data.size()) * itemSize + (selected - position) * itemSize;
        int bPlan = aPlan + (data.size()) * itemSize; // 多一圈
        // 让其尽量接近length
        final int end = Math.abs(length - aPlan) < Math.abs(length - bPlan) ? aPlan : bPlan;

        autoValueAnimator.cancel();
        autoValueAnimator.setIntValues(0, end);
        autoValueAnimator.setInterpolator(interpolator);
        autoValueAnimator.setDuration(duration);
        autoValueAnimator.removeAllUpdateListeners();
        if (end != 0) { // itemHeight为0导致endy=0
            autoValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float rate = 0;
                    rate = animation.getCurrentPlayTime() * 1f / animation.getDuration();
                    computeScroll((int) animation.getAnimatedValue(), end, rate);
                }
            });
            autoValueAnimator.removeAllListeners();
            autoValueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    isAutoScrolling = false;
                }
            });
            autoValueAnimator.start();
        } else {
            computeScroll(end, end, 1);
            isAutoScrolling = false;
        }
    }

    //自动滚动，默认速度为 0.6dp/ms
    public void autoScrollFast(final int position, long duration) {
        float speed = dip2px(0.6f);
        autoScrollFast(position, duration, speed, autoScrollInterpolator);
    }

    //自动滚动
    public void autoScrollFast(final int position, long duration, float speed) {
        autoScrollFast(position, duration, speed, autoScrollInterpolator);
    }

    //滚动到指定位置
    public void autoScrollToPosition(int toPosition, long duration, final Interpolator interpolator) {
        toPosition = toPosition % data.size();
        final int endY = (selected - toPosition) * itemHeight;
        autoScrollTo(endY, duration, interpolator, false);
    }

    public int dip2px(float dipVlue) {
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        float sDensity = metrics.density;
        return (int) (dipVlue * sDensity + 0.5F);
    }

    //获取数据源
    public List<T> getData() {
        return data;
    }

    //设置数据源
    public void setData(List<? extends T> data) {
        if (data == null) {
            this.data = new ArrayList<T>();
        } else {
            this.data = (List<T>) data;
        }
    }

    //获取选中item
    public T getSelectedItem() {
        return data.get(selected);
    }

    //获取选中position
    public int getSelectedPosition() {
        return selected;
    }

    //设置选中position
    public void setSelectedPosition(int position) {
        if (position < 0 || position > data.size() - 1 || (position == selected && hasCallSelectedListener)) {
            return;
        }

        hasCallSelectedListener = true;
        selected = position;
        invalidate();
        notifySelected();
    }

    //点击选择上一个
    public void setPrePosition() {
        if (selected == 0) {
            return;
        }
        hasCallSelectedListener = true;
        selected = selected - 1;
        invalidate();
        notifySelected();
    }

    //点击选择下一个
    public void setNextPosition() {
        if (selected >= data.size() - 1) {
            return;
        }
        hasCallSelectedListener = true;
        selected = selected + 1;
        invalidate();
        notifySelected();
    }

    public boolean isInertiaScroll() {
        return isInertiaScroll;
    }

    public void setInertiaScroll(boolean inertiaScroll) {
        this.isInertiaScroll = inertiaScroll;
    }

    //居中item背景色（Drawable）
    public void setCenterItemBackground(Drawable centerItemBackground) {
        this.centerItemBackground = centerItemBackground;
        this.centerItemBackground.setBounds(centerX, centerY, centerX + itemWidth, centerY + itemHeight);
        invalidate();
    }

    //居中item背景色（int）
    public void setCenterItemBackground(int centerItemBackground) {
        this.centerItemBackground = new ColorDrawable(centerItemBackground);
        this.centerItemBackground.setBounds(centerX, centerY, centerX + itemWidth, centerY + itemHeight);
        invalidate();
    }

    public Drawable getCenterItemBackground() {
        return this.centerItemBackground;
    }

    //获取可见item个数
    public int getVisibleItemCount() {
        return visibleItemCount;
    }

    //设置可见item个数
    public void setVisibleItemCount(int visibleItemCount) {
        this.visibleItemCount = visibleItemCount;
        reset();
        invalidate();
    }

    //设置中心位置，中间item的位置，0 <= centerPosition <= mVisibleItemCount
    public void setCenterPosition(int centerPosition) {
        if (centerPosition < 0) {
            this.centerPosition = 0;
        } else if (centerPosition >= visibleItemCount) {
            this.centerPosition = visibleItemCount - 1;
        } else {
            this.centerPosition = centerPosition;
        }
        centerY = centerPosition * itemHeight;
        invalidate();
    }

    //刷新选中
    private void notifySelected() {
        moveLength = 0;
        cancelScroll();
        if (this.listener != null) {
            // 告诉监听器选择完毕
            this.listener.onSelected(EasyPickerView.this, selected);
        }
    }

    //获取中心位置
    public int getCenterPosition() {
        return this.centerPosition;
    }

    //循环状态
    public boolean isIsCirculation() {
        return this.isCirculation;
    }

    //设置是否循环
    public void setIsCirculation(boolean isCirculation) {
        this.isCirculation = isCirculation;
    }

    //是否允许父元素拦截事件，设置true后可以保证在ScrollView下正常滚动
    public void setDisallowInterceptTouch(boolean disallowInterceptTouch) {
        this.isDisallowInterceptTouch = disallowInterceptTouch;
    }

    //父元素拦截事件
    public boolean isDisallowInterceptTouch() {
        return isDisallowInterceptTouch;
    }

    public int getItemHeight() {
        return itemHeight;
    }

    public int getItemWidth() {
        return itemWidth;
    }

    /**
     * @return 当垂直滚动时，mItemSize = mItemHeight;水平滚动时，mItemSize = mItemWidth
     */
    public int getItemSize() {
        return itemSize;
    }

    /**
     * @return 中间item的起始坐标x(不考虑偏移), 当垂直滚动时，x = mCenterPosition*mItemWidth
     */
    public int getCenterX() {
        return centerX;
    }

    /**
     * @return 中间item的起始坐标y(不考虑偏移), 当垂直滚动时，y= mCenterPosition*mItemHeight
     */
    public int getCenterY() {
        return centerY;
    }

    /**
     * @return 当垂直滚动时，mCenterPoint = mCenterY;水平滚动时，mCenterPoint = mCenterX
     */
    public int getCenterPoint() {
        return centerPoint;
    }

    public boolean isDisallowTouch() {
        return disallowTouch;
    }

    /**
     * 设置是否允许手动触摸滚动
     *
     * @param disallowTouch
     */
    public void setDisallowTouch(boolean disallowTouch) {
        this.disallowTouch = disallowTouch;
    }

    public boolean isDrawAllItem() {
        return this.drawAllItem;
    }

    public void setDrawAllItem(boolean drawAllItem) {
        this.drawAllItem = drawAllItem;
    }

    public boolean isHorizontalScroll() {
        return isHorizontalScroll;
    }

    public boolean isVertical() {
        return !isHorizontalScroll;
    }

    public void setHorizontal(boolean horizontal) {
        if (isHorizontalScroll == horizontal) {
            return;
        }
        isHorizontalScroll = horizontal;
        reset();
        if (isHorizontalScroll) {
            itemSize = itemWidth;
        } else {
            itemSize = itemHeight;
        }
        invalidate();
    }

    public void setVertical(boolean vertical) {
        if (isHorizontalScroll == !vertical) {
            return;
        }
        isHorizontalScroll = !vertical;
        reset();
        if (isHorizontalScroll) {
            itemSize = itemWidth;
        } else {
            itemSize = itemHeight;
        }
        invalidate();
    }

    public void setOnSelectedListener(OnSelectedListener listener) {
        this.listener = listener;
    }

    public OnSelectedListener getListener() {
        return this.listener;
    }

    /*
    选中接口
     */
    public interface OnSelectedListener {
        void onSelected(EasyPickerView easyPickerView, int position);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            moveToCenter();
        }
    }
}
