"use strict";

/**
 * Felicity Web Player — player.js
 *
 * Handles section navigation, content rendering, audio playback, ambient
 * background, light/dark theming, right-click context menu, and queue management.
 *
 * @author Hamza417
 */

/* ─── DOM references ─────────────────────────── */

const el = id => document.getElementById(id);

const audioEl        = el("audioEl");
const ambientA       = el("ambientA");
const ambientB       = el("ambientB");
const contentBody    = el("contentBody");
const searchInput    = el("searchInput");
const itemCount      = el("itemCount");
const sectionTitle   = el("sectionTitle");
const backBtn        = el("backBtn");
const backLabel      = el("backLabel");
const nowArt         = el("nowArt");
const nowTitle       = el("nowTitle");
const nowArtist      = el("nowArtist");
const seekBar        = el("seekBar");
const elapsed        = el("elapsed");
const remaining      = el("remaining");
const playBtn        = el("playBtn");
const playIcon       = el("playIcon");
const prevBtn        = el("prevBtn");
const nextBtn        = el("nextBtn");
const volBar         = el("volBar");
const themeToggle    = el("themeToggle");
const themeIcon      = el("themeIcon");
const ctxMenu        = el("ctxMenu");
const ctxPlay        = el("ctxPlay");
const ctxAddQueue    = el("ctxAddQueue");
const ctxDelete      = el("ctxDelete");
const navItems       = document.querySelectorAll(".nav-item");

/* ─── App state ──────────────────────────────── */

/**
 * Section-keyed data cache so switching sections does not re-fetch.
 * @type {{ songs: object[]|null, albums: object[]|null, artists: object[]|null, genres: object[]|null }}
 */
const cache = { songs: null, albums: null, artists: null, genres: null };

/** Currently active top-level section name. */
let section     = "songs";

/**
 * Active drill-down state when the user has clicked into an album/artist/genre.
 * @type {{ type: string, name: string, items: object[] }|null}
 */
let drillItem   = null;

/** Current value of the search input, lowercased. */
let searchQuery = "";

/**
 * The current playback queue and index within it.
 * @type {object[]}
 */
let queue    = [];
let queueIdx = -1;

/** Database ID of the currently playing song, or null if nothing is playing. */
let nowId = null;

/** Whether the user is actively dragging the seek slider. */
let seeking = false;

/** Tracks which ambient layer (A or B) is currently opaque. */
let ambientActive  = "A";
let lastAmbientUrl = "";

/**
 * The song targeted by the most recent right-click context menu.
 * @type {{ song: object, ctx: object[] }|null}
 */
let ctxSong = null;

/* ─── Theme ──────────────────────────────────── */

const THEME_KEY = "felicity_theme";

/**
 * Applies the given theme to the document root and persists the choice.
 *
 * @param {"dark"|"light"} theme  The theme to activate.
 */
function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    themeIcon.textContent = theme === "dark" ? "dark_mode" : "light_mode";
    localStorage.setItem(THEME_KEY, theme);
}

themeToggle.addEventListener("click", () => {
    const current = document.documentElement.getAttribute("data-theme") || "dark";
    applyTheme(current === "dark" ? "light" : "dark");
});

// Initialize theme from storage or fall back to dark.
applyTheme(localStorage.getItem(THEME_KEY) || "dark");

/* ─── Ambient background ─────────────────────── */

/**
 * Cross-fades the full-screen background to a new artwork URL.
 * Alternates between two overlay divs to avoid a visible transition gap.
 *
 * @param {string} artUrl  URL of the artwork to use as the background.
 */
function setAmbient(artUrl) {
    if (!artUrl || artUrl === lastAmbientUrl) return;
    lastAmbientUrl = artUrl;
    const next = ambientActive === "A" ? ambientB : ambientA;
    const curr = ambientActive === "A" ? ambientA : ambientB;
    next.style.backgroundImage = `url('${artUrl}')`;
    next.classList.add("visible");
    curr.classList.remove("visible");
    ambientActive = ambientActive === "A" ? "B" : "A";
}

/* ─── Helpers ────────────────────────────────── */

/**
 * Formats milliseconds as a "m:ss" string.
 *
 * @param   {number} ms  Duration in milliseconds.
 * @returns {string}
 */
function fmtMs(ms) {
    const s   = Math.floor(Math.max(0, ms) / 1000);
    const min = Math.floor(s / 60);
    const sec = (s % 60).toString().padStart(2, "0");
    return `${min}:${sec}`;
}

/**
 * Formats seconds as a "m:ss" string.
 *
 * @param   {number} s  Duration in seconds.
 * @returns {string}
 */
function fmtSec(s) { return fmtMs(s * 1000); }

/**
 * HTML-escapes a string to prevent XSS when injecting into innerHTML.
 *
 * @param   {string} t  Raw text.
 * @returns {string}    Escaped HTML text.
 */
function esc(t) {
    const d = document.createElement("div");
    d.appendChild(document.createTextNode(t || ""));
    return d.innerHTML;
}

/**
 * Returns a pluralized count string.
 *
 * @param   {number} n  Count.
 * @param   {string} w  Singular word.
 * @returns {string}
 */
function plural(n, w) { return `${n} ${w}${n !== 1 ? "s" : ""}`; }

/**
 * Updates the CSS custom property that drives the filled portion of a slider.
 *
 * @param {HTMLInputElement} slider  The range input.
 * @param {number}           pct    Fill percentage from 0 to 100.
 */
function setFill(slider, pct) {
    slider.style.setProperty("--fill", `${Math.min(100, Math.max(0, pct))}%`);
}

/**
 * Fetches a URL and returns the parsed JSON body.
 *
 * @param   {string} url  Request URL.
 * @returns {Promise<any>}
 */
async function fetchJson(url) {
    const r = await fetch(url);
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
}

/* ─── Toast ──────────────────────────────────── */

/**
 * Displays a brief floating notification above the player bar.
 *
 * @param {string} msg       Message text.
 * @param {number} duration  Visible duration in milliseconds.
 */
function showToast(msg, duration = 2800) {
    document.querySelectorAll(".toast").forEach(t => t.remove());
    const t = document.createElement("div");
    t.className = "toast";
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(() => {
        t.style.transition = "opacity 0.30s";
        t.style.opacity    = "0";
        setTimeout(() => t.remove(), 320);
    }, duration);
}

/* ─── Section navigation ─────────────────────── */

const SECTION_LABELS = {
    songs: "Songs", albums: "Albums", artists: "Artists", genres: "Genres",
};

const SECTION_PLACEHOLDERS = {
    songs:   "Search songs, artists, albums…",
    albums:  "Search albums…",
    artists: "Search artists…",
    genres:  "Search genres…",
};

/**
 * Fetches section data from the server if it has not yet been loaded.
 *
 * @param {"songs"|"albums"|"artists"|"genres"} sec  Section to load.
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
 * Navigates to a top-level library section and re-renders the content area.
 *
 * @param {"songs"|"albums"|"artists"|"genres"} sec  Section to activate.
 */
async function goSection(sec) {
    section     = sec;
    drillItem   = null;
    searchQuery = "";
    searchInput.value = "";
    backBtn.classList.add("hidden");
    sectionTitle.textContent    = SECTION_LABELS[sec] || sec;
    searchInput.placeholder     = SECTION_PLACEHOLDERS[sec] || "Search…";

    navItems.forEach(b => b.classList.toggle("active", b.dataset.sec === sec));

    contentBody.classList.add("fading");
    await ensureLoaded(sec);
    setTimeout(() => {
        renderContent();
        contentBody.classList.remove("fading");
    }, 120);
}

/**
 * Drills into an album, artist, or genre and shows a filtered song list.
 *
 * @param {"albums"|"artists"|"genres"} type  Parent category type.
 * @param {string}                      name  Display name of the category item.
 */
async function drillDown(type, name) {
    const url   = `/api/${type}/songs?name=${encodeURIComponent(name)}`;
    const items = await fetchJson(url);
    drillItem         = { type, name, items };
    searchQuery       = "";
    searchInput.value = "";
    backLabel.textContent       = name;
    backBtn.classList.remove("hidden");
    sectionTitle.textContent    = name;
    searchInput.placeholder     = "Filter songs…";
    renderContent();
}

/**
 * Returns from a drill-down view back to the parent section grid.
 */
function goBack() {
    drillItem         = null;
    searchQuery       = "";
    searchInput.value = "";
    backBtn.classList.add("hidden");
    sectionTitle.textContent = SECTION_LABELS[section] || section;
    searchInput.placeholder  = SECTION_PLACEHOLDERS[section] || "Search…";
    renderContent();
}

/* ─── Content rendering ──────────────────────── */

/**
 * Re-renders the content area based on the current section, drill-down state,
 * and active search query.
 */
function renderContent() {
    const q = searchQuery.toLowerCase();

    if (drillItem) {
        let items = drillItem.items;
        if (q) items = items.filter(s =>
            (s.title  || s.name || "").toLowerCase().includes(q) ||
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
                (s.title  || s.name || "").toLowerCase().includes(q) ||
                (s.artist || "").toLowerCase().includes(q) ||
                (s.album  || "").toLowerCase().includes(q)
            );
            itemCount.textContent = q
                ? `${items.length} / ${plural((cache.songs || []).length, "song")}`
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
 * Builds a single song row DOM element.
 *
 * @param {object}   song  Song data object from the API.
 * @param {object[]} ctx   Full queue context for prev/next navigation.
 * @returns {HTMLDivElement}
 */
function makeSongRow(song, ctx) {
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
    row.addEventListener("click",        ()  => playSong(song, ctx));
    row.addEventListener("contextmenu",  e   => openCtxMenu(e, song, ctx));
    return row;
}

/**
 * Renders a flat song list into the content area.
 *
 * @param {object[]} songs  Filtered songs to display.
 * @param {object[]} ctx    Full queue context for playback.
 */
function renderSongList(songs, ctx) {
    contentBody.innerHTML = "";
    if (songs.length === 0) {
        contentBody.innerHTML =
            `<div class="empty-state"><span class="material-icons-round empty-icon">music_off</span>
             <p class="empty-title">No songs found</p></div>`;
        return;
    }
    const frag = document.createDocumentFragment();
    songs.forEach(song => frag.appendChild(makeSongRow(song, ctx)));
    contentBody.appendChild(frag);
}

/**
 * Renders an auto-fill album grid into the content area.
 *
 * @param {object[]} albums  Albums to display.
 */
function renderAlbumGrid(albums) {
    contentBody.innerHTML = "";
    if (albums.length === 0) {
        contentBody.innerHTML =
            `<div class="empty-state"><span class="material-icons-round empty-icon">album</span>
             <p class="empty-title">No albums found</p></div>`;
        return;
    }
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
    contentBody.appendChild(grid);
}

/**
 * Renders a list of artists with circular avatars.
 *
 * @param {object[]} artists  Artists to display.
 */
function renderArtistList(artists) {
    contentBody.innerHTML = "";
    if (artists.length === 0) {
        contentBody.innerHTML =
            `<div class="empty-state"><span class="material-icons-round empty-icon">person_off</span>
             <p class="empty-title">No artists found</p></div>`;
        return;
    }
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
    contentBody.appendChild(frag);
}

/**
 * Picks a deterministic accent color pair for a genre based on its name hash.
 *
 * @param   {string} name  Genre name.
 * @returns {{ bg: string, fg: string }}  Background and foreground CSS color strings.
 */
function genreColor(name) {
    const palette = [
        { bg: "rgba(124,58,237,0.18)",  fg: "#a78bfa" },
        { bg: "rgba(219,39,119,0.18)",  fg: "#f472b6" },
        { bg: "rgba(5,150,105,0.18)",   fg: "#34d399" },
        { bg: "rgba(217,119,6,0.18)",   fg: "#fbbf24" },
        { bg: "rgba(220,38,38,0.18)",   fg: "#f87171" },
        { bg: "rgba(37,99,235,0.18)",   fg: "#60a5fa" },
        { bg: "rgba(20,184,166,0.18)",  fg: "#2dd4bf" },
        { bg: "rgba(245,158,11,0.18)",  fg: "#fcd34d" },
    ];
    let hash = 0;
    for (const c of (name || "X")) hash = (hash * 31 + c.charCodeAt(0)) | 0;
    return palette[Math.abs(hash) % palette.length];
}

/**
 * Renders a list of genres with colored icon badges.
 *
 * @param {object[]} genres  Genres to display.
 */
function renderGenreList(genres) {
    contentBody.innerHTML = "";
    if (genres.length === 0) {
        contentBody.innerHTML =
            `<div class="empty-state"><span class="material-icons-round empty-icon">music_off</span>
             <p class="empty-title">No genres found</p></div>`;
        return;
    }
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
    contentBody.appendChild(frag);
}

/* ─── Context menu ───────────────────────────── */

/**
 * Opens the right-click context menu at the cursor position for a song row.
 *
 * @param {MouseEvent} e    The contextmenu event.
 * @param {object}     song Song data object.
 * @param {object[]}   ctx  Current queue context.
 */
function openCtxMenu(e, song, ctx) {
    e.preventDefault();
    ctxSong = { song, ctx };
    const menuW = 180;
    const menuH = 130;
    const x = Math.min(e.clientX, window.innerWidth  - menuW - 8);
    const y = Math.min(e.clientY, window.innerHeight - menuH - 8);
    ctxMenu.style.left = `${x}px`;
    ctxMenu.style.top  = `${y}px`;
    ctxMenu.classList.remove("hidden");
}

/** Closes and resets the context menu. */
function closeCtxMenu() {
    ctxMenu.classList.add("hidden");
    ctxSong = null;
}

document.addEventListener("click",       e => { if (!ctxMenu.contains(e.target)) closeCtxMenu(); });
document.addEventListener("keydown",     e => { if (e.key === "Escape") closeCtxMenu(); });
document.addEventListener("contextmenu", e => { if (!e.target.closest(".song-row")) closeCtxMenu(); });

ctxPlay.addEventListener("click", () => {
    if (ctxSong) playSong(ctxSong.song, ctxSong.ctx);
    closeCtxMenu();
});

ctxAddQueue.addEventListener("click", () => {
    if (!ctxSong) return;
    const { song } = ctxSong;
    closeCtxMenu();
    if (nowId === null) {
        // Nothing is playing — just start the song.
        playSong(song, [song]);
    } else {
        // Insert after the currently playing position.
        queue.splice(queueIdx + 1, 0, song);
        showToast(`"${song.title || song.name}" added to queue`);
    }
});

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
            // Remove from in-memory caches and refresh the view.
            if (cache.songs)   cache.songs   = cache.songs.filter(s => s.id !== song.id);
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

/* ─── Playback ───────────────────────────────── */

/**
 * Starts playing a song and updates all player bar UI elements.
 *
 * @param {object}   song  Song object from the API.
 * @param {object[]} ctx   Queue context used for previous/next navigation.
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
    nowArt.src = artUrl;
    setAmbient(artUrl);

    seekBar.disabled = false;
    document.title   = `${song.title || song.name} — Felicity`;

    recordPlay(song.id);
    renderContent();
}

/**
 * Notifies the Android app that a song was played, updating the room database
 * play-count statistics via a fire-and-forget POST request.
 *
 * @param {number} songId  Database ID of the song that was played.
 */
async function recordPlay(songId) {
    try {
        await fetch(`/api/songs/${songId}/played`, { method: "POST" });
    } catch (_) { /* best-effort, ignore network errors */ }
}

/* ─── Album art in player bar ────────────────── */

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

/* ─── Player controls ────────────────────────── */

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

/* ─── Volume ─────────────────────────────────── */

volBar.addEventListener("input", () => {
    audioEl.volume = volBar.value / 100;
    setFill(volBar, +volBar.value);
});

setFill(volBar, 100);

/* ─── Search ─────────────────────────────────── */

searchInput.addEventListener("input", () => {
    searchQuery = searchInput.value;
    renderContent();
});

/* ─── Nav items ──────────────────────────────── */

navItems.forEach(btn => {
    btn.addEventListener("click", () => goSection(btn.dataset.sec));
});

/* ─── Back button ────────────────────────────── */

backBtn.addEventListener("click", goBack);

/* ─── Initialize ─────────────────────────────── */

goSection("songs");

