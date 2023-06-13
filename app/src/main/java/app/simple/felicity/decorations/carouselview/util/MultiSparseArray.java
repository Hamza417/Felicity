package app.simple.felicity.decorations.carouselview.util;

import android.util.SparseArray;

import java.util.ArrayList;

/**
 * Multi-dimension FIFO SparseArray.
 *
 * @author sunny-chung
 * @see SparseArray
 */
public class MultiSparseArray <E> {
    
    SparseArray <ArrayList <E>> mArray;
    int mSize = 0;
    
    public MultiSparseArray() {
        mArray = new SparseArray <>();
    }
    
    public MultiSparseArray(int initialCapacity) {
        mArray = new SparseArray <>(initialCapacity);
    }
    
    public void put(int key, E value) {
        ArrayList <E> values = mArray.get(key);
        if (values == null) {
            values = new ArrayList <>();
        }
        values.add(value);
        ++mSize;
        mArray.put(key, values);
    }
    
    public E get(int key) {
        ArrayList <E> values = mArray.get(key);
        if (values != null && values.size() > 0) {
            return values.get(0);
        }
        return null;
    }
    
    public E pop(int key) {
        ArrayList <E> values = mArray.get(key);
        if (values != null && values.size() > 0) {
            E value = values.get(0);
            values.remove(0);
            --mSize;
            return value;
        }
        return null;
    }
    
    public void remove(int key) {
        ArrayList <E> values = mArray.get(key);
        if (values != null && values.size() > 0) {
            values.remove(0);
            --mSize;
            // let the empty array to stay in memory
        }
    }
    
    public int size() {
        //        return mSize;
        return mArray.size();
    }
    
    public int keyAt(int index) {
        return mArray.keyAt(index);
    }
    
    public ArrayList <E> valuesAt(int index) {
        return mArray.valueAt(index);
    }
}
