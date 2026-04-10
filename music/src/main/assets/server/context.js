"use strict";

/**
 * context.js — Felicity Web Player
 *
 * Right-click context menu: positioning, show/hide, and all three actions
 * (Play, Add to Queue, Delete).
 */

/**
 * Positions and shows the context menu at the cursor location, clamped so it
 * never overflows the viewport.
 *
 * @param {MouseEvent} e    The native contextmenu event from a song row.
 * @param {object}     song Song data object from the API.
 * @param {object[]}   ctx  Queue context for the Play action.
 */
function openCtxMenu(e, song, ctx) {
    e.preventDefault();
    ctxSong = { song, ctx };
    const menuW = 180;
    const menuH = 132;
    const x = Math.min(e.clientX, window.innerWidth  - menuW - 8);
    const y = Math.min(e.clientY, window.innerHeight - menuH - 8);
    ctxMenu.style.left = `${x}px`;
    ctxMenu.style.top  = `${y}px`;
    ctxMenu.classList.remove("hidden");
}

/** Hides the context menu and clears the targeted song reference. */
function closeCtxMenu() {
    ctxMenu.classList.add("hidden");
    ctxSong = null;
}

/* Close on any click outside the menu, Escape key, or right-click elsewhere. */
document.addEventListener("click",       e => { if (!ctxMenu.contains(e.target)) closeCtxMenu(); });
document.addEventListener("keydown",     e => { if (e.key === "Escape") closeCtxMenu(); });
document.addEventListener("contextmenu", e => { if (!e.target.closest(".song-row")) closeCtxMenu(); });

/* Play — starts the song immediately. */
ctxPlay.addEventListener("click", () => {
    if (ctxSong) playSong(ctxSong.song, ctxSong.ctx);
    closeCtxMenu();
});

/* Add to Queue — inserts after the current track, or starts playback if idle. */
ctxAddQueue.addEventListener("click", () => {
    if (!ctxSong) return;
    const { song } = ctxSong;
    closeCtxMenu();
    if (nowId === null) {
        playSong(song, [song]);
    } else {
        queue.splice(queueIdx + 1, 0, song);
        showToast(`"${song.title || song.name}" added to queue`);
    }
});

/* Delete — asks for confirmation, then permanently removes the file via the API. */
ctxDelete.addEventListener("click", async () => {
    if (!ctxSong) return;
    const { song } = ctxSong;
    closeCtxMenu();

    const confirmed = window.confirm(
        `Delete "${song.title || song.name}"?\n\nThis will permanently remove the file from your device.`
    );
    if (!confirmed) return;

    try {
        const r = await fetch(`/api/songs/${song.id}`, { method: "DELETE" });
        if (r.ok) {
            if (cache.songs) cache.songs = cache.songs.filter(s => s.id !== song.id);
            cache.albums  = null;
            cache.artists = null;
            cache.genres  = null;
            renderContent();
            showToast(`"${song.title || song.name}" deleted`);
        } else {
            showToast("Could not delete — check app permissions.");
        }
    } catch (err) {
        showToast("Delete failed: " + err.message);
    }
});

