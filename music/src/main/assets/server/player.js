"use strict";

/* ═══════════════════════════════════════════════
   Felicity Web Player — player.js
   ═══════════════════════════════════════════════ */

/* ─── DOM refs ───────────────────────────────── */
const el = id => document.getElementById(id);

const audioEl      = el("audioEl");
const ambientA     = el("ambientA");
const ambientB     = el("ambientB");
const sidebarBody  = el("sidebarBody");
const searchInput  = el("searchInput");
const itemCount    = el("itemCount");
const backBtn      = el("backBtn");
const backLabel    = el("backLabel");
const emptyState   = el("emptyState");
const playerContent = el("playerContent");
const nowArt       = el("nowArt");
const nowTitle     = el("nowTitle");
const nowArtist    = el("nowArtist");
const nowAlbum     = el("nowAlbum");
const seekBar      = el("seekBar");
const elapsed      = el("elapsed");
const remaining    = el("remaining");
const playBtn      = el("playBtn");
const playIcon     = el("playIcon");
const prevBtn      = el("prevBtn");
const nextBtn      = el("nextBtn");
const volBar       = el("volBar");
const dockBtns     = document.querySelectorAll(".dock-btn");

/* ─── App State ──────────────────────────────── */

/** Cache for each section so switching is instant after first load. */
const cache = { songs: null, albums: null, artists: null, genres: null };

let section     = "songs";   // current dock section
let drillItem   = null;      // { type, name, items } or null
let searchQuery = "";

/** Current playback queue (array of song objects) and position. */
let queue    = [];
let queueIdx = -1;

/** ID of the currently playing song. */
let nowId = null;

/** Whether the user is currently dragging the seek bar. */
let seeking = false;

/** Which ambient layer is currently active ("A" | "B"). */
let ambientActive = "A";
let lastAmbientUrl = "";

/* ─── Ambient Background ─────────────────────── */

/**
 * Cross-fades the full-screen background blur to the given art URL.
 * Uses two overlapping divs and alternates opacity between them.
 *
 * @param {string} artUrl  URL of the artwork to use as background.
 */
function setAmbient(artUrl) {
    if (!artUrl || artUrl === lastAmbientUrl) return;
    lastAmbientUrl = artUrl;

    const next = ambientActive === "A" ? ambientB : ambientA;
    const curr = ambientActive === "A" ? ambientA : ambientB;

    next.style.backgroundImage = `url('${CSS.escape ? artUrl : artUrl}')`;
    next.classList.add("visible");
    curr.classList.remove("visible");
    ambientActive = ambientActive === "A" ? "B" : "A";
}

/* ─── Helpers ────────────────────────────────── */

function fmtMs(ms) {
    const s   = Math.floor(Math.max(0, ms) / 1000);
    const min = Math.floor(s / 60);
    const sec = (s % 60).toString().padStart(2, "0");
    return `${min}:${sec}`;
}

function fmtSec(s) { return fmtMs(s * 1000); }

function esc(t) {
    const d = document.createElement("div");
    d.appendChild(document.createTextNode(t || ""));
    return d.innerHTML;
}

function plural(n, w) { return `${n} ${w}${n !== 1 ? "s" : ""}`; }

/**
 * Updates a range slider's CSS custom property so the filled portion
 * renders in the accent color.
 *
 * @param {HTMLInputElement} slider  The range input element.
 * @param {number}           pct    Fill percentage (0–100).
 */
function setFill(slider, pct) {
    slider.style.setProperty("--fill", `${Math.min(100, Math.max(0, pct))}%`);
}

async function fetchJson(url) {
    const r = await fetch(url);
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
}

/* ─── Section Loading & Navigation ──────────── */

/**
 * Loads the data for a given section, using the in-memory cache when available.
 *
 * @param {string} sec  One of "songs" | "albums" | "artists" | "genres".
 */
async function ensureLoaded(sec) {
    if (cache[sec]) return;
    const endpoints = {
        songs:   "/api/songs",
        albums:  "/api/albums",
        artists: "/api/artists",
        genres:  "/api/genres",
    };
    cache[sec] = await fetchJson(endpoints[sec]);
}

/**
 * Switches the dock to a top-level section and re-renders the sidebar.
 *
 * @param {string} sec  Section to navigate to.
 */
async function goSection(sec) {
    section     = sec;
    drillItem   = null;
    searchQuery = "";
    searchInput.value = "";
    backBtn.classList.add("hidden");

    dockBtns.forEach(b => b.classList.toggle("active", b.dataset.sec === sec));

    const placeholders = {
        songs:   "Search songs, artists, albums…",
        albums:  "Search albums…",
        artists: "Search artists…",
        genres:  "Search genres…",
    };
    searchInput.placeholder = placeholders[sec] || "Search…";

    sidebarBody.classList.add("fading");
    await ensureLoaded(sec);
    setTimeout(() => {
        renderSidebar();
        sidebarBody.classList.remove("fading");
    }, 130);
}

/**
 * Drills into an album, artist, or genre and shows its songs.
 *
 * @param {string} type  "albums" | "artists" | "genres".
 * @param {string} name  The value to filter by.
 */
async function drillDown(type, name) {
    const url   = `/api/${type}/songs?name=${encodeURIComponent(name)}`;
    const items = await fetchJson(url);
    drillItem         = { type, name, items };
    searchQuery       = "";
    searchInput.value = "";
    backLabel.textContent = name;
    backBtn.classList.remove("hidden");
    searchInput.placeholder = "Filter songs…";
    renderSidebar();
}

/** Returns to the section grid after drilling into an album/artist/genre. */
function goBack() {
    drillItem         = null;
    searchQuery       = "";
    searchInput.value = "";
    backBtn.classList.add("hidden");
    const placeholders = {
        albums:  "Search albums…",
        artists: "Search artists…",
        genres:  "Search genres…",
    };
    searchInput.placeholder = placeholders[section] || "Search…";
    renderSidebar();
}

/* ─── Sidebar Rendering ──────────────────────── */

/** Top-level dispatcher that renders the correct view into the sidebar. */
function renderSidebar() {
    const q = searchQuery.toLowerCase();

    if (drillItem) {
        let items = drillItem.items;
        if (q) items = items.filter(s =>
            (s.title || s.name || "").toLowerCase().includes(q) ||
            (s.artist || "").toLowerCase().includes(q)
        );
        itemCount.textContent = plural(items.length, "song");
        renderSongList(items, drillItem.items);
        return;
    }

    switch (section) {
        case "songs": {
            let items = cache.songs || [];
            if (q) items = items.filter(s =>
                (s.title || s.name || "").toLowerCase().includes(q) ||
                (s.artist || "").toLowerCase().includes(q) ||
                (s.album  || "").toLowerCase().includes(q)
            );
            itemCount.textContent = q
                ? `${items.length} / ${plural(cache.songs.length, "song")}`
                : plural(items.length, "song");
            renderSongList(items, items);
            break;
        }
        case "albums": {
            let items = cache.albums || [];
            if (q) items = items.filter(a =>
                (a.name   || "").toLowerCase().includes(q) ||
                (a.artist || "").toLowerCase().includes(q)
            );
            itemCount.textContent = plural(items.length, "album");
            renderAlbumGrid(items);
            break;
        }
        case "artists": {
            let items = cache.artists || [];
            if (q) items = items.filter(a => (a.name || "").toLowerCase().includes(q));
            itemCount.textContent = plural(items.length, "artist");
            renderArtistList(items);
            break;
        }
        case "genres": {
            let items = cache.genres || [];
            if (q) items = items.filter(g => (g.name || "").toLowerCase().includes(q));
            itemCount.textContent = plural(items.length, "genre");
            renderGenreList(items);
            break;
        }
    }
}

/**
 * Renders a flat list of songs into the sidebar.
 *
 * @param {object[]} songs  Filtered list of songs to display.
 * @param {object[]} ctx    Full queue context (used for next/prev navigation).
 */
function renderSongList(songs, ctx) {
    sidebarBody.innerHTML = "";
    const frag = document.createDocumentFragment();
    songs.forEach(song => {
        const row = document.createElement("div");
        row.className = "song-row" + (song.id === nowId ? " active" : "");
        row.innerHTML = `
            <img class="song-thumb" src="/api/songs/${song.id}/art" loading="lazy" alt=""
                 onerror="this.style.opacity='0'">
            <div class="song-info">
                <div class="song-name">${esc(song.title || song.name)}</div>
                <div class="song-sub">${esc(song.artist || "Unknown Artist")}</div>
            </div>
            <span class="song-dur">${fmtMs(song.duration)}</span>`;
        row.addEventListener("click", () => playSong(song, ctx));
        frag.appendChild(row);
    });
    sidebarBody.appendChild(frag);
}

/** Renders a two-column album grid. */
function renderAlbumGrid(albums) {
    sidebarBody.innerHTML = "";
    const grid = document.createElement("div");
    grid.className = "album-grid";
    albums.forEach(album => {
        const card = document.createElement("div");
        card.className = "album-card";
        card.innerHTML = `
            <img class="album-cover" src="/api/songs/${album.coverSongId}/art"
                 loading="lazy" alt="" onerror="this.style.opacity='0'">
            <div class="album-info">
                <div class="album-name">${esc(album.name)}</div>
                <div class="album-meta">${esc(album.artist || "")} · ${plural(album.songCount, "song")}</div>
            </div>`;
        card.addEventListener("click", () => drillDown("albums", album.name));
        grid.appendChild(card);
    });
    sidebarBody.appendChild(grid);
}

/** Renders a list of artists with circular avatars. */
function renderArtistList(artists) {
    sidebarBody.innerHTML = "";
    const frag = document.createDocumentFragment();
    artists.forEach(artist => {
        const row = document.createElement("div");
        row.className = "artist-row";
        row.innerHTML = `
            <img class="artist-avatar" src="/api/songs/${artist.coverSongId}/art"
                 loading="lazy" alt="" onerror="this.style.opacity='0'">
            <div class="row-info">
                <div class="row-name">${esc(artist.name)}</div>
                <div class="row-sub">${plural(artist.songCount, "song")} · ${plural(artist.albumCount, "album")}</div>
            </div>
            <span class="material-icons-round chevron">chevron_right</span>`;
        row.addEventListener("click", () => drillDown("artists", artist.name));
        frag.appendChild(row);
    });
    sidebarBody.appendChild(frag);
}

/**
 * Picks a deterministic accent color for a genre based on its name.
 *
 * @param  {string} name  Genre name.
 * @returns {{ bg: string, fg: string }}  Background and foreground CSS colors.
 */
function genreColor(name) {
    const palette = [
        { bg: "rgba(124, 58, 237, 0.18)", fg: "#a78bfa" },
        { bg: "rgba(219, 39, 119, 0.18)", fg: "#f472b6" },
        { bg: "rgba(5, 150, 105, 0.18)",  fg: "#34d399" },
        { bg: "rgba(217, 119, 6, 0.18)",  fg: "#fbbf24" },
        { bg: "rgba(220, 38, 38, 0.18)",  fg: "#f87171" },
        { bg: "rgba(37, 99, 235, 0.18)",  fg: "#60a5fa" },
        { bg: "rgba(20, 184, 166, 0.18)", fg: "#2dd4bf" },
        { bg: "rgba(245, 158, 11, 0.18)", fg: "#fcd34d" },
    ];
    let hash = 0;
    for (const c of (name || "X")) hash = (hash * 31 + c.charCodeAt(0)) | 0;
    return palette[Math.abs(hash) % palette.length];
}

/** Renders a list of genres with colored icon badges. */
function renderGenreList(genres) {
    sidebarBody.innerHTML = "";
    const frag = document.createDocumentFragment();
    genres.forEach(genre => {
        const { bg, fg } = genreColor(genre.name);
        const row = document.createElement("div");
        row.className = "genre-row";
        row.innerHTML = `
            <div class="genre-icon-wrap" style="background:${bg}; border:1px solid ${fg}33;">
                <span class="material-icons-round" style="color:${fg}">music_note</span>
            </div>
            <div class="row-info">
                <div class="row-name">${esc(genre.name || "Unknown")}</div>
                <div class="row-sub">${plural(genre.songCount, "song")}</div>
            </div>
            <span class="material-icons-round chevron">chevron_right</span>`;
        row.addEventListener("click", () => drillDown("genres", genre.name));
        frag.appendChild(row);
    });
    sidebarBody.appendChild(frag);
}

/* ─── Playback ───────────────────────────────── */

/**
 * Starts playing a song and updates all player UI elements.
 *
 * @param {object}   song  Song object from the JSON API.
 * @param {object[]} ctx   The queue context (all songs in the current view).
 */
function playSong(song, ctx) {
    queue    = ctx;
    queueIdx = ctx.indexOf(song);
    nowId    = song.id;

    const artUrl = `/api/songs/${song.id}/art`;

    audioEl.src = `/api/songs/${song.id}/stream`;
    audioEl.load();
    audioEl.play().catch(() => {});

    nowTitle.textContent  = song.title || song.name || "Unknown";
    nowArtist.textContent = song.artist || "Unknown Artist";
    nowAlbum.textContent  = song.album  || "";

    nowArt.src = artUrl;
    setAmbient(artUrl);

    emptyState.style.display     = "none";
    playerContent.style.display  = "flex";
    document.title = `${song.title || song.name} — Felicity`;

    recordPlay(song.id);
    renderSidebar();
}

/**
 * Notifies the Android app that this song was played so it can update
 * the play-count statistics in the Room database.
 *
 * @param {number} songId  Database row ID of the played song.
 */
async function recordPlay(songId) {
    try {
        await fetch(`/api/songs/${songId}/played`, { method: "POST" });
    } catch (_) { /* best-effort, ignore errors */ }
}

/* ─── Audio Element Events ───────────────────── */

audioEl.addEventListener("play", () => {
    playIcon.textContent = "pause";
});

audioEl.addEventListener("pause", () => {
    playIcon.textContent = "play_arrow";
});

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
    if (queueIdx < queue.length - 1) {
        playSong(queue[queueIdx + 1], queue);
    }
});

/* ─── Player Controls ────────────────────────── */

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

/* ─── Seek Bar ───────────────────────────────── */

seekBar.addEventListener("mousedown",  () => { seeking = true; });
seekBar.addEventListener("touchstart", () => { seeking = true; }, { passive: true });

seekBar.addEventListener("input", () => {
    setFill(seekBar, +seekBar.value);
    if (audioEl.duration) {
        elapsed.textContent = fmtSec((seekBar.value / 100) * audioEl.duration);
    }
});

seekBar.addEventListener("change", () => {
    if (audioEl.duration) audioEl.currentTime = (seekBar.value / 100) * audioEl.duration;
    seeking = false;
});

/* ─── Volume ─────────────────────────────────── */

volBar.addEventListener("input", () => {
    audioEl.volume = volBar.value / 100;
    setFill(volBar, +volBar.value);
});

setFill(volBar, 100);

/* ─── Search ─────────────────────────────────── */

searchInput.addEventListener("input", () => {
    searchQuery = searchInput.value;
    renderSidebar();
});

/* ─── Dock ───────────────────────────────────── */

dockBtns.forEach(btn => {
    btn.addEventListener("click", () => goSection(btn.dataset.sec));
});

/* ─── Back Button ────────────────────────────── */

backBtn.addEventListener("click", goBack);

/* ─── Init ───────────────────────────────────── */

goSection("songs");

