package app.simple.felicity.decorations.coverflow.containers;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;
import app.simple.felicity.R;
import app.simple.felicity.decorations.coverflow.containers.interfaces.IRemovableItemsAdapterComponent;
import app.simple.felicity.decorations.coverflow.containers.interfaces.IRemoveFromAdapter;
import app.simple.felicity.decorations.coverflow.general.ToolBox;

public class HorizontalListWithRemovableItems extends HorizontalList {
    private static final int FADE_TIME = 250;
    private static final int SLIDE_TIME = 350;
    private final Rect mTempRect = new Rect();
    private Drawable mRemoveItemIconDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_close); //getResources().getDrawable(R.drawable.ico_delete_asset);
    private Drawable mIconForAnimation;
    private IRemovableItemsAdapterComponent mRemoveListener;
    private int mIconMarginTop = (int) ToolBox.dpToPixels(10, getContext());
    private int mIconMarginRight = (int) ToolBox.dpToPixels(10, getContext());
    private int mIconClickableMarginExtend = (int) ToolBox.dpToPixels(10, getContext());
    private int mDownX;
    private int mDownY;
    private boolean isPointerDown;
    private View mContainingView;
    private int mContainingViewPosition;
    private int mContainingViewIndex;
    private Object mData;
    private int mAnimationLastValue;
    
    private int mAlphaAnimationRunningOnIndex = -1;
    
    private boolean mEditable;
    
    public HorizontalListWithRemovableItems(Context context,
            AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    public HorizontalListWithRemovableItems(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public HorizontalListWithRemovableItems(Context context) {
        this(context, null);
    }
    
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        
        if (!mEditable) {
            return;
        }
        
        final int c = getChildCount();
        final int iw = mRemoveItemIconDrawable.getIntrinsicWidth();
        final int ih = mRemoveItemIconDrawable.getIntrinsicHeight();
        
        View v;
        int r, t;
        Drawable d;
        for (int i = 0; i < c; i++) {
            if (i != mAlphaAnimationRunningOnIndex) {
                d = mRemoveItemIconDrawable;
            } else {
                d = mIconForAnimation;
            }
            
            v = getChildAt(i);
            r = v.getRight();
            t = v.getTop();
            mTempRect.left = r - iw - mIconMarginRight;
            mTempRect.top = t + mIconMarginTop;
            mTempRect.right = r - mIconMarginRight;
            mTempRect.bottom = t + mIconMarginTop + ih;
            d.setBounds(mTempRect);
            d.draw(canvas);
        }
        
    }
    
    @Override
    protected View addAndMeasureChild(View child, int layoutMode) {
        if (layoutMode == LAYOUT_MODE_TO_BEFORE && mAlphaAnimationRunningOnIndex != -1) {
            mAlphaAnimationRunningOnIndex++;
        }
        
        return super.addAndMeasureChild(child, layoutMode);
    }
    
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mEditable || ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return super.onInterceptTouchEvent(ev);
        }
        //only down event will get through initial condition
        
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();
        
        final int iw = mRemoveItemIconDrawable.getIntrinsicWidth();
        final int ih = mRemoveItemIconDrawable.getIntrinsicHeight();
        
        View v;
        int r, t;
        final int c = getChildCount();
        for (int i = 0; i < c; i++) {
            v = getChildAt(i);
            r = v.getRight();
            t = v.getTop();
            mTempRect.left = r - iw - mIconMarginRight - mIconClickableMarginExtend;
            mTempRect.top = t + mIconMarginTop - mIconClickableMarginExtend;
            mTempRect.right = r - mIconMarginRight + mIconClickableMarginExtend;
            mTempRect.bottom = t + mIconMarginTop + ih + mIconClickableMarginExtend;
            
            if (mTempRect.contains(getScrollX() + x, y)) {
                mDownX = x;
                mDownY = y;
                isPointerDown = true;
                
                mContainingView = v;
                mContainingViewPosition = mFirstItemPosition + i;
                mData = mAdapter.getItem(mContainingViewPosition);
                mContainingViewIndex = i;
                
                return true;
            }
        }
        
        isPointerDown = false;
        
        return super.onInterceptTouchEvent(ev);
    }
    
    @SuppressLint ("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isPointerDown) {
            if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                if (ToolBox.getLineLength(ev.getX(), ev.getY(), mDownX, mDownY) < mTouchSlop && mAlphaAnimationRunningOnIndex == -1) {
                    createRemoveAnimations(mContainingViewIndex).start();
                }
                
                isPointerDown = false;
            }
            
            return true;
        } else {
            return super.onTouchEvent(ev);
        }
    }
    
    //	protected void refill(){
    //		if(mAdapter == null) return;
    //
    //		final int leftScreenEdge = getScrollX();
    //		int rightScreenEdge = leftScreenEdge + getWidth();
    //
    //		if(mAlphaAnimationRunningOnIndex != -1) rightScreenEdge += mContainingView.getWidth();
    //
    //		removeNonVisibleViewsLeftToRight(leftScreenEdge);
    //		removeNonVisibleViewsRightToLeft(rightScreenEdge);
    //
    //		refillLeftToRight(leftScreenEdge, rightScreenEdge);
    //		refillRightToLeft(leftScreenEdge);
    //	}
    
    private void onRemoveAnimationFinished(int position, View view, Object item) {
        if (mRemoveListener == null && mAdapter instanceof IRemoveFromAdapter) {
            ((IRemoveFromAdapter) mAdapter).removeItemFromAdapter(position);
        } else {
            assert mRemoveListener != null;
            if (!mRemoveListener.onItemRemove(position, view, item) && mAdapter instanceof IRemoveFromAdapter) {
                ((IRemoveFromAdapter) mAdapter).removeItemFromAdapter(position);
            }
        }
        
        mContainingView = null;
        mContainingViewIndex = -1;
        mContainingViewPosition = -1;
        mData = null;
    }
    
    private Animator createRemoveAnimations(final int removedViewIndex) {
        if (mIconForAnimation == null) {
            mIconForAnimation = mRemoveItemIconDrawable.getConstantState().newDrawable(getResources()).mutate();
        }
        mAlphaAnimationRunningOnIndex = removedViewIndex;
        isScrollingDisabled = true;
        View removed = getChildAt(removedViewIndex);
        
        ObjectAnimator fader = ObjectAnimator.ofFloat(removed, "alpha", 1f, 0f);
        fader.setDuration(FADE_TIME);
        fader.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator anim) {
                mIconForAnimation.setAlpha((int) (255 * ((Float) anim.getAnimatedValue())));
                invalidate(mIconForAnimation.getBounds());
            }
        });
        
        mAnimationLastValue = 0;
        final int distance = removed.getWidth();
        final boolean scrollDuringSlide;
        scrollDuringSlide = mRightEdge != NO_VALUE && getScrollX() + distance > mRightEdge - getWidth();
        
        ValueAnimator slider = ValueAnimator.ofInt(0, -distance);
        slider.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator anim) {
                final int val = (Integer) anim.getAnimatedValue();
                int dx = val - mAnimationLastValue;
                mAnimationLastValue = val;
                
                final int c = getChildCount();
                View v;
                for (int i = removedViewIndex + 1; i < c; i++) {
                    v = getChildAt(i);
                    v.layout(v.getLeft() + dx, v.getTop(), v.getRight() + dx, v.getBottom());
                }
                
                if (scrollDuringSlide) {
                    if (getScrollX() + dx < 0) {
                        dx = -getScrollX();
                    }
                    scrollBy(dx, 0);
                }
            }
        });
        slider.setDuration(SLIDE_TIME);
        
        //		View v;
        //
        //		final float distance = -removed.getWidth();
        //		final ArrayList<Animator> anims = new ArrayList<Animator>();
        //		ObjectAnimator slider = null;
        //		for(int i=removedViewIndex+1; i < getChildCount(); i++){
        //			v = getChildAt(i);
        //			slider = ObjectAnimator.ofFloat(v, "translationX", 0f, distance);
        //			anims.add(slider);
        //		}
        //		if(slider != null) slider.addUpdateListener(new AnimatorUpdateListener() {
        //			@Override
        //			public void onAnimationUpdate(ValueAnimator anim) {
        //				invalidate();
        //			}
        //		});
        //
        //		AnimatorSet sliderSet = new AnimatorSet();
        //		sliderSet.playTogether(anims);
        //		sliderSet.setDuration(SLIDE_TIME);
        
        final AnimatorListener listener = new AnimatorListener() {
            public void onAnimationStart(Animator arg0) {
            }
            
            public void onAnimationRepeat(Animator arg0) {
            }
            
            public void onAnimationCancel(Animator arg0) {
            }
            
            public void onAnimationEnd(Animator arg0) {
                mAlphaAnimationRunningOnIndex = -1;
                isScrollingDisabled = false;
                
                onRemoveAnimationFinished(mContainingViewPosition, mContainingView, mData);
            }
        };
        
        AnimatorSet resultSet = new AnimatorSet();
        resultSet.playSequentially(fader, slider);
        
        resultSet.addListener(listener);
        
        return resultSet;
    }
    
    /**
     * Sets icon for overlay which removes item on click
     */
    public void setRemoveItemIcon(int resId) {
        mRemoveItemIconDrawable = ContextCompat.getDrawable(getContext(), resId);
        mIconForAnimation = null;
    }
    
    public void setRemoveItemIconMarginTop(int px) {
        mIconMarginTop = px;
    }
    
    public void setRemoveItemIconMarginRight(int px) {
        mIconMarginRight = px;
    }
    
    /**
     * @param px margin of icon which is clickable
     */
    public void setClickableMarginOfIcon(int px) {
        mIconClickableMarginExtend = px;
    }
    
    public void setRemoveItemListener(IRemovableItemsAdapterComponent listener) {
        mRemoveListener = listener;
    }
    
    public void setEditable(boolean isEditable) {
        mEditable = isEditable;
    }
    
}
