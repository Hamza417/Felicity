"use strict";

/**
 * playback.js — Felicity Web Player
 *
 * Audio playback engine: starting a song, the waveform canvas seekbar,
 * syncing the right-side player pane, and all playback controls.
 */

/* ==================== Waveform Canvas ==================== */

const WAVEFORM_BARS = 64;

/**
 * A seeded random number generator so each song always gets the same
 * waveform shape — feels more personal, less random noise.
 *
 * @param   {number} seed  An integer seed (we use the song's database ID).
 * @returns {function(): number}
 */
function makeSeededRandom(seed) {
    let s = seed | 0;
    return function () {
        s = (Math.imul(s ^ (s >>> 16), 0x45d9f3b) + 0x3ade6857) | 0;
        return (s >>> 0) / 0xffffffff;
    };
}

/**
 * Builds the array of bar heights for the current song and stores it
 * in the shared {@link waveformBars} state variable.
 *
 * @param {number} songId  The database ID of the song being played.
 */
function buildWaveformBars(songId) {
    const rand = makeSeededRandom(songId || 42);
    waveformBars = Array.from({ length: WAVEFORM_BARS }, () => 0.15 + rand() * 0.85);
}

/**
 * Redraws the waveform canvas at the given playback progress.
 * Bars to the left of the playhead use the accent color;
 * bars to the right use a dimmer color — a simple but effective visual.
 *
 * @param {number} progress  0.0 = start of track, 1.0 = end of track.
 */
function drawWaveform(progress) {
    if (!waveformCanvas || waveformBars.length === 0) return;
    const dpr = window.devicePixelRatio || 1;
    const w   = waveformCanvas.clientWidth;
    const h   = waveformCanvas.clientHeight;
    if (w === 0 || h === 0) return;

    waveformCanvas.width  = w * dpr;
    waveformCanvas.height = h * dpr;

    const ctx    = waveformCanvas.getContext("2d");
    ctx.scale(dpr, dpr);

    const bars   = waveformBars.length;
    const barW   = (w - bars * 2) / bars;
    const splitX = progress * w;

    ctx.clearRect(0, 0, w, h);

    const styles   = getComputedStyle(document.documentElement);
    const accent   = styles.getPropertyValue("--accent").trim() || "#2980b9";
    const isDark   = document.documentElement.getAttribute("data-theme") !== "light";
    const dimColor = isDark ? "rgba(255,255,255,0.18)" : "rgba(0,0,0,0.15)";

    for (let i = 0; i < bars; i++) {
        const barH = waveformBars[i] * h;
        const x    = i * (barW + 2);
        const y    = (h - barH) / 2;
        ctx.fillStyle = (x + barW / 2) <= splitX ? accent : dimColor;
        ctx.beginPath();
        if (ctx.roundRect) ctx.roundRect(x, y, barW, barH, Math.min(barW / 2, 3));
        else ctx.rect(x, y, barW, barH);
        ctx.fill();
    }
}

window.addEventListener("resize", () => {
    if (nowId !== null) drawWaveform(audioEl.duration ? audioEl.currentTime / audioEl.duration : 0);
});

/* ==================== Player Pane ==================== */

/**
 * Opens the right-side player pane by adding the .open class, which
 * triggers the CSS width transition.
 */
function openPlayer() {
    playerPane.classList.add("open");
    /* Hide the FAB since the full player pane is now visible. */
    playerFab.classList.add("hidden");
    /* Redraw the waveform now that the pane has a real size to paint into. */
    requestAnimationFrame(() => {
        if (nowId !== null) drawWaveform(audioEl.duration ? audioEl.currentTime / audioEl.duration : 0);
    });
}

/**
 * Closes the player pane by removing .open — it slides back out to the right.
 * If a song is currently playing, the floating FAB pops up so the user can
 * bring the player back without losing their place in the library.
 */
function closePlayer() {
    playerPane.classList.remove("open");
    if (nowId !== null) playerFab.classList.remove("hidden");
}

playerCloseBtn.addEventListener("click", closePlayer);
playerFab.addEventListener("click", openPlayer);

/* ==================== Playing a Song ==================== */

/**
 * Starts playing a song, updates the player pane UI, and opens the pane
 * if it isn't already visible.
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

    nowTitle.textContent  = song.title  || song.name  || "Unknown";
    nowArtist.textContent = song.artist || "Unknown Artist";
    playerArt.src         = artUrl;

    seekBar.disabled = false;
    document.title   = song.title || song.name || "Felicity";

    buildWaveformBars(song.id);

    openPlayer();
    recordPlay(song.id);
    renderContent();
}

/**
 * Records a play event in the Android app's database. Best-effort — we don't
 * show an error if this request fails, since it's just a statistics update.
 *
 * @param {number} songId  The database ID of the played song.
 */
async function recordPlay(songId) {
    try {
        await fetch(`/api/songs/${songId}/played`, { method: "POST" });
    } catch (_) { /* best-effort */ }
}

/* ==================== Album Art ==================== */

playerArt.addEventListener("load",  () => { playerArt.style.opacity = "1"; });
playerArt.addEventListener("error", () => { playerArt.style.opacity = "0"; });

/* ==================== Audio Events ==================== */

audioEl.addEventListener("play",  () => { playIcon.textContent = "pause"; });
audioEl.addEventListener("pause", () => { playIcon.textContent = "play_arrow"; });

audioEl.addEventListener("timeupdate", () => {
    if (!seeking && audioEl.duration && isFinite(audioEl.duration)) {
        const pct = (audioEl.currentTime / audioEl.duration) * 100;
        seekBar.value = pct;
        elapsed.textContent   = fmtSec(audioEl.currentTime);
        remaining.textContent = fmtSec(audioEl.duration);
        drawWaveform(pct / 100);
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

/* ==================== Controls ==================== */

playBtn.addEventListener("click", () => {
    if (nowId === null && cache.songs && cache.songs.length > 0) {
        playSong(cache.songs[0], cache.songs);
        return;
    }
    if (audioEl.paused) audioEl.play(); else audioEl.pause();
});

prevBtn.addEventListener("click", () => {
    if (queueIdx > 0) playSong(queue[queueIdx - 1], queue);
});

nextBtn.addEventListener("click", () => {
    if (queueIdx < queue.length - 1) playSong(queue[queueIdx + 1], queue);
});

/* ==================== Seek ==================== */

seekBar.addEventListener("mousedown",  () => { seeking = true; });
seekBar.addEventListener("touchstart", () => { seeking = true; }, { passive: true });

seekBar.addEventListener("input", () => {
    drawWaveform(seekBar.value / 100);
    if (audioEl.duration) elapsed.textContent = fmtSec((seekBar.value / 100) * audioEl.duration);
});

seekBar.addEventListener("change", () => {
    if (audioEl.duration) audioEl.currentTime = (seekBar.value / 100) * audioEl.duration;
    seeking = false;
});

/* ==================== Volume ==================== */

/**
 * Swaps the volume icon glyph to match the current volume level.
 *
 * @param {number} vol  Volume from 0 to 1.
 */
function updateVolIcon(vol) {
    if      (vol === 0)  volIcon.textContent = "volume_off";
    else if (vol < 0.4)  volIcon.textContent = "volume_down";
    else                 volIcon.textContent = "volume_up";
}

volBar.addEventListener("input", () => {
    const vol = volBar.value / 100;
    audioEl.volume = vol;
    setFill(volBar, +volBar.value);
    updateVolIcon(vol);
});

setFill(volBar, 100);
updateVolIcon(1);

