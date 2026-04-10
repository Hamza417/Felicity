"use strict";

/**
 * helpers.js — Felicity Web Player
 *
 * Pure utility functions with no side effects and no DOM dependencies:
 * element lookup shorthand, time formatters, HTML escaping, slider fill
 * helper, JSON fetcher, and the toast notification factory.
 */

/**
 * Shorthand for {@link document.getElementById}.
 *
 * @param   {string} id  Element ID.
 * @returns {HTMLElement}
 */
const el = id => document.getElementById(id);

/**
 * Formats a duration given in milliseconds as a "m:ss" string.
 *
 * @param   {number} ms  Duration in milliseconds.
 * @returns {string}
 */
function fmtMs(ms) {
    const s   = Math.floor(Math.max(0, ms) / 1000);
    const min = Math.floor(s / 60);
    const sec = (s % 60).toString().padStart(2, "0");
    return `${min}:${sec}`;
}

/**
 * Formats a duration given in seconds as a "m:ss" string.
 *
 * @param   {number} s  Duration in seconds.
 * @returns {string}
 */
function fmtSec(s) { return fmtMs(s * 1000); }

/**
 * HTML-escapes a plain-text string for safe injection into innerHTML.
 *
 * @param   {string} t  Raw text, possibly containing special characters.
 * @returns {string}    Escaped HTML text.
 */
function esc(t) {
    const d = document.createElement("div");
    d.appendChild(document.createTextNode(t || ""));
    return d.innerHTML;
}

/**
 * Returns a pluralized count string, e.g. "1 song" or "3 songs".
 *
 * @param   {number} n  Count.
 * @param   {string} w  Singular base word.
 * @returns {string}
 */
function plural(n, w) { return `${n} ${w}${n !== 1 ? "s" : ""}`; }

/**
 * Updates the CSS custom property that drives the filled portion of a thick
 * thumbless slider.
 *
 * @param {HTMLInputElement} slider  The range input element to update.
 * @param {number}           pct    Fill percentage in the range 0–100.
 */
function setFill(slider, pct) {
    slider.style.setProperty("--fill", `${Math.min(100, Math.max(0, pct))}%`);
}

/**
 * Fetches a URL and returns the parsed JSON response body.
 *
 * @param   {string} url  Request URL.
 * @returns {Promise<any>}
 * @throws  {Error} If the HTTP response status is not OK.
 */
async function fetchJson(url) {
    const r = await fetch(url);
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    return r.json();
}

/**
 * Displays a brief floating toast notification above the player bar.
 *
 * @param {string} msg       Message to display.
 * @param {number} duration  How long to show the toast in milliseconds.
 */
function showToast(msg, duration = 2800) {
    document.querySelectorAll(".toast").forEach(t => t.remove());
    const t = document.createElement("div");
    t.className   = "toast";
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(() => {
        t.style.transition = "opacity 0.30s";
        t.style.opacity    = "0";
        setTimeout(() => t.remove(), 320);
    }, duration);
}

