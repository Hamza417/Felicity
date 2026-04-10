"use strict";

/**
 * resize.js — Felicity Web Player
 *
 * Blender-style resizable pane dividers for the navigation pane (left/right)
 * and the player bar (top/bottom). Drag the vertical handle to adjust the nav
 * pane width; drag the horizontal handle to adjust the player bar height.
 *
 * Interactions are disabled at the mobile breakpoint (≤ 700 px) — handles
 * remain as static visual dividers only. Pane sizes are persisted in
 * {@link localStorage} and restored on every page load.
 *
 * @author Hamza417
 */

(function () {

    const NAV_KEY = "felicity_navW";
    const BAR_KEY = "felicity_barH";

    const NAV_MIN = 140;
    const NAV_MAX = 420;
    const BAR_MIN = 60;
    const BAR_MAX = 130;

    const MOBILE_BP = 700;

    /** Returns true when the viewport is inside the mobile breakpoint. */
    function isMobile() {
        return window.innerWidth <= MOBILE_BP;
    }

    /**
     * Reads a CSS custom property from the document root and parses it as a float.
     *
     * @param   {string} prop  CSS custom property name (e.g. "--nav-w").
     * @returns {number}
     */
    function getCssProp(prop) {
        return parseFloat(getComputedStyle(document.documentElement).getPropertyValue(prop)) || 0;
    }

    /**
     * Sets a CSS custom property on the document root as an inline style,
     * overriding any stylesheet-defined value.
     *
     * @param {string} prop   CSS custom property name.
     * @param {number} value  Numeric value in pixels.
     */
    function setCssProp(prop, value) {
        document.documentElement.style.setProperty(prop, value + "px");
    }

    /**
     * Restores saved pane sizes from localStorage and applies them as inline CSS
     * custom properties. Skipped when in the mobile breakpoint so that the CSS
     * media-query overrides remain effective.
     */
    function initSizes() {
        if (isMobile()) return;

        const navW = parseInt(localStorage.getItem(NAV_KEY), 10);
        if (navW >= NAV_MIN && navW <= NAV_MAX) {
            setCssProp("--nav-w", navW);
        }

        const barH = parseInt(localStorage.getItem(BAR_KEY), 10);
        if (barH >= BAR_MIN && barH <= BAR_MAX) {
            setCssProp("--bar-h", barH);
        }
    }

    /**
     * Removes any inline CSS size overrides so that the mobile media-query
     * variables (--nav-w: 58px etc.) take full effect.
     */
    function clearSizes() {
        document.documentElement.style.removeProperty("--nav-w");
        document.documentElement.style.removeProperty("--bar-h");
    }

    window.addEventListener("resize", () => {
        if (isMobile()) clearSizes();
        else initSizes();
    });

    /**
     * Wires a vertical (column) resize handle. Dragging left or right adjusts
     * the nav pane width via the --nav-w CSS variable.
     *
     * @param {HTMLElement}          handle      The draggable divider element.
     * @param {function(): number}   getCurrent  Returns the current value in px.
     * @param {function(number): void} apply     Applies a new value in px.
     * @param {string}               key         localStorage key for persistence.
     */
    function wireVertical(handle, getCurrent, apply, key) {
        let startX = 0, startVal = 0, active = false;

        handle.addEventListener("mousedown", e => {
            if (isMobile()) return;
            e.preventDefault();
            active   = true;
            startX   = e.clientX;
            startVal = getCurrent();
            handle.classList.add("dragging");
            document.body.style.cursor     = "col-resize";
            document.body.style.userSelect = "none";
        });

        document.addEventListener("mousemove", e => {
            if (!active) return;
            const next = Math.min(NAV_MAX, Math.max(NAV_MIN, startVal + (e.clientX - startX)));
            apply(next);
        });

        document.addEventListener("mouseup", () => {
            if (!active) return;
            active = false;
            handle.classList.remove("dragging");
            document.body.style.cursor     = "";
            document.body.style.userSelect = "";
            localStorage.setItem(key, Math.round(getCurrent()));
        });
    }

    /**
     * Wires a horizontal (row) resize handle. Dragging up or down adjusts the
     * player bar height via the --bar-h CSS variable.
     *
     * @param {HTMLElement}          handle      The draggable divider element.
     * @param {function(): number}   getCurrent  Returns the current value in px.
     * @param {function(number): void} apply     Applies a new value in px.
     * @param {string}               key         localStorage key for persistence.
     */
    function wireHorizontal(handle, getCurrent, apply, key) {
        let startY = 0, startVal = 0, active = false;

        handle.addEventListener("mousedown", e => {
            if (isMobile()) return;
            e.preventDefault();
            active   = true;
            startY   = e.clientY;
            startVal = getCurrent();
            handle.classList.add("dragging");
            document.body.style.cursor     = "row-resize";
            document.body.style.userSelect = "none";
        });

        document.addEventListener("mousemove", e => {
            if (!active) return;
            /* Dragging upward (negative delta) increases the bar height. */
            const next = Math.min(BAR_MAX, Math.max(BAR_MIN, startVal - (e.clientY - startY)));
            apply(next);
        });

        document.addEventListener("mouseup", () => {
            if (!active) return;
            active = false;
            handle.classList.remove("dragging");
            document.body.style.cursor     = "";
            document.body.style.userSelect = "";
            localStorage.setItem(key, Math.round(getCurrent()));
        });
    }

    initSizes();

    const navResizer = document.getElementById("navResizer");
    const barResizer = document.getElementById("barResizer");

    if (navResizer) {
        wireVertical(
            navResizer,
            () => getCssProp("--nav-w") || 220,
            w  => setCssProp("--nav-w", w),
            NAV_KEY
        );
    }

    if (barResizer) {
        wireHorizontal(
            barResizer,
            () => getCssProp("--bar-h") || 80,
            h  => setCssProp("--bar-h", h),
            BAR_KEY
        );
    }

})();

