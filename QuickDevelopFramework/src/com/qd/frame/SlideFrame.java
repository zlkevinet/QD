//project copyright
package com.qd.frame;

import com.qd.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * 滑动框架
 * 可以实现左右或上下滑动的布局视图
 * 1.实现手动配置
 * 2.具有标题栏
 * 3.可实现分TAB加载内容
 * @author Jimmy.Z
 * @since 2011-10-27
 */
public class SlideFrame extends ViewGroup{
    //===============================//
    // static field
    //===============================//
    public static final int HORIZONTAL = 0; 
    public static final int VERTICAL = 1; 
    
    //===============================//
    // field
    //===============================//
    
    private int mOrientation = HORIZONTAL;
    private int mTitleItemIdsArray;
    
    private View mSlideTitle;
    private View mSlideContain;
    private View mSlideDivider;
    
    public SlideFrame(Context context) {
        super(context);
        initLayout();
    }
    
    public SlideFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlideFrame);
        mOrientation = a.getInt(R.styleable.SlideFrame_stackOrientation, HORIZONTAL);
        a.recycle();
    }
    
    //===============================//
    // override method
    //===============================//

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        
    }
    
    //===============================//
    // method
    //===============================//

    private void initLayout(){
        
    }
    //===============================//
    // inner class
    //===============================//

}
