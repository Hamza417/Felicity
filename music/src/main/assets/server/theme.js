"use strict";

/**
 * theme.js — Felicity Web Player
 *
 * Light/dark theme toggling (persisted in localStorage) and the ambient
 * full-screen album-art background cross-fade logic.
 */

const THEME_KEY = "felicity_theme";

/** Tracks which ambient layer is currently showing (A or B). */
let ambientActive  = "A";
let lastAmbientUrl = "";

/**
 * Applies a theme to the document root and persists the choice in localStorage
 * so the next page load starts with the right colors instead of flashing.
 *
 * @param {"dark"|"light"} theme  The theme to activate.
 */
function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    themeIcon.textContent = theme === "dark" ? "dark_mode" : "light_mode";
    localStorage.setItem(THEME_KEY, theme);
    /* Waveform uses CSS colors, so redraw it whenever the theme changes. */
    if (waveformBars.length > 0) {
        const progress = audioEl.duration ? audioEl.currentTime / audioEl.duration : 0;
        drawWaveform(progress);
    }
}

/**
 * Cross-fades the ambient background to a new album artwork URL by alternating
 * between two overlapping div layers. This avoids any ugly blank-background flash
 * during the transition — it's all smooth and pretty.
 *
 * @param {string} artUrl  URL of the album artwork image to display.
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

themeToggle.addEventListener("click", () => {
    const cur = document.documentElement.getAttribute("data-theme") || "dark";
    applyTheme(cur === "dark" ? "light" : "dark");
});

/* Sync the icon with whatever theme the inline <head> script already set. */
applyTheme(localStorage.getItem(THEME_KEY) || "dark");
