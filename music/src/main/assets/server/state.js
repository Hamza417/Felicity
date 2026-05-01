"use strict";

/**
 * state.js — Felicity Web Player
 *
 * All shared mutable state and cached DOM references used across every other module.
 * Must be loaded immediately after helpers.js.
 */

/* DOM references — player pane */
const audioEl        = el("audioEl");
const playerPane     = el("playerPane");
const playerCloseBtn = el("playerCloseBtn");
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

/* DOM references — screens */
const screenDashboard = el("screenDashboard");
const screenLibrary   = el("screenLibrary");
const dashboardBody   = el("dashboardBody");
const contentBody     = el("contentBody");

/* DOM references — library header */
const homeBtn        = el("homeBtn");
const searchInput    = el("searchInput");
const itemCount      = el("itemCount");
const sectionTitle   = el("sectionTitle");
const backBtn        = el("backBtn");
const backLabel      = el("backLabel");
const viewToggle     = el("viewToggle");
const viewToggleIcon = el("viewToggleIcon");

/* DOM references — theme */
const themeToggle = el("themeToggle");
const themeIcon   = el("themeIcon");

/* DOM references — floating now-playing FAB */
const playerFab = el("playerFab");

/* DOM references — context menu */
const ctxMenu     = el("ctxMenu");
const ctxPlay     = el("ctxPlay");
const ctxAddQueue = el("ctxAddQueue");
const ctxDelete   = el("ctxDelete");

/* ==================== App State ==================== */

/**
 * Per-section data cache so we don't re-fetch the same data on every tab switch.
 * @type {{ songs: object[]|null, albums: object[]|null, artists: object[]|null, genres: object[]|null }}
 */
const cache = { songs: null, albums: null, artists: null, genres: null };

/** Currently active section — "dashboard", "songs", "albums", "artists", or "genres". */
let section = "dashboard";

/**
 * Active drill-down context (e.g. all songs in an album), or null at the top level.
 * @type {{ type: string, name: string, items: object[] }|null}
 */
let drillItem = null;

/** Current value of the search input field. */
let searchQuery = "";

/** Playback queue — the array of songs the user is navigating with prev/next. */
let queue    = [];

/** Index of the currently playing song within {@link queue}. */
let queueIdx = -1;

/** Database ID of the currently playing song, or null when nothing is playing. */
let nowId = null;

/** True while the user is actively dragging the seek slider. */
let seeking = false;

/**
 * Per-section view mode — "list" or "grid". Albums default to grid because
 * album art looks much better that way.
 */
const VIEWMODE_DEFAULTS = { songs: "list", albums: "grid", artists: "list", genres: "list" };
const viewMode = (() => {
    try {
        const saved = JSON.parse(localStorage.getItem("felicity_viewMode") || "null");
        return (saved && typeof saved === "object")
            ? Object.assign({ ...VIEWMODE_DEFAULTS }, saved)
            : { ...VIEWMODE_DEFAULTS };
    } catch (_) { return { ...VIEWMODE_DEFAULTS }; }
})();

/** View mode for drill-down song lists (e.g. songs inside an album). */
let drillViewMode = localStorage.getItem("felicity_drillViewMode") || "list";

/**
 * Saves the scroll position of each section so when you drill into an album
 * and come back, you land right where you left off — not at the very top.
 * @type {{ [section: string]: number }}
 */
const scrollState = {};

/**
 * Song currently targeted by the context menu, or null when the menu is closed.
 * @type {{ song: object, ctx: object[] }|null}
 */
let ctxSong = null;

/**
 * Waveform bar heights for the canvas, one value (0.0–1.0) per bar.
 * Regenerated each time a new song starts — seeded from the song ID so
 * the same song always looks the same.
 * @type {number[]}
 */
let waveformBars = [];
