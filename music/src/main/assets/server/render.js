"use strict";

/**
 * render.js — Felicity Web Player
 *
 * All functions responsible for building and injecting DOM content:
 * the dashboard home screen (stats, browse panels, album and song carousels),
 * song rows, album grids, artist lists, genre lists, and the top-level
 * dispatch that picks the right renderer for the current section.
 */

/* ==================== Dashboard ==================== */

/**
 * Renders the dashboard home screen using whatever data is already in the cache.
 * Shows library stats chips, four browse panel cards, a "Top Albums" carousel
 * (sorted by song count so the juiciest albums float to the top), and a
 * "Recently Added" carousel made from the last few songs in the library.
 *
 * @param {boolean} [animate=false]  Play entrance animations on the panel cards.
 */
function renderDashboard(animate = false) {
    const songs   = cache.songs   || [];
    const albums  = cache.albums  || [];
    const artists = cache.artists || [];
    const genres  = cache.genres  || [];

    const topAlbums    = [...albums].sort((a, b) => (b.songCount || 0) - (a.songCount || 0)).slice(0, 20);
    const recentSongs  = songs.slice(-16).reverse();

    const dash = document.createElement("div");
    dash.className = "dashboard";

    /* Stats chips row */
    const statsRow = document.createElement("div");
    statsRow.className = "dash-stats";
    [
        { icon: "music_note",  label: `${songs.length.toLocaleString()} songs`   },
        { icon: "album",       label: `${albums.length.toLocaleString()} albums`  },
        { icon: "person",      label: `${artists.length.toLocaleString()} artists`},
        { icon: "piano",       label: `${genres.length.toLocaleString()} genres`  },
    ].forEach(({ icon, label }) => {
        const chip = document.createElement("div");
        chip.className = "dash-stat-chip";
        chip.innerHTML = `<span class="material-icons-round">${icon}</span><span>${label}</span>`;
        statsRow.appendChild(chip);
    });
    dash.appendChild(statsRow);

    /* Browse panel cards */
    const panelsSection = document.createElement("div");
    panelsSection.className = "dash-section";
    panelsSection.innerHTML = `<span class="dash-section-title">Browse</span>`;
    const panelsGrid = document.createElement("div");
    panelsGrid.className = "dash-panels";
    [
        { sec: "songs",   icon: "music_note", label: "Songs"   },
        { sec: "albums",  icon: "album",      label: "Albums"  },
        { sec: "artists", icon: "person",     label: "Artists" },
        { sec: "genres",  icon: "piano",      label: "Genres"  },
    ].forEach(({ sec, icon, label }) => {
        const card = document.createElement("button");
        card.className = "panel-card";
        card.innerHTML = `
            <div class="panel-icon"><span class="material-icons-round">${icon}</span></div>
            <span class="panel-label">${label}</span>`;
        card.addEventListener("click", () => goSection(sec));
        panelsGrid.appendChild(card);
    });
    panelsSection.appendChild(panelsGrid);
    dash.appendChild(panelsSection);

    /* Top Albums carousel */
    if (topAlbums.length > 0) {
        const albumSection = document.createElement("div");
        albumSection.className = "dash-section";
        albumSection.innerHTML = `<span class="dash-section-title">Top Albums</span>`;
        const carousel = document.createElement("div");
        carousel.className = "dash-carousel";
        topAlbums.forEach(album => {
            const card = document.createElement("div");
            card.className = "carousel-album-card";
            card.innerHTML = `
                <img class="carousel-album-art"
                     src="/api/songs/${album.coverSongId}/art"
                     loading="lazy" alt=""
                     onerror="this.style.opacity='0'">
                <div class="carousel-album-name">${esc(album.name)}</div>
                <div class="carousel-album-sub">${esc(album.artist || "")} · ${plural(album.songCount, "song")}</div>`;
            card.addEventListener("click", () => drillDown("albums", album.name));
            carousel.appendChild(card);
        });
        albumSection.appendChild(carousel);
        dash.appendChild(albumSection);
    }

    /* Recently Added carousel */
    if (recentSongs.length > 0) {
        const recentSection = document.createElement("div");
        recentSection.className = "dash-section";
        recentSection.innerHTML = `<span class="dash-section-title">Recently Added</span>`;
        const carousel = document.createElement("div");
        carousel.className = "dash-carousel";
        recentSongs.forEach(song => {
            const card = document.createElement("div");
            card.className = "carousel-song-card";
            card.innerHTML = `
                <img class="carousel-song-art"
                     src="/api/songs/${song.id}/art"
                     loading="lazy" alt=""
                     onerror="this.style.opacity='0'">
                <div class="carousel-song-name">${esc(song.title || song.name)}</div>
                <div class="carousel-song-sub">${esc(song.artist || "Unknown")}</div>`;
            card.addEventListener("click", () => playSong(song, songs));
            carousel.appendChild(card);
        });
        recentSection.appendChild(carousel);
        dash.appendChild(recentSection);
    }

    dashboardBody.innerHTML = "";
    dashboardBody.appendChild(dash);

    if (animate) animateEntrance(panelsGrid, true, 4);
}

/* ==================== Genre Color ==================== */

/**
 * Picks a deterministic accent color pair for a genre chip based on the
 * genre name's hash code. Same name always gets the same color — very reliable.
 *
 * @param   {string} name  Genre name.
 * @returns {{ bg: string, fg: string }}
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

/* ==================== Song Renderers ==================== */

/**
 * Builds a single song list row element with click and context-menu handlers.
 *
 * @param   {object}   song  Song data object from the API.
 * @param   {object[]} ctx   Full queue context for playback navigation.
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
    row.addEventListener("click",       () => playSong(song, ctx));
    row.addEventListener("contextmenu", e  => openCtxMenu(e, song, ctx));
    return row;
}

/**
 * Builds a song grid card element.
 *
 * @param   {object}   song  Song data object from the API.
 * @param   {object[]} ctx   Full queue context.
 * @returns {HTMLDivElement}
 */
function makeSongCard(song, ctx) {
    const card = document.createElement("div");
    card.className = "song-card album-card" + (song.id === nowId ? " active" : "");
    card.innerHTML = `
        <img class="album-cover" src="/api/songs/${song.id}/art" loading="lazy" alt=""
             onerror="this.style.opacity='0'">
        <div class="album-info">
            <div class="album-name">${esc(song.title || song.name)}</div>
            <div class="album-meta">${esc(song.artist || "Unknown Artist")}</div>
        </div>`;
    card.addEventListener("click",       () => playSong(song, ctx));
    card.addEventListener("contextmenu", e  => openCtxMenu(e, song, ctx));
    return card;
}

/**
 * Animates list items or grid cards with a staggered entrance effect so they
 * feel like they're sliding in one by one rather than all at once.
 *
 * @param {HTMLElement} container  Parent whose children will be animated.
 * @param {boolean}     isGrid     Pass true for grid containers.
 * @param {number}      [cols=3]   Estimated column count for diagonal stagger.
 */
function animateEntrance(container, isGrid, cols) {
    const items = Array.from(container.children);
    if (items.length === 0) return;

    const STEP     = isGrid ? 48 : 28;
    const MAX_STEP = isGrid ? 7  : 16;

    if (isGrid) {
        const estimatedCols = cols || Math.max(2, Math.floor((container.offsetWidth || 600) / 210));
        items.forEach((item, i) => {
            const row  = Math.floor(i / estimatedCols);
            const col  = i % estimatedCols;
            const diag = Math.min(row + col, MAX_STEP);
            item.style.animation = `itemAppear 280ms var(--ease) ${diag * STEP}ms backwards`;
            item.addEventListener("animationend", () => { item.style.animation = ""; }, { once: true });
        });
    } else {
        items.forEach((item, i) => {
            const step = Math.min(i, MAX_STEP);
            item.style.animation = `itemAppear 280ms var(--ease) ${step * STEP}ms backwards`;
            item.addEventListener("animationend", () => { item.style.animation = ""; }, { once: true });
        });
    }
}

/**
 * Renders a flat song list into the content area.
 *
 * @param {object[]} songs    Songs to display.
 * @param {object[]} ctx      Full queue context for playback.
 * @param {string}   mode     "list" or "grid".
 * @param {boolean}  animate  Whether to play entrance animations.
 */
function renderSongList(songs, ctx, mode = "list", animate = false) {
    contentBody.innerHTML = "";
    if (songs.length === 0) {
        contentBody.innerHTML =
            `<div class="empty-state"><span class="material-icons-round empty-icon">music_off</span>
             <p class="empty-title">No songs found</p></div>`;
        return;
    }
    if (mode === "grid") {
        const grid = document.createElement("div");
        grid.className = "album-grid";
        songs.forEach(song => grid.appendChild(makeSongCard(song, ctx)));
        contentBody.appendChild(grid);
        if (animate) animateEntrance(grid, true);
    } else {
        const frag = document.createDocumentFragment();
        songs.forEach(song => frag.appendChild(makeSongRow(song, ctx)));
        contentBody.appendChild(frag);
        if (animate) animateEntrance(contentBody, false);
    }
}

/**
 * Renders an album card grid (or list) into the content area.
 *
 * @param {object[]} albums  Albums to display.
 * @param {string}   mode    "grid" or "list".
 * @param {boolean}  animate Whether to play entrance animations.
 */
function renderAlbumGrid(albums, mode = "grid", animate = false) {
    contentBody.innerHTML = "";
    if (albums.length === 0) {
        contentBody.innerHTML =
            `<div class="empty-state"><span class="material-icons-round empty-icon">album</span>
             <p class="empty-title">No albums found</p></div>`;
        return;
    }
    if (mode === "list") {
        const frag = document.createDocumentFragment();
        albums.forEach(album => {
            const row = document.createElement("div");
            row.className = "album-row";
            row.innerHTML = `
                <img class="album-row-art" src="/api/songs/${album.coverSongId}/art"
                     loading="lazy" alt="" onerror="this.style.opacity='0'">
                <div class="row-info">
                    <div class="row-name">${esc(album.name)}</div>
                    <div class="row-sub">${esc(album.artist || "")} · ${plural(album.songCount, "song")}</div>
                </div>
                <span class="material-icons-round chevron">chevron_right</span>`;
            row.addEventListener("click", () => drillDown("albums", album.name));
            frag.appendChild(row);
        });
        contentBody.appendChild(frag);
        if (animate) animateEntrance(contentBody, false);
    } else {
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
        if (animate) animateEntrance(grid, true);
    }
}

/**
 * Renders a list or grid of artists into the content area.
 *
 * @param {object[]} artists  Artists to display.
 * @param {string}   mode     "list" or "grid".
 * @param {boolean}  animate  Whether to play entrance animations.
 */
function renderArtistList(artists, mode = "list", animate = false) {
    contentBody.innerHTML = "";
    if (artists.length === 0) {
        contentBody.innerHTML =
            `<div class="empty-state"><span class="material-icons-round empty-icon">person_off</span>
             <p class="empty-title">No artists found</p></div>`;
        return;
    }
    if (mode === "grid") {
        const grid = document.createElement("div");
        grid.className = "artist-grid";
        artists.forEach(artist => {
            const card = document.createElement("div");
            card.className = "artist-card";
            card.innerHTML = `
                <img class="artist-card-img" src="/api/songs/${artist.coverSongId}/art"
                     loading="lazy" alt="" onerror="this.style.opacity='0'">
                <div class="artist-card-name">${esc(artist.name)}</div>
                <div class="artist-card-sub">${plural(artist.songCount, "song")}</div>`;
            card.addEventListener("click", () => drillDown("artists", artist.name));
            grid.appendChild(card);
        });
        contentBody.appendChild(grid);
        if (animate) animateEntrance(grid, true);
    } else {
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
        if (animate) animateEntrance(contentBody, false);
    }
}

/**
 * Renders a list or grid of genres with their color-coded icon badges.
 *
 * @param {object[]} genres  Genres to display.
 * @param {string}   mode    "list" or "grid".
 * @param {boolean}  animate Whether to play entrance animations.
 */
function renderGenreList(genres, mode = "list", animate = false) {
    contentBody.innerHTML = "";
    if (genres.length === 0) {
        contentBody.innerHTML =
            `<div class="empty-state"><span class="material-icons-round empty-icon">music_off</span>
             <p class="empty-title">No genres found</p></div>`;
        return;
    }
    if (mode === "grid") {
        const grid = document.createElement("div");
        grid.className = "genre-grid";
        genres.forEach(genre => {
            const { bg, fg } = genreColor(genre.name);
            const card = document.createElement("div");
            card.className = "genre-card";
            card.style.borderColor = `${fg}44`;
            card.innerHTML = `
                <div class="genre-card-icon-wrap" style="background:${bg}; border:1px solid ${fg}55; border-radius:var(--r-md);">
                    <span class="material-icons-round" style="color:${fg}">music_note</span>
                </div>
                <div class="genre-card-name">${esc(genre.name || "Unknown")}</div>
                <div class="genre-card-sub">${plural(genre.songCount, "song")}</div>`;
            card.addEventListener("click", () => drillDown("genres", genre.name));
            grid.appendChild(card);
        });
        contentBody.appendChild(grid);
        if (animate) animateEntrance(grid, true);
    } else {
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
        if (animate) animateEntrance(contentBody, false);
    }
}

/**
 * Top-level dispatcher — renders the active section (or drill-down) into the
 * content area, and re-renders the dashboard if it's the active screen.
 *
 * @param {boolean} [animate=false]  Play entrance animations on navigation events.
 */
function renderContent(animate = false) {
    if (section === "dashboard") {
        renderDashboard(animate);
        return;
    }

    const q    = searchQuery.toLowerCase();
    const mode = drillItem ? drillViewMode : (viewMode[section] || "list");

    if (drillItem) {
        let items = drillItem.items;
        if (q) items = items.filter(s =>
            (s.title  || s.name || "").toLowerCase().includes(q) ||
            (s.artist || "").toLowerCase().includes(q)
        );
        itemCount.textContent = plural(items.length, "song");
        renderSongList(items, drillItem.items, mode, animate);
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
            renderSongList(items, items, mode, animate);
            break;
        }
        case "albums": {
            let items = cache.albums || [];
            if (q) items = items.filter(a =>
                (a.name   || "").toLowerCase().includes(q) ||
                (a.artist || "").toLowerCase().includes(q)
            );
            itemCount.textContent = plural(items.length, "album");
            renderAlbumGrid(items, mode, animate);
            break;
        }
        case "artists": {
            let items = cache.artists || [];
            if (q) items = items.filter(a => (a.name || "").toLowerCase().includes(q));
            itemCount.textContent = plural(items.length, "artist");
            renderArtistList(items, mode, animate);
            break;
        }
        case "genres": {
            let items = cache.genres || [];
            if (q) items = items.filter(g => (g.name || "").toLowerCase().includes(q));
            itemCount.textContent = plural(items.length, "genre");
            renderGenreList(items, mode, animate);
            break;
        }
    }
}

