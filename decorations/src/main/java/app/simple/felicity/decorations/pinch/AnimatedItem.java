package app.simple.felicity.decorations.pinch;

import android.graphics.Rect;

import androidx.recyclerview.widget.RecyclerView.ItemAnimator.ItemHolderInfo;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

public class AnimatedItem {
    private ViewHolder viewHolder;
    private Rect preRect;
    private Rect postRect;
    private Type type;
    
    public AnimatedItem(ViewHolder viewHolder, Rect preRect, Rect postRect, Type type) {
        this.viewHolder = viewHolder;
        this.preRect = preRect;
        this.postRect = postRect;
        this.type = type;
    }
    
    public ViewHolder getViewHolder() {
        return viewHolder;
    }
    
    public Rect getPreRect() {
        return preRect;
    }
    
    public Rect getPostRect() {
        return postRect;
    }
    
    public Type getType() {
        return type;
    }
    
    public enum Type {
        DISAPPEARANCE, APPEARANCE, PERSISTENCE, CHANGE
    }
    
    public static class Builder {
        private ViewHolder viewHolder;
        
        private Rect preRect;
        private Rect postRect;
        
        private Type type;
        
        public Builder setViewHolder(ViewHolder viewHolder) {
            this.viewHolder = viewHolder;
            return this;
        }
        
        public Builder setPreRect(ItemHolderInfo preLayoutInfo) {
            if (preLayoutInfo == null) {
                preRect = new Rect();
            } else {
                this.preRect = new Rect(preLayoutInfo.left, preLayoutInfo.top,
                        preLayoutInfo.right, preLayoutInfo.bottom);
            }
            return this;
        }
        
        public Builder setPostRect(ItemHolderInfo postLayoutInfo) {
            if (postLayoutInfo == null) {
                postRect = new Rect();
            } else {
                this.postRect = new Rect(postLayoutInfo.left, postLayoutInfo.top,
                        postLayoutInfo.right, postLayoutInfo.bottom);
            }
            return this;
        }
        
        public Builder setType(Type type) {
            this.type = type;
            return this;
        }
        
        public AnimatedItem build() {
            return new AnimatedItem(viewHolder, preRect, postRect, type);
        }
    }
}
