"use strict";

/**
 * state.js — Felicity Web Player
 *
 * All shared mutable state and cached DOM references used across every other
 * module. This file must be loaded immediately after helpers.js.
 */

/* DOM references — player overlay */
const audioEl        = el("audioEl");
const playerOverlay  = el("playerOverlay");
const playerCloseBtn = el("playerCloseBtn");
const playerBg       = el("playerBg");
const playerArt      = el("playerArt");
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
const volIcon        = el("volIcon");
const waveformCanvas = el("waveformCanvas");

/* DOM references — mini player */
const miniPlayer    = el("miniPlayer");
const miniOpen      = el("miniOpen");
const miniTitle     = el("miniTitle");
const miniArtist    = el("miniArtist");
const miniPlayBtn   = el("miniPlayBtn");
const miniPlayIcon  = el("miniPlayIcon");
const miniNextBtn   = el("miniNextBtn");
const miniSeekFill  = el("miniSeekFill");
const nowArt        = el("nowArt");

/* DOM references — ambient background */
const ambientA = el("ambientA");
const ambientB = el("ambientB");

/* DOM references — screens */
const screenDashboard = el("screenDashboard");
const screenLibrary   = el("screenLibrary");
const dashboardBody   = el("dashboardBody");
const contentBody     = el("contentBody");

/* DOM references — library header controls */
const searchInput    = el("searchInput");
const itemCount      = el("itemCount");
const sectionTitle   = el("sectionTitle");
const backBtn        = el("backBtn");
const backLabel      = el("backLabel");
const viewToggle     = el("viewToggle");
const viewToggleIcon = el("viewToggleIcon");

/* DOM references — other */
const themeToggle = el("themeToggle");
const themeIcon   = el("themeIcon");
const ctxMenu     = el("ctxMenu");
const ctxPlay     = el("ctxPlay");
const ctxAddQueue = el("ctxAddQueue");
const ctxDelete   = el("ctxDelete");
const navItems    = document.querySelectorAll(".nav-item");

/* ==================== App State ==================== */

/**
 * Per-section data cache — avoids repeated API calls when switching tabs.
 *
 * @type {{ songs: object[]|null, albums: object[]|null, artists: object[]|null, genres: object[]|null }}
 */
const cache = { songs: null, albums: null, artists: null, genres: null };

/** Currently active section key — can be "dashboard", "songs", "albums", "artists", or "genres". */
let section = "dashboard";

/**
 * Drill-down context when the user has navigated into an album, artist, or genre.
 * Null when at the top-level library view.
 *
 * @type {{ type: string, name: string, items: object[] }|null}
 */
let drillItem = null;

/** Live value of the search input. */
let searchQuery = "";

/** Current playback queue — array of song objects from the API. */
let queue    = [];

/** Index of the currently playing song within {@link queue}. */
let queueIdx = -1;

/** Database ID of the currently playing song, or null when idle. */
let nowId = null;

/** True while the user is dragging the seek slider. */
let seeking = false;

/**
 * Per-section view mode preference — "list" or "grid".
 * Albums default to "grid"; everything else defaults to "list".
 *
 * @type {{ songs: string, albums: string, artists: string, genres: string }}
 */
const VIEWMODE_DEFAULTS = { songs: "list", albums: "grid", artists: "list", genres: "list" };
const viewMode = (() => {
    try {
        const saved = JSON.parse(localStorage.getItem("felicity_viewMode") || "null");
        return (saved && typeof saved === "object")
            ? Object.assign({ ...VIEWMODE_DEFAULTS }, saved)
            : { ...VIEWMODE_DEFAULTS };
    } catch (_) {
        return { ...VIEWMODE_DEFAULTS };
    }
})();

/**
 * View mode used when the user has drilled into an album/artist/genre
 * and is viewing the resulting song list.
 */
let drillViewMode = localStorage.getItem("felicity_drillViewMode") || "list";

/**
 * Song targeted by the most recently opened context menu.
 *
 * @type {{ song: object, ctx: object[] }|null}
 */
let ctxSong = null;

/**
 * The bar heights used by the waveform canvas, regenerated each time a new
 * song starts. Each value is between 0.0 and 1.0 and represents the relative
 * height of one bar in the visualization.
 *
 * @type {number[]}
 */
let waveformBars = [];
