#include "usb_iso_stream.h"
#include "felicity_usb_dac.h"

#include <cstring>
#include <cstdlib>
#include <climits>
#include <cerrno>
#include <sched.h> // SCHED_FIFO, sched_param
#include <time.h>  // nanosleep

// ------------------------------------------------------------------ //
//  libusb transfer callback — called by libusb on the event thread
// ------------------------------------------------------------------ //

static void LIBUSB_CALL iso_transfer_callback(struct libusb_transfer *transfer) {
    UsbIsoStream *stream = static_cast<UsbIsoStream *>(transfer->user_data);
    stream->onTransferComplete(transfer);
}

// ------------------------------------------------------------------ //
//  Construction / destruction
// ------------------------------------------------------------------ //

UsbIsoStream::UsbIsoStream() {
    memset(transfers_, 0, sizeof(transfers_));
}

UsbIsoStream::~UsbIsoStream() {
    stop();
}

// ------------------------------------------------------------------ //
//  PCM float → packed integer conversion
// ------------------------------------------------------------------ //

/**
 * Converts [frames] interleaved float samples from [src] into the DAC's native
 * packed integer format and writes them to [dst].
 *
 * All formats are written as little-endian (the USB Audio spec mandates this).
 * The float values are expected to be in the [-1.0, 1.0] range; anything outside
 * is clamped to avoid wrapping artifacts.
 */
void UsbIsoStream::convertAndPack(const float *src, uint8_t *dst, int frames) const {
    const int samples = frames * channels_;

    for (int i = 0; i < samples; i++) {
        const float f = (src[i] < -1.f) ? -1.f : (src[i] > 1.f) ? 1.f
                                                                : src[i];

        if (subslotSize_ == 2) {
            // 16-bit PCM: scale to [-32768, 32767]
            const int16_t s16 = static_cast<int16_t>(f * 32767.f);
            dst[0] = static_cast<uint8_t>(s16 & 0xFF);
            dst[1] = static_cast<uint8_t>((s16 >> 8) & 0xFF);
            dst += 2;
        } else if (subslotSize_ == 3) {
            // 24-bit PCM packed into 3 bytes (the most common high-res DAC format)
            const int32_t s24 = static_cast<int32_t>(f * 8388607.f); // 2^23 - 1
            dst[0] = static_cast<uint8_t>(s24 & 0xFF);
            dst[1] = static_cast<uint8_t>((s24 >> 8) & 0xFF);
            dst[2] = static_cast<uint8_t>((s24 >> 16) & 0xFF);
            dst += 3;
        } else {
            // 32-bit PCM (subslotSize == 4), covers both 24-bit padded and true 32-bit
            const int32_t s32 = static_cast<int32_t>(f * 2147483647.f); // 2^31 - 1
            dst[0] = static_cast<uint8_t>(s32 & 0xFF);
            dst[1] = static_cast<uint8_t>((s32 >> 8) & 0xFF);
            dst[2] = static_cast<uint8_t>((s32 >> 16) & 0xFF);
            dst[3] = static_cast<uint8_t>((s32 >> 24) & 0xFF);
            dst += 4;
        }
    }
}

// ------------------------------------------------------------------ //
//  Transfer buffer fill
// ------------------------------------------------------------------ //

/**
 * Computes how many audio frames the current microframe should carry so the
 * long-term data rate matches the negotiated sample rate exactly.
 *
 * USB high-speed runs at 8000 microframes per second.  For sample rates that are
 * integer multiples of 8000 (e.g. 48000, 96000) every microframe gets the same
 * number of frames.  For rates like 44100 Hz the ratio is fractional (5.5125);
 * we use a Bresenham-style accumulator so that the average over many microframes
 * is exactly 44100/8000, preventing the ring buffer from draining faster than the
 * DAC actually consumes audio.
 *
 * @return Number of frames to pack into this microframe's packet.
 */
int UsbIsoStream::framesForCurrentUframe() {
    const int baseFrames = sampleRate_ / 8000;
    const uint32_t remainder = static_cast<uint32_t>(sampleRate_ % 8000);

    if (remainder == 0) {
        // Exact integer ratio (e.g. 48000/8000 = 6) — no fractional tracking needed.
        return baseFrames;
    }

    uframeFraction_ += remainder;
    if (uframeFraction_ >= 8000u) {
        uframeFraction_ -= 8000u;
        return baseFrames + 1; // emit an extra frame this microframe
    }
    return baseFrames;
}

/**
 * Fills [transfer]'s buffer by reading from the ring buffer and converting each
 * packet worth of frames to packed PCM.  The number of frames packed into each
 * microframe is determined by [framesForCurrentUframe] so it matches the DAC's
 * audio clock rather than the endpoint's maximum bandwidth — this is what
 * prevents the chronic underruns that would otherwise happen when maxPacketSize
 * is dozens of times larger than the sample-rate-dictated packet size.
 *
 * Any packet that cannot be satisfied from live ring-buffer data is zero-filled
 * (silence) — this is the underrun guard.
 */
void UsbIsoStream::fillTransferBuffer(libusb_transfer *transfer) {
    const int bytesPerFrame = subslotSize_ * channels_;
    uint8_t *bufferPtr = transfer->buffer;

    for (int p = 0; p < transfer->num_iso_packets; p++) {
        libusb_iso_packet_descriptor &pkt = transfer->iso_packet_desc[p];

        // Determine how many audio frames belong in this microframe based on the
        // negotiated sample rate — NOT the endpoint's wMaxPacketSize which is
        // typically 10-50× larger than what the audio clock actually needs.
        const int framesInPkt = framesForCurrentUframe();
        const int packetBytes = framesInPkt * bytesPerFrame;

        // Guard against exceeding the endpoint's hardware limit.
        if (packetBytes > static_cast<int>(maxPacketSize_)) {
            LOGW("Calculated packet size %d exceeds maxPacketSize %d — clamping",
                 packetBytes, maxPacketSize_);
            pkt.length = maxPacketSize_;
        } else {
            pkt.length = static_cast<unsigned int>(packetBytes);
        }

        if (framesInPkt > 0 && packetBytes > 0) {
            // Temporary scratch buffer for the float data.  Stack allocation is
            // fine because with correct sample-rate-based sizing each packet
            // carries at most ~12 frames (for 96000 Hz stereo) — well under 512.
            float scratch[512]{};
            const int samplesNeeded = framesInPkt * channels_;

            const uint32_t got = ringBuffer_.read(scratch, static_cast<uint32_t>(samplesNeeded));
            if (got < static_cast<uint32_t>(samplesNeeded)) {
                LOGD("USB underrun: needed %d samples, got %u — padding with silence",
                     samplesNeeded, got);
            }

            // Convert float → packed PCM (zeros produce silence on underrun because
            // UsbRingBuffer::read already zero-filled the scratch buffer).
            convertAndPack(scratch, bufferPtr, framesInPkt);
        }

        bufferPtr += pkt.length;
    }
}

// ------------------------------------------------------------------ //
//  Transfer lifecycle
// ------------------------------------------------------------------ //

bool UsbIsoStream::allocateTransfers(libusb_device_handle *handle,
                                     uint8_t endpointAddr,
                                     uint16_t maxPacketSize) {
    const int bufferSize = PACKETS_PER_URB * maxPacketSize;

    for (int i = 0; i < NUM_TRANSFERS; i++) {
        libusb_transfer *t = libusb_alloc_transfer(PACKETS_PER_URB);
        if (!t) {
            LOGE("libusb_alloc_transfer failed for URB %d", i);
            return false;
        }

        // Each URB gets its own heap-allocated PCM buffer.
        auto *buf = static_cast<uint8_t *>(calloc(1, bufferSize));
        if (!buf) {
            libusb_free_transfer(t);
            LOGE("Failed to allocate PCM buffer for URB %d", i);
            return false;
        }

        libusb_fill_iso_transfer(
                t,
                handle,
                endpointAddr,
                buf,
                bufferSize,
                PACKETS_PER_URB,
                iso_transfer_callback,
                this, // user_data → routed to onTransferComplete
                0     // no timeout — isochronous transfers don't use one
        );

        libusb_set_iso_packet_lengths(t, maxPacketSize);
        transfers_[i] = t;
    }
    return true;
}

void UsbIsoStream::freeTransfers() {
    for (int i = 0; i < NUM_TRANSFERS; i++) {
        if (transfers_[i]) {
            free(transfers_[i]->buffer);
            libusb_free_transfer(transfers_[i]);
            transfers_[i] = nullptr;
        }
    }
}

// ------------------------------------------------------------------ //
//  Callback — called by libusb on the event thread
// ------------------------------------------------------------------ //

void UsbIsoStream::onTransferComplete(libusb_transfer *transfer) {
    // Cancellation means we are shutting down. Just count it down.
    if (transfer->status == LIBUSB_TRANSFER_CANCELLED) {
        LOGD("URB cancelled (shutdown in progress), pending=%d",
             pendingCount_.load(std::memory_order_relaxed) - 1);
        pendingCount_.fetch_sub(1, std::memory_order_release);
        return;
    }

    // If the stream is no longer running, cancel this transfer instead of
    // resubmitting it so the event thread can eventually drain to zero pending.
    if (!running_.load(std::memory_order_acquire)) {
        libusb_cancel_transfer(transfer);
        return;
    }

    // Log non-success statuses but keep going — a one-off error is far less
    // harmful than stopping the entire USB stream over it.
    if (transfer->status != LIBUSB_TRANSFER_COMPLETED) {
        LOGW("ISO transfer status %d — continuing", transfer->status);
    }

    // Refill the transfer buffer with fresh PCM (or silence on underrun).
    fillTransferBuffer(transfer);

    // Resubmit immediately so the pipeline is always full.
    const int ret = libusb_submit_transfer(transfer);
    if (ret != LIBUSB_SUCCESS) {
        LOGE("libusb_submit_transfer failed after callback: %s — stream may stall",
             libusb_strerror((libusb_error) ret));
        pendingCount_.fetch_sub(1, std::memory_order_release);
    }
}

// ------------------------------------------------------------------ //
//  Event thread
// ------------------------------------------------------------------ //

void *UsbIsoStream::eventThreadEntry(void *arg) {
    static_cast<UsbIsoStream *>(arg)->eventThreadLoop();
    return nullptr;
}

void UsbIsoStream::eventThreadLoop() {
    // Try to elevate this thread to SCHED_FIFO so USB callbacks preempt regular
    // threads. This dramatically reduces isochronous timing jitter on busy systems.
    // On Android, SCHED_FIFO requires CAP_SYS_NICE or the audio group — we attempt
    // it and fall back to the default scheduler without failing hard.
    struct sched_param sp{};
    sp.sched_priority = sched_get_priority_max(SCHED_FIFO);
    if (pthread_setschedparam(pthread_self(), SCHED_FIFO, &sp) != 0) {
        LOGW("Could not set SCHED_FIFO for USB event thread (errno=%d) — using default scheduler",
             errno);
    } else {
        LOGI("USB event thread running at SCHED_FIFO priority %d", sp.sched_priority);
    }

    LOGI("USB event thread started");

    // 10 ms poll interval — short enough for low-latency audio, long enough
    // that a tear-down signal is noticed promptly without burning CPU in a spin.
    struct timeval tv{0, 10000};

    while (!eventThreadExit_.load(std::memory_order_acquire)) {
        int ret = libusb_handle_events_timeout(ctx_, &tv);
        if (ret != LIBUSB_SUCCESS && ret != LIBUSB_ERROR_INTERRUPTED) {
            LOGE("libusb_handle_events error: %s", libusb_strerror((libusb_error) ret));
        }
    }

    LOGI("USB event thread exiting");
}

// ------------------------------------------------------------------ //
//  Public API
// ------------------------------------------------------------------ //

bool UsbIsoStream::start(libusb_context *ctx,
                         libusb_device_handle *handle,
                         const UacAltSetting &alt,
                         int subslotSize,
                         int bitResolution,
                         int sampleRate) {
    if (alt.endpointAddress == 0 || alt.wMaxPacketSize == 0) {
        LOGE("Cannot start ISO stream — alt-setting has no valid endpoint");
        return false;
    }
    if (sampleRate <= 0) {
        LOGE("Cannot start ISO stream — invalid sample rate %d", sampleRate);
        return false;
    }

    ctx_ = ctx;
    subslotSize_ = subslotSize;
    bitResolution_ = bitResolution;
    maxPacketSize_ = alt.wMaxPacketSize;
    channels_ = (alt.bNrChannels > 0) ? alt.bNrChannels : 2;
    sampleRate_ = sampleRate;
    uframeFraction_ = 0;

    // Compute frames-per-microframe so we can size each USB packet to match the
    // DAC's actual audio clock rather than the endpoint's maximum bandwidth.
    // USB high-speed = 8000 microframes per second.
    // For integer ratios (e.g. 48000/8000=6) every packet gets the same size.
    // For fractional ratios (e.g. 44100/8000=5.5125) we use a Bresenham
    // accumulator so the long-term average is exact.
    const int baseFramesPerUframe = sampleRate_ / 8000;

    LOGI("Starting ISO stream: ep=0x%02X maxPkt=%d subslot=%d bits=%d ch=%d rate=%dHz "
         "baseFramesPerUframe=%d",
         alt.endpointAddress, maxPacketSize_, subslotSize_, bitResolution_, channels_,
         sampleRate_, baseFramesPerUframe);

    if (!allocateTransfers(handle, alt.endpointAddress, maxPacketSize_)) {
        freeTransfers();
        return false;
    }

    // Pre-fill the ring buffer with enough silence to cover the entire USB
    // pipeline depth before the DSP thread pushes its first audio chunk.
    // Without this cushion the first few dozen URB completions would hit an
    // empty ring buffer and pad every packet with zeros — producing an
    // audible gap at the start of playback and making the pipeline vulnerable
    // to underrun-then-burst cycles that sound like a skipping CD.
    //
    // We pre-fill with roughly half the ring buffer's capacity so the USB
    // consumer has immediate data to drain while the DSP producer is still
    // ramping up. At 96 kHz stereo this is ~170 ms of silence, well under
    // the perceptual threshold for an initial gap.
    ringBuffer_.flush();
    ringBuffer_.prefillSilence(UsbRingBuffer::CAPACITY / 2u);

    running_.store(true, std::memory_order_release);
    eventThreadExit_.store(false, std::memory_order_release);

    // Submit all URBs before starting the event thread to ensure the DAC has a
    // full pipeline immediately on open, avoiding an initial starvation burst.
    pendingCount_.store(0, std::memory_order_relaxed);
    for (int i = 0; i < NUM_TRANSFERS; i++) {
        fillTransferBuffer(transfers_[i]);
        int ret = libusb_submit_transfer(transfers_[i]);
        if (ret != LIBUSB_SUCCESS) {
            LOGE("Failed to submit initial URB %d: %s", i, libusb_strerror((libusb_error) ret));
            // Don't abort — the event thread will retry on the next cycle.
        } else {
            pendingCount_.fetch_add(1, std::memory_order_relaxed);
        }
    }

    // Spawn the dedicated USB event thread.
    if (pthread_create(&eventThread_, nullptr, eventThreadEntry, this) != 0) {
        LOGE("Failed to create USB event thread (errno=%d)", errno);
        running_.store(false, std::memory_order_release);
        freeTransfers();
        return false;
    }
    threadStarted_ = true;

    LOGI("ISO stream started — %d URBs in flight", pendingCount_.load());
    return true;
}

void UsbIsoStream::stop() {
    if (!running_.load(std::memory_order_acquire) && !threadStarted_)
        return;

    LOGI("Stopping ISO stream…");

    // 1. Signal the pipeline to stop accepting new submissions.
    running_.store(false, std::memory_order_release);

    // 2. Cancel all pending transfers. The cancellations are processed by
    //    libusb_handle_events() on the event thread; each fires the callback
    //    with LIBUSB_TRANSFER_CANCELLED, which decrements pendingCount_.
    for (int i = 0; i < NUM_TRANSFERS; i++) {
        if (transfers_[i]) {
            libusb_cancel_transfer(transfers_[i]);
        }
    }

    // 3. Wait for all callbacks to confirm cancellation (max 2 s).
    const int maxWaitMs = 2000;
    int waitedMs = 0;
    while (pendingCount_.load(std::memory_order_acquire) > 0 && waitedMs < maxWaitMs) {
        struct timespec ts{0, 5'000'000L}; // 5 ms
        nanosleep(&ts, nullptr);
        waitedMs += 5;
    }

    if (pendingCount_.load(std::memory_order_acquire) > 0) {
        LOGW("Timed out waiting for %d URBs to cancel — forcing thread exit",
             pendingCount_.load());
    } else {
        LOGI("All URBs cancelled cleanly");
    }

    // 4. Tell the event thread to exit and wait for it to finish.
    eventThreadExit_.store(true, std::memory_order_release);
    if (threadStarted_) {
        pthread_join(eventThread_, nullptr);
        threadStarted_ = false;
        LOGI("USB event thread joined");
    }

    // 5. Flush the ring buffer so stale samples do not bleed into the next session.
    ringBuffer_.flush();

    // 6. Release all URB memory.
    freeTransfers();

    LOGI("ISO stream stopped");
}

int UsbIsoStream::pushPcm(const float *samples, int count) {
    return static_cast<int>(ringBuffer_.write(samples, static_cast<uint32_t>(count)));
}
