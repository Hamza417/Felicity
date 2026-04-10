"use strict";

/**
 * playback.js — Felicity Web Player
 *
 * Audio playback engine: starting a song, recording play events, reacting to
 * all HTMLAudioElement events, wiring the seek slider and playback controls,
 * and managing the volume icon + popup slider.
 */

/**
 * Starts playing a song, sets the player bar metadata, and triggers an ambient
 * background cross-fade.
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
    nowArt.src = artUrl;
    setAmbient(artUrl);

    seekBar.disabled = false;
    document.title   = `${song.title || song.name}`;

    recordPlay(song.id);
    renderContent();
}

/**
 * Notifies the Android app that a song has been played so its Room database
 * play-count statistics stay in sync.  This is a best-effort fire-and-forget
 * request — network errors are silently swallowed.
 *
 * @param {number} songId  The database ID of the song that was played.
 */
async function recordPlay(songId) {
    try {
        await fetch(`/api/songs/${songId}/played`, { method: "POST" });
    } catch (_) { /* best-effort */ }
}

/* ─── Album art visibility in the player bar ─ */

nowArt.addEventListener("load",  () => { nowArt.style.opacity = "1"; });
nowArt.addEventListener("error", () => { nowArt.style.opacity = "0"; });

/* ─── Audio element events ───────────────────── */

audioEl.addEventListener("play",  () => { playIcon.textContent = "pause"; });
audioEl.addEventListener("pause", () => { playIcon.textContent = "play_arrow"; });

audioEl.addEventListener("timeupdate", () => {
    if (!seeking && audioEl.duration && isFinite(audioEl.duration)) {
        const pct = (audioEl.currentTime / audioEl.duration) * 100;
        seekBar.value = pct;
        setFill(seekBar, pct);
        elapsed.textContent   = fmtSec(audioEl.currentTime);
        remaining.textContent = fmtSec(audioEl.duration);
    }
});

audioEl.addEventListener("loadedmetadata", () => {
    remaining.textContent = fmtSec(audioEl.duration);
    setFill(seekBar, 0);
    seekBar.value = 0;
});

audioEl.addEventListener("ended", () => {
    if (queueIdx < queue.length - 1) playSong(queue[queueIdx + 1], queue);
});

/* ─── Playback controls ──────────────────────── */

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

/* ─── Seek bar ───────────────────────────────── */

seekBar.addEventListener("mousedown",  () => { seeking = true; });
seekBar.addEventListener("touchstart", () => { seeking = true; }, { passive: true });

seekBar.addEventListener("input", () => {
    setFill(seekBar, +seekBar.value);
    if (audioEl.duration) elapsed.textContent = fmtSec((seekBar.value / 100) * audioEl.duration);
});

seekBar.addEventListener("change", () => {
    if (audioEl.duration) audioEl.currentTime = (seekBar.value / 100) * audioEl.duration;
    seeking = false;
});

/* ─── Volume popup — JS-managed open/close with a grace-period ─── */

/**
 * Timer used to delay closing the volume popup so the cursor can move
 * from the icon button to the slider without the popup disappearing.
 */
let volCloseTimer;

volWrap.addEventListener("mouseenter", () => {
    clearTimeout(volCloseTimer);
    volWrap.classList.add("vol-open");
});

volWrap.addEventListener("mouseleave", () => {
    volCloseTimer = setTimeout(() => volWrap.classList.remove("vol-open"), 200);
});

/* ─── Volume popup slider ────────────────────── */

/**
 * Updates the volume icon glyph to reflect the current volume level.
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
    volPct.textContent = `${Math.round(volBar.value)}%`;
    updateVolIcon(vol);
});

/* Initialize volume slider fill and icon. */
setFill(volBar, 100);
updateVolIcon(1);

