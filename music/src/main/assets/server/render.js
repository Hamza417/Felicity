"use strict";

/**
 * render.js — Felicity Web Player
 *
 * All functions responsible for building and injecting DOM content into the
 * scrollable content area: song rows, album grid, artist list, genre list,
 * and the top-level dispatch function that picks the right renderer based on
 * the current section and search query.
 */

/**
 * Picks a deterministic accent color pair for a genre chip based on the
 * genre name's hash code.
 *
 * @param   {string} name  Genre name.
 * @returns {{ bg: string, fg: string }}  Background and foreground CSS colors.
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
    row.addEventListener("click",       ()  => playSong(song, ctx));
    row.addEventListener("contextmenu", e   => openCtxMenu(e, song, ctx));
    return row;
}

/**
 * Builds a song grid card element (alternative to the list row).
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
    card.addEventListener("click",       ()  => playSong(song, ctx));
    card.addEventListener("contextmenu", e   => openCtxMenu(e, song, ctx));
    return card;
}

/**
 * Applies a staggered entrance animation to the direct children of a
 * container. List items animate sequentially top-to-bottom; grid items
 * animate in diagonal waves from top-left to bottom-right, matching the
 * visual sweep seen in modern music apps.
 *
 * The animation itself is defined in content.css as {@code @keyframes itemAppear}.
 *
 * @param {HTMLElement} container  Parent whose children will be animated.
 * @param {boolean}     isGrid     Pass true for grid containers.
 * @param {number}      [cols=3]   Estimated column count used for diagonal
 *                                 stagger calculations when isGrid is true.
 */
function animateEntrance(container, isGrid, cols) {
    const items = Array.from(container.children);
    if (items.length === 0) return;

    const STEP     = isGrid ? 48 : 28;   /* ms between stagger steps */
    const MAX_STEP = isGrid ? 7  : 16;   /* cap to avoid very long waits */

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
 * @param {object[]} songs    Filtered songs to display.
 * @param {object[]} ctx      Full queue context for playback.
 * @param {string}   mode     "list" or "grid".
 * @param {boolean}  animate  True to play the entrance animation.
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
 * Renders an auto-fill album card grid into the content area.
 *
 * @param {object[]} albums   Albums to display.
 * @param {string}   mode     "grid" or "list".
 * @param {boolean}  animate  True to play the entrance animation.
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
 * Renders a list of artists with circular photo avatars, or a grid of cards.
 *
 * @param {object[]} artists  Artists to display.
 * @param {string}   mode     "list" or "grid".
 * @param {boolean}  animate  True to play the entrance animation.
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
        if (animate) animateEntrance(grid, true, Math.max(2, Math.floor((grid.offsetWidth || 500) / 170)));
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
 * Renders a list of genres with deterministically colored icon badges, or a grid.
 *
 * @param {object[]} genres  Genres to display.
 * @param {string}   mode    "list" or "grid".
 * @param {boolean}  animate True to play the entrance animation.
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
        if (animate) animateEntrance(grid, true, Math.max(2, Math.floor((grid.offsetWidth || 500) / 170)));
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
 * Top-level dispatcher — selects the appropriate renderer based on the active
 * section, drill-down state, current search query, and current view mode.
 *
 * @param {boolean} [animate=false]  When true, items will play their entrance
 *                                   animation. Pass true on navigation events;
 *                                   false for live search filtering.
 */
function renderContent(animate = false) {
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

