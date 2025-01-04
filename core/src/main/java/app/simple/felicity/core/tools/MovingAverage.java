package app.simple.felicity.core.tools;

import java.util.LinkedList;
import java.util.Queue;

public class MovingAverage {
    private final Queue <Long> queue;
    private final long size;
    private double sum;
    
    public MovingAverage(int size) {
        this.queue = new LinkedList <>();
        this.size = size;
        this.sum = 0.0;
    }
    
    public double next(long val) {
        if (queue.size() == size) {
            sum -= queue.remove();
        }
        queue.add(val);
        sum += val;
        return sum / queue.size();
    }
}
