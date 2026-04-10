"use strict";

/**
 * state.js — Felicity Web Player
 *
 * All shared mutable state and cached DOM references used across every other
 * module. This file must be loaded immediately after helpers.js.
 */

/* ─── DOM references ─────────────────────────── */

const audioEl      = el("audioEl");
const ambientA     = el("ambientA");
const ambientB     = el("ambientB");
const contentBody  = el("contentBody");
const searchInput  = el("searchInput");
const itemCount    = el("itemCount");
const sectionTitle = el("sectionTitle");
const backBtn      = el("backBtn");
const backLabel    = el("backLabel");
const nowArt       = el("nowArt");
const nowTitle     = el("nowTitle");
const nowArtist    = el("nowArtist");
const seekBar      = el("seekBar");
const elapsed      = el("elapsed");
const remaining    = el("remaining");
const playBtn      = el("playBtn");
const playIcon     = el("playIcon");
const prevBtn      = el("prevBtn");
const nextBtn      = el("nextBtn");
const volBar       = el("volBar");
const volIcon      = el("volIcon");
const volPct       = el("volPct");
const themeToggle  = el("themeToggle");
const themeIcon    = el("themeIcon");
const ctxMenu      = el("ctxMenu");
const ctxPlay      = el("ctxPlay");
const ctxAddQueue  = el("ctxAddQueue");
const ctxDelete    = el("ctxDelete");
const navItems     = document.querySelectorAll(".nav-item");

/* ─── App state ──────────────────────────────── */

/**
 * Per-section data cache — avoids repeated API calls when switching tabs.
 *
 * @type {{ songs: object[]|null, albums: object[]|null, artists: object[]|null, genres: object[]|null }}
 */
const cache = { songs: null, albums: null, artists: null, genres: null };

/** Currently active top-level section key. */
let section = "songs";

/**
 * Drill-down context when the user has navigated into an album, artist, or
 * genre.  Null when at the top-level section view.
 *
 * @type {{ type: string, name: string, items: object[] }|null}
 */
let drillItem = null;

/** Live value of the search input (lower-cased). */
let searchQuery = "";

/** Current playback queue (array of song objects from the API). */
let queue    = [];

/** Index of the currently playing song within {@link queue}. */
let queueIdx = -1;

/** Database ID of the currently playing song, or null when idle. */
let nowId = null;

/** True while the user is dragging the seek slider. */
let seeking = false;

/**
 * Song targeted by the most recently opened context menu.
 *
 * @type {{ song: object, ctx: object[] }|null}
 */
let ctxSong = null;

