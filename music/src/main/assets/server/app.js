"use strict";

/**
 * app.js — Felicity Web Player
 *
 * Navigation logic: switching between the dashboard and the library screens,
 * drill-down into albums/artists/genres, the back button, live search
 * filtering, and the view-mode toggle. This file must be loaded last.
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
 * Switches to the dashboard home screen and hides the library screen.
 * Also eagerly kicks off loading all sections so the stats chips and
 * carousels have data to show right away.
 */
async function goDashboard() {
    section   = "dashboard";
    drillItem = null;

    screenDashboard.classList.add("active");
    screenDashboard.classList.remove("hidden");
    screenLibrary.classList.remove("active");
    screenLibrary.classList.add("hidden");


    /* Kick off loading all library data so the dashboard is fully populated. */
    await Promise.all([
        ensureLoaded("songs"),
        ensureLoaded("albums"),
        ensureLoaded("artists"),
        ensureLoaded("genres"),
    ]);

    renderDashboard(true);
}

/**
 * Updates the view-toggle button to show the icon for the OPPOSITE of
 * the current mode — because the icon shows what you'll switch TO.
 */
function updateViewToggle() {
    const mode = drillItem ? drillViewMode : (viewMode[section] || "list");
    viewToggleIcon.textContent = mode === "list" ? "grid_view" : "view_list";
    viewToggle.title = mode === "list" ? "Switch to grid view" : "Switch to list view";
}

/**
 * Fetches section data from the server if it hasn't been loaded yet.
 * Results are cached in the shared cache object to avoid re-fetching.
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
 * Navigates to a library section (songs, albums, artists, or genres).
 * Shows the library screen, hides the dashboard, and loads the data.
 *
 * @param {"songs"|"albums"|"artists"|"genres"} sec  Section to activate.
 */
async function goSection(sec) {
    section     = sec;
    drillItem   = null;
    searchQuery = "";
    searchInput.value = "";
    backBtn.classList.add("hidden");
    sectionTitle.textContent   = SECTION_LABELS[sec] || sec;
    searchInput.placeholder    = SECTION_PLACEHOLDERS[sec] || "Search…";

    /* Show the library screen, hide the dashboard. */
    screenLibrary.classList.add("active");
    screenLibrary.classList.remove("hidden");
    screenDashboard.classList.remove("active");
    screenDashboard.classList.add("hidden");

    updateViewToggle();

    contentBody.classList.add("fading");
    await ensureLoaded(sec);
    setTimeout(() => {
        renderContent(true);
        contentBody.classList.remove("fading");
    }, 120);
}

/**
 * Drills into an album, artist, or genre and shows a filtered song list
 * inside the library screen.
 *
 * @param {"albums"|"artists"|"genres"} type  Category type.
 * @param {string}                      name  Display name of the category.
 */
async function drillDown(type, name) {
    /* Save the current scroll position so goBack() can restore it later. */
    if (section !== "dashboard") {
        scrollState[section] = contentBody.scrollTop;
    }

    /* Make sure the library screen is visible for the drill-down view. */
    screenLibrary.classList.add("active");
    screenLibrary.classList.remove("hidden");
    screenDashboard.classList.remove("active");
    screenDashboard.classList.add("hidden");

    /* Update section so renderContent knows we're NOT on the dashboard anymore. */
    section = type;

    const url   = `/api/${type}/songs?name=${encodeURIComponent(name)}`;
    const items = await fetchJson(url);
    drillItem         = { type, name, items };
    searchQuery       = "";
    searchInput.value = "";
    backLabel.textContent    = name;
    backBtn.classList.remove("hidden");
    sectionTitle.textContent = name;
    searchInput.placeholder  = "Filter songs…";


    updateViewToggle();
    renderContent(true);
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
    updateViewToggle();
    renderContent(false);
    /* Put the user back where they were before they drilled in. */
    const saved = scrollState[section] || 0;
    requestAnimationFrame(() => { contentBody.scrollTop = saved; });
}

/* Home button in the library header does the same thing as the Home nav tab. */
homeBtn.addEventListener("click", goDashboard);

backBtn.addEventListener("click", goBack);

searchInput.addEventListener("input", () => {
    searchQuery = searchInput.value;
    renderContent();
});

/**
 * Toggles the view mode between "list" and "grid" for the current section,
 * saves the preference to localStorage, and re-renders the content area.
 */
viewToggle.addEventListener("click", () => {
    if (drillItem) {
        drillViewMode = drillViewMode === "list" ? "grid" : "list";
        localStorage.setItem("felicity_drillViewMode", drillViewMode);
    } else {
        viewMode[section] = viewMode[section] === "list" ? "grid" : "list";
        localStorage.setItem("felicity_viewMode", JSON.stringify(viewMode));
    }
    updateViewToggle();
    renderContent(true);
});

/* Start on the dashboard. */
goDashboard();
