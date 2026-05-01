"use strict";

/**
 * theme.js — Felicity Web Player
 *
 * Two jobs:
 *   1. Fetch the current accent color and dark/light preference from the Android
 *      app via /api/theme and apply them as CSS custom properties so the web page
 *      looks identical to the app the user is actually running.
 *
 *   2. Wire the manual theme toggle button for when the user wants to flip
 *      between dark and light without touching the phone.
 */

const THEME_KEY = "felicity_theme";

/**
 * Applies a theme string to the document root and persists it in localStorage.
 *
 * @param {"dark"|"light"} theme  The theme to activate.
 */
function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    themeIcon.textContent = theme === "dark" ? "dark_mode" : "light_mode";
    localStorage.setItem(THEME_KEY, theme);
    /* Waveform bars use CSS color variables, so redraw when the theme flips. */
    if (waveformBars.length > 0) {
        drawWaveform(audioEl.duration ? audioEl.currentTime / audioEl.duration : 0);
    }
}

/**
 * Parses a "#RRGGBB" hex string and returns an {r, g, b} object.
 * Used to compute the alpha-blended accent variants.
 *
 * @param   {string} hex  A six-digit hex color like "#2980b9".
 * @returns {{ r: number, g: number, b: number }}
 */
function hexToRgb(hex) {
    const h = hex.replace("#", "");
    return {
        r: parseInt(h.slice(0, 2), 16),
        g: parseInt(h.slice(2, 4), 16),
        b: parseInt(h.slice(4, 6), 16),
    };
}

/**
 * Applies the app accent color to all the CSS custom properties that reference it.
 * This makes the whole web page match the accent the user picked in the Android app —
 * no more hardcoded blue for everyone.
 *
 * @param {string} primary    Primary accent hex, e.g. "#2980b9".
 * @param {string} secondary  Secondary / gradient-end hex.
 */
function applyAccentColor(primary, secondary) {
    const { r, g, b } = hexToRgb(primary);
    const root = document.documentElement;
    root.style.setProperty("--accent",           primary);
    root.style.setProperty("--accent-2",          secondary || primary);
    root.style.setProperty("--accent-gradient",   `linear-gradient(135deg, ${primary} 0%, ${secondary || primary} 100%)`);
    root.style.setProperty("--accent-glow",       `rgba(${r},${g},${b},0.38)`);
    root.style.setProperty("--accent-dim",        `rgba(${r},${g},${b},0.13)`);
    root.style.setProperty("--accent-border",     `rgba(${r},${g},${b},0.30)`);
    root.style.setProperty("--active-bg",         `rgba(${r},${g},${b},0.12)`);
    root.style.setProperty("--active-text",        primary);

    /* Also re-fill the volume slider since it uses --accent directly in its gradient. */
    setFill(volBar, +volBar.value);
    /* Redraw waveform so it picks up the new accent. */
    if (waveformBars.length > 0) {
        drawWaveform(audioEl.duration ? audioEl.currentTime / audioEl.duration : 0);
    }
}

/**
 * Fetches the current accent color and theme mode from the Android app, then
 * applies them to the page. Falls back to the CSS defaults if the request fails
 * (which can happen in dev / browser preview without the server running).
 */
async function loadAppTheme() {
    try {
        const data = await fetchJson("/api/theme");
        /* The app tells us which theme is active — override the saved localStorage value
         * so the web page always matches the phone, not a stale cached value. */
        applyTheme(data.isDark ? "dark" : "light");
        applyAccentColor(data.accent, data.accentSecondary);
    } catch (_) {
        /* Server not available — use whatever was stored locally, or dark by default. */
        applyTheme(localStorage.getItem(THEME_KEY) || "dark");
    }
}

/* Manual toggle for power users who want to flip themes from the browser. */
themeToggle.addEventListener("click", () => {
    const cur = document.documentElement.getAttribute("data-theme") || "dark";
    applyTheme(cur === "dark" ? "light" : "dark");
});

/* Apply the locally cached theme immediately (before the async fetch returns)
 * so there is zero flash of unstyled content. */
applyTheme(localStorage.getItem(THEME_KEY) || "dark");

/* Then fire the async load — it will silently update colors once the server responds. */
loadAppTheme();
