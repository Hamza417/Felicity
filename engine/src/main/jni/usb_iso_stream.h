#pragma once

#include "usb_ring_buffer.h"
#include "uac_descriptors.h"

#include <libusb.h>
#include <pthread.h>
#include <atomic>
#include <cstdint>

/**
 * Manages the full isochronous USB audio output pipeline.
 *
 * Responsibilities:
 *   - Holds a pool of pre-allocated [libusb_transfer] URBs.
 *   - Runs a dedicated high-priority pthread that calls libusb_handle_events()
 *     in a tight loop, which in turn fires the URB completion callbacks.
 *   - In each callback, drains fresh PCM from [ringBuffer], converts it from
 *     float to the DAC's native PCM format, refills the URB, and resubmits it.
 *   - On underrun (ring buffer momentarily empty) silently pads with zeros so
 *     the USB stream never collapses.
 *
 * Thread model:
 *   - DSP thread   → calls [pushPcm]  (ring buffer writer, only producer)
 *   - USB thread   → fires callbacks  (ring buffer reader, only consumer)
 *   - Kotlin thread → calls [start] / [stop]
 *
 * @author Hamza417
 */
class UsbIsoStream {
public:
    /** URBs kept in-flight simultaneously. More = smoother but higher latency. */
    static constexpr int NUM_TRANSFERS = 8;

    /** Isochronous packets bundled inside each URB. */
    static constexpr int PACKETS_PER_URB = 8;

    UsbIsoStream();

    ~UsbIsoStream();

    /**
     * Allocates URBs sized for the given alt-setting, submits them, and spawns
     * the USB event thread. Must be called after format negotiation has completed.
     *
     * @param ctx            The libusb context (owned by the driver, not this class).
     * @param handle         The open libusb device handle.
     * @param alt            The chosen alt-setting (provides endpoint addr + maxPktSize).
     * @param subslotSize    Bytes per sample slot as negotiated (e.g. 3 for 24-bit).
     * @param bitResolution  Significant bits per sample (e.g. 24).
     * @param sampleRate     The negotiated sample rate in Hz (e.g. 44100, 96000).
     *                       Used to compute the exact frames-per-microframe so packets
     *                       are sized to match the audio clock rather than the maximum
     *                       endpoint bandwidth.
     * @return true if all URBs were submitted and the event thread started.
     */
    bool start(libusb_context *ctx,
               libusb_device_handle *handle,
               const UacAltSetting &alt,
               int subslotSize,
               int bitResolution,
               int sampleRate);

    /**
     * Cancels all pending transfers and joins the event thread.
     * Blocks until teardown is complete — safe to call from any thread.
     */
    void stop();

    /**
     * Delivers interleaved float PCM samples from the DSP thread into the ring
     * buffer. Thread-safe (SPSC — only one producer, only one consumer).
     *
     * @param samples Pointer to interleaved float samples in [-1.0, 1.0].
     * @param count   Total number of floats (frames × channels).
     * @return Number of samples actually accepted (may be less if buffer is full).
     */
    int pushPcm(const float *samples, int count);

    bool isRunning() const { return running_.load(std::memory_order_acquire); }

    /**
     * Called from the libusb transfer callback — not for external use.
     * Refills and resubmits the transfer, or decrements the pending count if
     * we are shutting down.
     */
    void onTransferComplete(libusb_transfer *transfer);

private:
    bool allocateTransfers(libusb_device_handle *handle,
                           uint8_t endpointAddr,
                           uint16_t maxPacketSize);

    void freeTransfers();

    void fillTransferBuffer(libusb_transfer *transfer);

    /**
     * Returns the number of audio frames that should be packed into the current
     * 125 µs USB microframe to match the negotiated sample rate.
     *
     * For integer ratios (e.g. 48000 Hz / 8000 = 6) the return value is constant.
     * For fractional ratios (e.g. 44100 Hz / 8000 = 5.5125) a Bresenham-style
     * accumulator toggles between floor and ceil so the long-term average is exact.
     */
    int framesForCurrentUframe();

    void convertAndPack(const float *src, uint8_t *dst, int frames) const;

    static void *eventThreadEntry(void *arg);

    void eventThreadLoop();

    libusb_context *ctx_ = nullptr;
    libusb_transfer *transfers_[NUM_TRANSFERS]{};
    UsbRingBuffer ringBuffer_;

    std::atomic<bool> running_{false};
    std::atomic<bool> eventThreadExit_{false};
    std::atomic<int> pendingCount_{0};

    pthread_t eventThread_{};
    bool threadStarted_ = false;

    int subslotSize_ = 2;
    int bitResolution_ = 16;
    uint16_t maxPacketSize_ = 0;
    int channels_ = 2;

    /**
     * The sample rate negotiated with the DAC, used to calculate the exact number
     * of audio frames that should be packed into each 125 µs USB microframe.
     * For non-integer rates (e.g. 44100 Hz → 5.5125 frames/uframe), the fractional
     * remainder is tracked by [uframeFraction_] so the correct integer frame count
     * averages out to the target rate over many microframes.
     */
    int sampleRate_ = 0;

    /**
     * Accumulated fractional part of the frames-per-microframe calculation.
     * Each microframe we add `sampleRate_ % 8000`; when the accumulator reaches
     * or exceeds 8000 we emit one extra frame and subtract 8000.
     *
     * This implements Bresenham-style sample rate conversion — it guarantees the
     * long-term average frame count per microframe matches the DAC clock exactly,
     * preventing the ring buffer from draining faster than the DAC plays.
     */
    uint32_t uframeFraction_ = 0;
};
