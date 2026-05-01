"use strict";

/**
 * playback.js — Felicity Web Player
 *
 * Audio playback engine: starting a song, recording play events, reacting to
 * all HTMLAudioElement events, syncing the waveform seekbar, managing the mini
 * player strip, and handling the full-screen PlayerFaded overlay.
 */

/* ==================== Waveform Canvas ==================== */

/** How many vertical bars to draw in the waveform visualization. */
const WAVEFORM_BARS = 64;

/**
 * A tiny seeded random number generator so the waveform bars look the same
 * every time for the same song — no jarring reshuffles on re-render.
 *
 * @param   {number} seed  Any integer (we'll use the song's database ID).
 * @returns {function(): number}  A function that returns the next 0..1 value.
 */
function makeSeededRandom(seed) {
    let s = seed | 0;
    return function () {
        s = (Math.imul(s ^ (s >>> 16), 0x45d9f3b) + 0x3ade6857) | 0;
        return (s >>> 0) / 0xffffffff;
    };
}

/**
 * Generates a fresh set of waveform bar heights for the given song ID.
 * Heights are pseudo-random but always the same for the same song,
 * which makes it feel like the app "knows" what the song looks like.
 *
 * @param {number} songId  The database ID of the current song.
 */
function buildWaveformBars(songId) {
    const rand = makeSeededRandom(songId || 42);
    waveformBars = Array.from({ length: WAVEFORM_BARS }, () => 0.15 + rand() * 0.85);
}

/**
 * Redraws the waveform canvas at the given playback progress.
 * Bars to the left of the playhead get the accent color; bars to the right
 * get a dim translucent color — giving a nice visual "played / remaining" split.
 *
 * @param {number} progress  Playback progress from 0.0 to 1.0.
 */
function drawWaveform(progress) {
    if (!waveformCanvas || waveformBars.length === 0) return;
    const dpr = window.devicePixelRatio || 1;
    const w   = waveformCanvas.clientWidth;
    const h   = waveformCanvas.clientHeight;
    if (w === 0 || h === 0) return;

    waveformCanvas.width  = w * dpr;
    waveformCanvas.height = h * dpr;

    const ctx = waveformCanvas.getContext("2d");
    ctx.scale(dpr, dpr);

    const bars     = waveformBars.length;
    const totalGap = bars * 2;           /* 2px gap between each bar */
    const barW     = (w - totalGap) / bars;
    const splitX   = progress * w;

    ctx.clearRect(0, 0, w, h);

    const styles   = getComputedStyle(document.documentElement);
    const accent   = styles.getPropertyValue("--accent").trim() || "#05aefe";
    /* The unplayed portion uses a semi-transparent white/color based on theme. */
    const isDark   = document.documentElement.getAttribute("data-theme") !== "light";
    const dimColor = isDark ? "rgba(255,255,255,0.18)" : "rgba(8,20,40,0.16)";

    for (let i = 0; i < bars; i++) {
        const barH  = waveformBars[i] * h;
        const x     = i * (barW + 2);
        const y     = (h - barH) / 2;
        const midX  = x + barW / 2;

        ctx.fillStyle = midX <= splitX ? accent : dimColor;
        ctx.beginPath();
        if (ctx.roundRect) {
            ctx.roundRect(x, y, barW, barH, Math.min(barW / 2, 3));
        } else {
            ctx.rect(x, y, barW, barH);   /* fallback for older browsers */
        }
        ctx.fill();
    }
}

/* Redraw the waveform when the window is resized (the canvas physical size changes). */
window.addEventListener("resize", () => {
    if (nowId !== null) drawWaveform(audioEl.duration ? audioEl.currentTime / audioEl.duration : 0);
});

/* ==================== Player Overlay ==================== */

/**
 * Opens the full-screen player overlay with a smooth upward slide.
 */
function openPlayer() {
    playerOverlay.classList.add("open");
    /* Make sure the waveform is freshly drawn at the current size. */
    if (nowId !== null) {
        drawWaveform(audioEl.duration ? audioEl.currentTime / audioEl.duration : 0);
    }
}

/**
 * Closes the full-screen player overlay by sliding it back down.
 */
function closePlayer() {
    playerOverlay.classList.remove("open");
}

playerCloseBtn.addEventListener("click", closePlayer);
miniOpen.addEventListener("click", openPlayer);

/* ==================== Playing a Song ==================== */

/**
 * Starts playing a song, updates the player and mini player UI, and
 * triggers the ambient background cross-fade.
 *
 * @param {object}   song  Song data object from the API.
 * @param {object[]} ctx   Queue context for previous/next navigation.
 */
function playSong(song, ctx) {
    queue    = ctx;
    queueIdx = ctx.indexOf(song);
    nowId    = song.id;

    const artUrl = `/api/songs/${song.id}/art`;

    audioEl.src = `/api/songs/${song.id}/stream`;
    audioEl.load();
    audioEl.play().catch(() => {});

    /* Update the big player overlay. */
    nowTitle.textContent  = song.title  || song.name  || "Unknown";
    nowArtist.textContent = song.artist || "Unknown Artist";
    playerArt.src         = artUrl;
    playerBg.style.backgroundImage = `url('${artUrl}')`;

    /* Update the mini player strip too. */
    miniTitle.textContent  = song.title  || song.name  || "Unknown";
    miniArtist.textContent = song.artist || "Unknown Artist";
    nowArt.src             = artUrl;

    setAmbient(artUrl);

    seekBar.disabled = false;
    document.title   = song.title || song.name || "Felicity";

    /* Show the mini player (it was hidden before the first song). */
    miniPlayer.classList.remove("hidden");

    /* Build a new waveform pattern for this song. */
    buildWaveformBars(song.id);
    drawWaveform(0);

    recordPlay(song.id);
    renderContent();
}

/**
 * Notifies the Android app that a song has been played so its Room database
 * play-count statistics stay in sync. Best-effort fire-and-forget.
 *
 * @param {number} songId  The database ID of the song that was played.
 */
async function recordPlay(songId) {
    try {
        await fetch(`/api/songs/${songId}/played`, { method: "POST" });
    } catch (_) { /* best-effort */ }
}

/* ==================== Album Art Visibility ==================== */

playerArt.addEventListener("load",  () => { playerArt.style.opacity = "1"; });
playerArt.addEventListener("error", () => { playerArt.style.opacity = "0"; });
nowArt.addEventListener("load",     () => { nowArt.style.opacity    = "1"; });
nowArt.addEventListener("error",    () => { nowArt.style.opacity    = "0"; });

/* ==================== Audio Element Events ==================== */

audioEl.addEventListener("play", () => {
    playIcon.textContent     = "pause";
    miniPlayIcon.textContent = "pause";
});

audioEl.addEventListener("pause", () => {
    playIcon.textContent     = "play_arrow";
    miniPlayIcon.textContent = "play_arrow";
});

audioEl.addEventListener("timeupdate", () => {
    if (!seeking && audioEl.duration && isFinite(audioEl.duration)) {
        const pct      = (audioEl.currentTime / audioEl.duration) * 100;
        const progress = pct / 100;

        seekBar.value = pct;
        elapsed.textContent   = fmtSec(audioEl.currentTime);
        remaining.textContent = fmtSec(audioEl.duration);

        /* Animate the thin progress bar on the mini player. */
        miniSeekFill.style.width = `${pct}%`;

        /* Redraw the waveform canvas at the new position. */
        drawWaveform(progress);
    }
});

audioEl.addEventListener("loadedmetadata", () => {
    remaining.textContent = fmtSec(audioEl.duration);
    seekBar.value = 0;
    drawWaveform(0);
});

audioEl.addEventListener("ended", () => {
    if (queueIdx < queue.length - 1) playSong(queue[queueIdx + 1], queue);
});

/* ==================== Playback Controls ==================== */

/** Shared play/pause toggle — used by both the overlay button and the mini player. */
function togglePlayPause() {
    if (nowId === null && cache.songs && cache.songs.length > 0) {
        playSong(cache.songs[0], cache.songs);
        return;
    }
    if (audioEl.paused) audioEl.play(); else audioEl.pause();
}

playBtn.addEventListener("click", togglePlayPause);
miniPlayBtn.addEventListener("click", togglePlayPause);

prevBtn.addEventListener("click", () => {
    if (queueIdx > 0) playSong(queue[queueIdx - 1], queue);
});

nextBtn.addEventListener("click", () => {
    if (queueIdx < queue.length - 1) playSong(queue[queueIdx + 1], queue);
});

miniNextBtn.addEventListener("click", () => {
    if (queueIdx < queue.length - 1) playSong(queue[queueIdx + 1], queue);
});

/* ==================== Seek Bar ==================== */

seekBar.addEventListener("mousedown",  () => { seeking = true; });
seekBar.addEventListener("touchstart", () => { seeking = true; }, { passive: true });

seekBar.addEventListener("input", () => {
    const progress = seekBar.value / 100;
    drawWaveform(progress);
    if (audioEl.duration) elapsed.textContent = fmtSec(progress * audioEl.duration);
});

seekBar.addEventListener("change", () => {
    if (audioEl.duration) audioEl.currentTime = (seekBar.value / 100) * audioEl.duration;
    seeking = false;
});

/* ==================== Volume ==================== */

/**
 * Updates the volume icon to reflect the current level so users can see at
 * a glance whether the app is muted, quiet, or at full blast.
 *
 * @param {number} vol  Volume in the range 0–1.
 */
function updateVolIcon(vol) {
    if (vol === 0)       volIcon.textContent = "volume_off";
    else if (vol < 0.4)  volIcon.textContent = "volume_down";
    else                 volIcon.textContent = "volume_up";
}

volBar.addEventListener("input", () => {
    const vol = volBar.value / 100;
    audioEl.volume = vol;
    setFill(volBar, +volBar.value);
    updateVolIcon(vol);
});

/* Initialize volume fill and icon on load. */
setFill(volBar, 100);
updateVolIcon(1);

