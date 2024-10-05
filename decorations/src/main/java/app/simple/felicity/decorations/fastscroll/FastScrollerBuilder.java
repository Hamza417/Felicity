/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.simple.felicity.decorations.fastscroll;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.util.Consumer;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import app.simple.felicity.decoration.R;
import app.simple.felicity.theme.managers.ThemeManager;

public class FastScrollerBuilder {
    
    @NonNull
    private final ViewGroup view;
    
    @Nullable
    private FastScroller.ViewHelper viewHelper;
    
    @Nullable
    private PopupTextProvider popupTextProvider;
    
    @Nullable
    private Rect padding;
    
    private Drawable trackDrawable;
    private Drawable thumbDrawable;
    private Consumer <TextView> popupStyle;
    
    @Nullable
    private FastScroller.AnimationHelper animationHelper;
    
    public FastScrollerBuilder(@NonNull ViewGroup view) {
        this.view = view;
        setupAesthetics();
    }
    
    @NonNull
    public FastScrollerBuilder setViewHelper(@Nullable FastScroller.ViewHelper viewHelper) {
        this.viewHelper = viewHelper;
        return this;
    }
    
    @NonNull
    public FastScrollerBuilder setPopupTextProvider(@Nullable PopupTextProvider popupTextProvider) {
        this.popupTextProvider = popupTextProvider;
        return this;
    }
    
    @NonNull
    public FastScrollerBuilder setPadding(int left, int top, int right, int bottom) {
        if (padding == null) {
            padding = new Rect();
        }
        padding.set(left, top, right, bottom);
        return this;
    }
    
    @NonNull
    public FastScrollerBuilder setPadding(@Nullable Rect padding) {
        if (padding != null) {
            if (this.padding == null) {
                this.padding = new Rect();
            }
            this.padding.set(padding);
        } else {
            this.padding = null;
        }
        return this;
    }
    
    @NonNull
    public FastScrollerBuilder setTrackDrawable(@NonNull Drawable trackDrawable) {
        this.trackDrawable = trackDrawable;
        return this;
    }
    
    @NonNull
    public FastScrollerBuilder setThumbDrawable(@NonNull Drawable thumbDrawable) {
        this.thumbDrawable = thumbDrawable;
        return this;
    }
    
    @NonNull
    public FastScrollerBuilder setPopupStyle(@NonNull Consumer <TextView> popupStyle) {
        this.popupStyle = popupStyle;
        return this;
    }
    
    @NonNull
    public FastScrollerBuilder setupAesthetics() {
        Context context = view.getContext();
        trackDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.fast_scroller_track, context.getTheme());
        thumbDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.fast_scroller_thumb, context.getTheme());
        assert thumbDrawable != null;
        thumbDrawable.setTintList(ColorStateList.valueOf(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor()));
        popupStyle = PopupStyles.Inure;
        return this;
    }
    
    public void setAnimationHelper(@Nullable FastScroller.AnimationHelper animationHelper) {
        this.animationHelper = animationHelper;
    }
    
    public void disableScrollbarAutoHide() {
        DefaultAnimationHelper animationHelper = new DefaultAnimationHelper(view);
        animationHelper.setScrollbarAutoHideEnabled(false);
        this.animationHelper = animationHelper;
    }
    
    @NonNull
    public FastScroller build() {
        return new FastScroller(view, getOrCreateViewHelper(), padding, trackDrawable,
                thumbDrawable, popupStyle, getOrCreateAnimationHelper());
    }
    
    @NonNull
    private FastScroller.ViewHelper getOrCreateViewHelper() {
        if (viewHelper != null) {
            return viewHelper;
        }
        if (view instanceof ViewHelperProvider) {
            return ((ViewHelperProvider) view).getViewHelper();
        } else if (view instanceof RecyclerView) {
            return new RecyclerViewHelper((RecyclerView) view, popupTextProvider);
        } else if (view instanceof NestedScrollView) {
            throw new UnsupportedOperationException("Please use "
                    + FastScrollNestedScrollView.class.getSimpleName() + " instead of "
                    + NestedScrollView.class.getSimpleName() + "for fast scroll");
        } else if (view instanceof ScrollView) {
            throw new UnsupportedOperationException("Please use "
                    + FastScrollScrollView.class.getSimpleName() + " instead of "
                    + ScrollView.class.getSimpleName() + "for fast scroll");
        } else if (view instanceof WebView) {
            throw new UnsupportedOperationException("Please use "
                    + FastScrollWebView.class.getSimpleName() + " instead of "
                    + WebView.class.getSimpleName() + "for fast scroll");
        } else {
            throw new UnsupportedOperationException(view.getClass().getSimpleName()
                    + " is not supported for fast scroll");
        }
    }
    
    public void updateAesthetics() {
        thumbDrawable.setTintList(ColorStateList.valueOf(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor()));
    }
    
    @NonNull
    private FastScroller.AnimationHelper getOrCreateAnimationHelper() {
        if (animationHelper != null) {
            return animationHelper;
        }
        return new DefaultAnimationHelper(view);
    }
}
