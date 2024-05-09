package app.simple.felicity.decorations.views;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;
import app.simple.felicity.R;
import app.simple.felicity.adapters.menus.AdapterFloatingMenu;
import app.simple.felicity.decorations.corners.LayoutBackground;
import app.simple.felicity.decorations.overscroll.CustomHorizontalRecyclerView;
import app.simple.felicity.interfaces.menus.BottomMenuCallbacks;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.preferences.LayoutsPreferences;
import app.simple.felicity.preferences.MainPreferences;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.themes.Theme;
import app.simple.felicity.utils.ViewUtils;
import kotlin.Pair;
import kotlin.ranges.RangesKt;

public class FloatingMenuRecyclerView extends CustomHorizontalRecyclerView {
    
    public static final String ACTION_CLOSE_BOTTOM_MENU = "app.simple.inure.ACTION_CLOSE_BOTTOM_MENU";
    public static final String ACTION_OPEN_BOTTOM_MENU = "app.simple.inure.ACTION_OPEN_BOTTOM_MENU";
    /**
     * @noinspection unused
     */
    private static final String TAG = "BottomMenuRecyclerView";
    private final int MIN_ITEMS_THRESHOLD = 12;
    private final IntentFilter intentFilter = new IntentFilter();
    private int containerHeight;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                if (intent.getAction().equals(ACTION_CLOSE_BOTTOM_MENU)) {
                    animate()
                            .translationY(containerHeight)
                            .setDuration(250)
                            .setInterpolator(new AccelerateInterpolator())
                            .start();
                } else if (intent.getAction().equals(ACTION_OPEN_BOTTOM_MENU)) {
                    animate()
                            .translationY(0)
                            .setDuration(250)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
            }
        }
    };
    private int displayWidth;
    private boolean isScrollListenerAdded = false;
    private boolean isInitialized = false;
    private boolean isBottomMenuVisible = true;
    
    public FloatingMenuRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(AttributeSet attributeSet) {
        if (isInEditMode()) {
            return;
        }
        displayWidth = new DisplayMetrics().widthPixels;
        int padding = getResources().getDimensionPixelOffset(R.dimen.popup_padding);
        setPadding(padding, padding, padding, padding);
        setElevation(getResources().getDimensionPixelOffset(R.dimen.app_views_elevation));
        LayoutBackground.setBackground(getContext(), this, attributeSet);
        ViewUtils.INSTANCE.addShadow(this);
        setClipToPadding(false);
        setClipChildren(true);
        
        if (AppearancePreferences.INSTANCE.isAccentColorOnBottomMenu()) {
            setBackgroundTintList(ViewUtils.INSTANCE.toColorStateList(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor()));
        }
        
        intentFilter.addAction(ACTION_CLOSE_BOTTOM_MENU);
        intentFilter.addAction(ACTION_OPEN_BOTTOM_MENU);
        
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(broadcastReceiver, intentFilter);
    }
    
    public void initBottomMenu(ArrayList <Pair <Integer, Integer>> bottomMenuItems, BottomMenuCallbacks bottomMenuCallbacks) {
        AdapterFloatingMenu adapterBottomMenu = new AdapterFloatingMenu(bottomMenuItems);
        adapterBottomMenu.setMenuCallbacks(bottomMenuCallbacks);
        
        if (getAdapter() == null) {
            setLayoutAnimation(AnimationUtils.loadLayoutAnimation(getContext(), R.anim.list_animation_controller));
        } else {
            setLayoutAnimation(AnimationUtils.loadLayoutAnimation(getContext(), R.anim.list_pop_in_animation_controller));
        }
        
        setAdapter(adapterBottomMenu);
        
        post(() -> {
            scrollToPosition(bottomMenuItems.size() - 1);
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) getLayoutParams();
            
            layoutParams.topMargin = getResources().getDimensionPixelOffset(R.dimen.padding_10);
            layoutParams.bottomMargin = getResources().getDimensionPixelOffset(R.dimen.padding_10);
            layoutParams.leftMargin = getResources().getDimensionPixelOffset(R.dimen.padding_10);
            layoutParams.rightMargin = getResources().getDimensionPixelOffset(R.dimen.padding_10);
            
            containerHeight = getHeight() + layoutParams.topMargin + layoutParams.bottomMargin;
            MainPreferences.INSTANCE.setFloatingMenuHeight(getHeight() - layoutParams.topMargin - layoutParams.bottomMargin);
            setLayoutParams(layoutParams);
            
            if (LayoutsPreferences.INSTANCE.isCenterFloatingMenu()) {
                try {
                    FrameLayout.LayoutParams layoutParams_ = ((FrameLayout.LayoutParams) getLayoutParams());
                    layoutParams_.gravity = Gravity.CENTER | Gravity.BOTTOM;
                    setLayoutParams(layoutParams_);
                } catch (ClassCastException e) {
                    try {
                        LinearLayout.LayoutParams layoutParams_ = ((LinearLayout.LayoutParams) getLayoutParams());
                        layoutParams_.gravity = Gravity.CENTER | Gravity.BOTTOM;
                        setLayoutParams(layoutParams_);
                    } catch (ClassCastException ex) {
                        LayoutsPreferences.INSTANCE.setCenterFloatingMenu(false);
                    }
                }
            }
        });
    }
    
    @Override
    public void setAdapter(@Nullable RecyclerView.Adapter adapter) {
        super.setAdapter(adapter);
        scheduleLayoutAnimation();
    }
    
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = Math.min(widthSpec, displayWidth);
        super.onMeasure(width, heightSpec);
    }
    
    public AdapterFloatingMenu getMenuAdapter() {
        return (AdapterFloatingMenu) super.getAdapter();
    }
    
    public void initBottomMenuWithRecyclerView(ArrayList <Pair <Integer,
            Integer>> bottomMenuItems, RecyclerView recyclerView, BottomMenuCallbacks bottomMenuCallbacks) {
        if (isInitialized) {
            return;
        }
        
        initBottomMenu(bottomMenuItems, bottomMenuCallbacks);
        
        /*
         * Rather than clearing all scroll listeners at once, which will break other
         * features of the app such as Fast Scroller, we will use a boolean to check
         * if the scroll listener has been added or not and then add it. This should
         * be valid till the lifecycle of the BottomMenuRecyclerView.
         */
        // recyclerView.clearOnScrollListeners();
        
        if (recyclerView != null) {
            if (recyclerView.getAdapter() != null) {
                if (!isScrollListenerAdded) {
                    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                            super.onScrolled(recyclerView, dx, dy);
                            // setTranslationY(dy);
                            setContainerVisibility(dy, true);
                        }
                        
                        @Override
                        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                            super.onScrollStateChanged(recyclerView, newState);
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                Log.d(TAG, "onScrollStateChanged: SCROLL_STATE_IDLE");
                                if (getTranslationY() >= 0) {
                                    if (recyclerView.getAdapter().getItemCount() > MIN_ITEMS_THRESHOLD) {
                                        if (recyclerView.canScrollVertically(1 /* down */)) {
                                            animate()
                                                    .translationY(0)
                                                    .setDuration(250)
                                                    .setInterpolator(new DecelerateInterpolator())
                                                    .start();
                                        }
                                    } else {
                                        animate()
                                                .translationY(0)
                                                .setDuration(250)
                                                .setInterpolator(new DecelerateInterpolator())
                                                .start();
                                    }
                                }
                            } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                                Log.d(TAG, "onScrollStateChanged: SCROLL_STATE_DRAGGING");
                                if (getTranslationY() == 0) {
                                    animate()
                                            .translationY(containerHeight)
                                            .setDuration(250)
                                            .setInterpolator(new AccelerateInterpolator())
                                            .start();
                                }
                            }
                        }
                    });
                    
                    isScrollListenerAdded = true;
                }
            }
            
            isInitialized = true;
        }
    }
    
    public void initBottomMenuWithScrollView(ArrayList <Pair <Integer,
            Integer>> bottomMenuItems, NestedScrollView scrollView, BottomMenuCallbacks bottomMenuCallbacks) {
        if (isInitialized) {
            return;
        }
        
        initBottomMenu(bottomMenuItems, bottomMenuCallbacks);
        
        /*
         * Rather than clearing all scroll listeners at once, which will break other
         * features of the app such as Fast Scroller, we will use a boolean to check
         * if the scroll listener has been added or not and then add it. This should
         * be valid till the lifecycle of the BottomMenuRecyclerView.
         */
        // scrollView.clearOnScrollListeners();
        
        if (scrollView != null) {
            if (!isScrollListenerAdded) {
                scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY > oldScrollY && isBottomMenuVisible) {
                        animate()
                                .translationY(containerHeight)
                                .setDuration(250)
                                .setInterpolator(new AccelerateInterpolator())
                                .start();
                        isBottomMenuVisible = false;
                    } else if (scrollY < oldScrollY && !isBottomMenuVisible) {
                        animate()
                                .translationY(0)
                                .setDuration(250)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        isBottomMenuVisible = true;
                    }
                });
                
                isScrollListenerAdded = true;
            }
            
            isInitialized = true;
        }
    }
    
    public void updateBottomMenu(ArrayList <Pair <Integer, Integer>> bottomMenuItems) {
        getMenuAdapter().updateMenu(bottomMenuItems);
        // requestLayout();
    }
    
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            scheduleLayoutAnimation();
        }
    }
    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(broadcastReceiver);
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        if (AppearancePreferences.INSTANCE.isAccentColorOnBottomMenu()) {
            setBackgroundTintList(ViewUtils.INSTANCE.toColorStateList(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor()));
        } else {
            super.onThemeChanged(theme, animate);
        }
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        super.onAccentChanged(accent);
        if (AppearancePreferences.INSTANCE.isAccentColorOnBottomMenu()) {
            setBackgroundTintList(ViewUtils.INSTANCE.toColorStateList(accent.getPrimaryAccentColor()));
        }
    }
    
    public void setContainerVisibility(int dy, boolean animate) {
        if (dy > 0 && isBottomMenuVisible) {
            if (animate) {
                animate()
                        .translationY(containerHeight)
                        .setDuration(250)
                        .setInterpolator(new AccelerateInterpolator())
                        .start();
            } else {
                setTranslationY(containerHeight);
            }
            
            isBottomMenuVisible = false;
        } else if (dy < 0 && !isBottomMenuVisible) {
            if (animate) {
                animate()
                        .translationY(0)
                        .setDuration(250)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                setTranslationY(0);
            }
            
            isBottomMenuVisible = true;
        }
    }
    
    public void setTranslationY(int dy) {
        if (dy > 0) {
            if (getTranslationY() < containerHeight) {
                setTranslationY(getTranslationY() + dy);
                setTranslationY(RangesKt.coerceAtMost(getTranslationY(), containerHeight));
            }
        } else {
            if (getTranslationY() > 0) {
                setTranslationY(getTranslationY() + dy);
                setTranslationY(RangesKt.coerceAtLeast(getTranslationY(), 0));
            }
        }
    }
    
    public void setInitialized(boolean initialized) {
        isInitialized = initialized;
    }
    
    public void clear() {
        setAdapter(null);
    }
}
