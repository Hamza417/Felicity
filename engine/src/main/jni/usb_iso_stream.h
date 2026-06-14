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
     * @param sampleRate     The negotiated sample rate in Hz (e.g. 44100, 48000, 96000).
     *                       Used to compute the correct number of frames per 1 ms USB packet
     *                       instead of blindly using wMaxPacketSize, which overflows the DAC.
     * @param srcChannels    Channel count of the audio coming from the DSP thread.
     *                       May differ from [alt.bNrChannels] when a mono track plays through
     *                       a stereo-only DAC — the stream upmixes internally in that case.
     * @return true if all URBs were submitted and the event thread started.
     */
    bool start(libusb_context *ctx,
               libusb_device_handle *handle,
               const UacAltSetting &alt,
               int subslotSize,
               int bitResolution,
               uint32_t sampleRate,
               int srcChannels);

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

    /**
     * Discards all pending samples from the ring buffer without stopping the
     * isochronous stream. Use this on seeks or track changes to get rid of
     * stale audio without the expensive stop/restart cycle. In-flight URBs
     * will briefly output zeros until fresh PCM arrives via [pushPcm].
     */
    void flushRingBuffer();

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

    /** Channel count the DAC hardware expects (from the negotiated alt-setting). */
    int channels_ = 2;

    /**
     * Channel count pushed into the ring buffer by the DSP thread. Typically the
     * same as [channels_], but can be 1 (mono) when the source track is mono and
     * the DAC has no mono alt-setting. [fillTransferBuffer] upmixes 1→2 when needed.
     */
    int srcChannels_ = 2;

    /** Sample rate the DAC is clocked at, in Hz. Used to compute per-packet frame counts. */
    uint32_t sampleRate_ = 48000;

    /**
     * Running fractional accumulator for the per-packet frame count calculation.
     * Keeps track of the sub-millisecond remainder so that non-integer rates like
     * 44.1 kHz alternate between 44 and 45 frames per packet and deliver EXACTLY
     * sampleRate_ frames per second to the DAC — no FIFO overflow, no underrun drift.
     */
    uint32_t sampleAccum_ = 0;

    /**
     * Counts how many consecutive underruns have happened since the last time
     * real data was available. Used to throttle the underrun log so we only
     * print a message for the first underrun in each dry spell instead of once
     * per packet (which would be thousands of lines on startup).
     */
    std::atomic<uint32_t> underrunCount_{0};
};

