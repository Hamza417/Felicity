#include "usb_iso_stream.h"
#include "felicity_usb_dac.h"

#include <cstring>
#include <cstdlib>
#include <climits>
#include <cerrno>
#include <sched.h>    // SCHED_FIFO, sched_param
#include <time.h>     // nanosleep

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
        const float f = (src[i] < -1.f) ? -1.f : (src[i] > 1.f) ? 1.f : src[i];

        if (subslotSize_ == 2) {
            // 16-bit PCM: scale to [-32768, 32767]
            const int16_t s16 = static_cast<int16_t>(f * 32767.f);
            dst[0] = static_cast<uint8_t>( s16 & 0xFF);
            dst[1] = static_cast<uint8_t>((s16 >> 8) & 0xFF);
            dst += 2;

        } else if (subslotSize_ == 3) {
            // 24-bit PCM packed into 3 bytes (the most common high-res DAC format)
            const int32_t s24 = static_cast<int32_t>(f * 8388607.f); // 2^23 - 1
            dst[0] = static_cast<uint8_t>( s24 & 0xFF);
            dst[1] = static_cast<uint8_t>((s24 >> 8) & 0xFF);
            dst[2] = static_cast<uint8_t>((s24 >> 16) & 0xFF);
            dst += 3;

        } else {
            // 32-bit PCM (subslotSize == 4), covers both 24-bit padded and true 32-bit
            const int32_t s32 = static_cast<int32_t>(f * 2147483647.f); // 2^31 - 1
            dst[0] = static_cast<uint8_t>( s32 & 0xFF);
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
 * Fills [transfer]'s buffer by reading from the ring buffer and converting each
 * packet worth of frames to packed PCM. Any packet that cannot be satisfied from
 * live ring-buffer data is zero-filled (silence) — this is the underrun guard.
 *
 * Packet sizing uses a fractional accumulator so that the host delivers EXACTLY
 * [sampleRate_] frames per second to the DAC. For 44.1 kHz this alternates between
 * 44 and 45 frames per 1 ms packet; for 48 kHz it is always 48. Using [maxPacketSize]
 * directly was sending far too many frames per packet, overflowing the DAC's internal
 * buffer and producing screeching artifacts.
 *
 * The frame count per packet is capped by BOTH the hardware's [maxPacketSize_] AND
 * by the 512-float scratch buffer. Without the second cap a high-channel-count / high-rate
 * device could request more frames than the scratch buffer holds, and the gap between
 * what we write and [pkt.length] would be filled with stale garbage — causing audible
 * zombie-audio loops.
 */
void UsbIsoStream::fillTransferBuffer(libusb_transfer *transfer) {
    const int bytesPerFrame = subslotSize_ * channels_;
    if (bytesPerFrame <= 0) return;

    // The scratch buffer holds one packet's worth of interleaved float samples.
    // Capping here (not later) ensures framesInPkt, actualBytes, and the number
    // of bytes we actually write are always consistent — no gap, no garbage.
    const int maxScratchFrames = 512 / channels_;

    uint8_t *bufferPtr = transfer->buffer;

    for (int p = 0; p < transfer->num_iso_packets; p++) {
        libusb_iso_packet_descriptor &pkt = transfer->iso_packet_desc[p];

        // Accumulator-based frame count: advance by sampleRate_ per ms, take the integer
        // part as the frame count for this packet, carry the remainder forward.
        // Example for 44100 Hz: 9 packets get 44 frames, 1 packet gets 45 frames per 10 ms.
        sampleAccum_ += sampleRate_;
        const int nominalFrames = static_cast<int>(sampleAccum_ / 1000u);
        sampleAccum_ %= 1000u;

        // Apply both limits before computing actualBytes so the buffer write and
        // pkt.length are always equal — zero-padding requirement satisfied.
        const int maxFromHardware = maxPacketSize_ / bytesPerFrame;
        const int hardCap = (maxFromHardware < maxScratchFrames) ? maxFromHardware
                                                                 : maxScratchFrames;
        const int framesInPkt = (nominalFrames < hardCap) ? nominalFrames : hardCap;
        const int actualBytes = framesInPkt * bytesPerFrame;

        // Tell libusb exactly how many bytes to send so it doesn't transmit old data.
        pkt.length = static_cast<unsigned int>(actualBytes);

        if (framesInPkt > 0) {
            float scratch[512]{};

            // Read source-channel samples from the ring buffer. For a mono track through
            // a stereo DAC, srcChannels_ == 1 and channels_ == 2 — we read half as many
            // samples as the DAC expects, then upmix below.
            // The ring buffer already zero-pads any shortfall, so underruns produce
            // silence rather than garbage without any extra handling here.
            const int srcSamplesNeeded = framesInPkt * srcChannels_;
            const uint32_t got = ringBuffer_.read(scratch, static_cast<uint32_t>(srcSamplesNeeded));

            if (got < static_cast<uint32_t>(srcSamplesNeeded)) {
                const uint32_t n = underrunCount_.fetch_add(1, std::memory_order_relaxed) + 1;
                if (n == 1 || n % 500 == 0) {
                    LOGD("USB underrun: needed %d samples, got %u — padding with silence (count: %u)",
                         srcSamplesNeeded, got, n);
                }
            } else {
                underrunCount_.store(0, std::memory_order_relaxed);
            }

            // Mono-to-stereo upmix: duplicate each mono sample into both L and R channels.
            // Processing frames from last to first avoids overwriting a source sample before
            // it has been copied, since the stereo frame at index f writes to scratch[f*2]
            // and scratch[f*2+1], and the mono source at index f sits at scratch[f].
            if (srcChannels_ == 1 && channels_ == 2) {
                for (int f = framesInPkt - 1; f >= 0; --f) {
                    const float mono = scratch[f];
                    scratch[f * 2] = mono;
                    scratch[f * 2 + 1] = mono;
                }
            }

            convertAndPack(scratch, bufferPtr, framesInPkt);
        }

        // Advance past only the bytes we actually wrote, not the full maxPacketSize.
        bufferPtr += actualBytes;
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
                this,          // user_data → routed to onTransferComplete
                0              // no timeout — isochronous transfers don't use one
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
        if (ret == LIBUSB_ERROR_NO_DEVICE) {
            // The DAC was physically unplugged. Stop accepting new submissions and
            // let the remaining in-flight URBs drain naturally. The Kotlin layer's
            // UsbDacReceiver will detect the disconnect and call nativeReleaseUsb.
            LOGE("USB device no longer present — halting stream");
            running_.store(false, std::memory_order_release);
        } else {
            LOGE("libusb_submit_transfer failed after callback: %s — stream may stall",
                 libusb_strerror((libusb_error) ret));
        }
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

    // 1 ms poll interval — tight enough to catch URB completions within one USB
    // microframe boundary and keep the isochronous pipeline fed without burning CPU
    // in a busy-spin. libusb_handle_events_timeout returns immediately when events
    // are already pending, so this timeout only matters during quiet periods.
    struct timeval tv{0, 1000};

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
                         uint32_t sampleRate,
                         int srcChannels) {
    if (alt.endpointAddress == 0 || alt.wMaxPacketSize == 0) {
        LOGE("Cannot start ISO stream — alt-setting has no valid endpoint");
        return false;
    }

    ctx_ = ctx;
    subslotSize_ = subslotSize;
    bitResolution_ = bitResolution;
    maxPacketSize_ = alt.wMaxPacketSize;
    channels_ = (alt.bNrChannels > 0) ? alt.bNrChannels : 2;
    sampleRate_ = (sampleRate > 0) ? sampleRate : 48000u;
    srcChannels_ = (srcChannels > 0) ? srcChannels : channels_;
    sampleAccum_ = 0;  // Reset fractional packet accumulator for clean start

    LOGI("Starting ISO stream: ep=0x%02X maxPkt=%d subslot=%d bits=%d dacCh=%d srcCh=%d rate=%u",
         alt.endpointAddress, maxPacketSize_, subslotSize_, bitResolution_,
         channels_, srcChannels_, sampleRate_);

    if (!allocateTransfers(handle, alt.endpointAddress, maxPacketSize_)) {
        freeTransfers();
        return false;
    }

    // Pre-fill ring buffer with silence so the first batch of URBs have something
    // to drain even before the DSP thread pushes any real audio.
    ringBuffer_.flush();

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
    if (!running_.load(std::memory_order_acquire) && !threadStarted_) return;

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

void UsbIsoStream::flushRingBuffer() {
    ringBuffer_.flush();
}

