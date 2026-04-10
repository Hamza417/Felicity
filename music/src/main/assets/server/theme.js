"use strict";

/**
 * theme.js — Felicity Web Player
 *
 * Light/dark theme toggling (persisted in localStorage) and the ambient
 * full-screen album-art background cross-fade logic.
 */

const THEME_KEY = "felicity_theme";

/** Which ambient layer div (A or B) is currently fully opaque. */
let ambientActive  = "A";
let lastAmbientUrl = "";

/**
 * Applies a theme to the document root element and persists the choice so it
 * survives page reloads.
 *
 * @param {"dark"|"light"} theme  The theme to activate.
 */
function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    themeIcon.textContent = theme === "dark" ? "dark_mode" : "light_mode";
    localStorage.setItem(THEME_KEY, theme);
}

/**
 * Cross-fades the full-screen ambient background to a new album artwork URL.
 * Alternates between two overlapping `div` layers so there is never a visible
 * blank gap during the transition.
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

/* Wire the theme toggle button. */
themeToggle.addEventListener("click", () => {
    const cur = document.documentElement.getAttribute("data-theme") || "dark";
    applyTheme(cur === "dark" ? "light" : "dark");
});

/* Initialize from localStorage (the inline <script> in <head> already sets
 * the attribute, but we still need to update the icon and sync the button). */
applyTheme(localStorage.getItem(THEME_KEY) || "dark");

