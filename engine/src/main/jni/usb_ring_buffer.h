#pragma once

#include <atomic>
#include <cstdint>
#include <cstring>

/**
 * A single-producer / single-consumer lock-free ring buffer for interleaved
 * float PCM samples.
 *
 * The DSP output thread is the sole writer; the USB streaming callback is the
 * sole reader. Because only one thread writes and one thread reads at a time,
 * the two atomic indices never contend with each other — no mutex needed.
 *
 * Capacity is a power of two so the modulo operation becomes a cheap bitmask,
 * which matters when this is called thousands of times per second from a
 * real-time callback.
 *
 * @author Hamza417
 */
class UsbRingBuffer {
public:
    /**
     * ~170 ms of float samples at 96 kHz stereo. Large enough to absorb DSP
     * scheduling jitter without adding perceptible latency.
     */
    static constexpr uint32_t CAPACITY = 32768u;
    static constexpr uint32_t MASK = CAPACITY - 1u;

    UsbRingBuffer() : writeIdx_(0), readIdx_(0) {
        memset(data_, 0, sizeof(data_));
    }

    /**
     * Writes up to [count] floats from [src] into the buffer.
     * Drops samples that would overflow rather than blocking, so the DSP thread
     * never stalls waiting for the USB thread to catch up.
     *
     * @return Number of samples actually written (may be less than [count] when full).
     */
    uint32_t write(const float *src, uint32_t count) {
        const uint32_t w = writeIdx_.load(std::memory_order_relaxed);
        const uint32_t r = readIdx_.load(std::memory_order_acquire);

        // Compute used space correctly even when the write index has wrapped
        // around 2^32 but the read index has not caught up yet.
        const uint32_t used = (w >= r) ? (w - r) : (CAPACITY - r + w);
        const uint32_t free = CAPACITY - used;
        const uint32_t n = (count < free) ? count : free;

        for (uint32_t i = 0; i < n; i++) {
            data_[(w + i) & MASK] = src[i];
        }
        writeIdx_.store(w + n, std::memory_order_release);
        return n;
    }

    /**
     * Reads exactly [count] floats into [dst]. Any samples that cannot be filled
     * from real data are padded with zeros (digital silence), so the USB stream
     * never collapses during a momentary DSP underrun.
     *
     * @return Number of samples read from live ring-buffer data (zeros not counted).
     */
    uint32_t read(float *dst, uint32_t count) {
        const uint32_t r = readIdx_.load(std::memory_order_relaxed);
        const uint32_t w = writeIdx_.load(std::memory_order_acquire);

        // Compute available samples correctly even when the write index has
        // wrapped around 2^32 but the read index is still behind.
        const uint32_t avail = (w >= r) ? (w - r) : (CAPACITY - r + w);
        const uint32_t n = (count < avail) ? count : avail;

        for (uint32_t i = 0; i < n; i++) {
            dst[i] = data_[(r + i) & MASK];
        }

        // Pad the remainder with silence so the caller always gets [count] samples.
        if (n < count) {
            memset(dst + n, 0, (count - n) * sizeof(float));
        }

        readIdx_.store(r + n, std::memory_order_release);
        return n;
    }

    /** Samples currently waiting to be consumed. */
    uint32_t available() const {
        const uint32_t w = writeIdx_.load(std::memory_order_acquire);
        const uint32_t r = readIdx_.load(std::memory_order_acquire);
        return (w >= r) ? (w - r) : (CAPACITY - r + w);
    }

    /** Free slots that can still accept new samples. */
    uint32_t freeSpace() const {
        return CAPACITY - available();
    }

    /** Discards all pending samples. Called during stream teardown. */
    void flush() {
        readIdx_.store(writeIdx_.load(std::memory_order_acquire),
                       std::memory_order_release);
    }

private:
    // Each index on its own cache line to eliminate false-sharing between the
    // producer (DSP thread) and consumer (USB callback thread).
    alignas(64) std::atomic<uint32_t> writeIdx_;
    alignas(64) std::atomic<uint32_t> readIdx_;
    alignas(64) float data_[CAPACITY];
};
