"use strict";

/**
 * app.js — Felicity Web Player
 *
 * Navigation logic (section switching, drill-down, back button, search) and
 * the application entry point.  This file must be loaded last.
 */

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
 * Fetches section data from the server if it has not been loaded yet.
 * Results are stored in the shared {@link cache} object.
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
 * Navigates to a top-level library section, resets drill-down/search state,
 * and re-renders the content area.
 *
 * @param {"songs"|"albums"|"artists"|"genres"} sec  Section to activate.
 */
async function goSection(sec) {
    section     = sec;
    drillItem   = null;
    searchQuery = "";
    searchInput.value       = "";
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
 * @param {"albums"|"artists"|"genres"} type  Category type.
 * @param {string}                      name  Display name of the category.
 */
async function drillDown(type, name) {
    const url   = `/api/${type}/songs?name=${encodeURIComponent(name)}`;
    const items = await fetchJson(url);
    drillItem         = { type, name, items };
    searchQuery       = "";
    searchInput.value = "";
    backLabel.textContent    = name;
    backBtn.classList.remove("hidden");
    sectionTitle.textContent = name;
    searchInput.placeholder  = "Filter songs…";
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

/* ─── Event wiring ───────────────────────────── */

navItems.forEach(btn => {
    btn.addEventListener("click", () => goSection(btn.dataset.sec));
});

backBtn.addEventListener("click", goBack);

searchInput.addEventListener("input", () => {
    searchQuery = searchInput.value;
    renderContent();
});

/* ─── Initialize ─────────────────────────────── */

goSection("songs");

