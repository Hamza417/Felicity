### Library

- Migrated app to use SAF instead.
    - Since SAF is a major change, most of the efforts in the last week went into stabilizing the
      app over this. You will need to clear app data and start afresh to make sure you don't
      encounter weird problems.
- Added TagLib based metadata fetching for better support SAF replacing the JAudioTagger.
- Scan only when app is launched.

## DSP

- Added **Pitch** and **Speed** adjustments in **Equalizer**.

### User Interface

- Added toggle to enable/disable auto lyrics fetching.
- Added first audio widget.
- Added option to clear image caches in **Library Preferences**.
- Replaced toasts with Felicity snack-bars.
- Revamped **Lyrics** behavior and UI.
    - Removed text resizing from normal → current → normal causing a clunky scroll behavior.
    - Uses unified fonts and color based highlighting.
    - Much smoother scrolling and movement now.
    - Removed many static layouts and layout calculations to make it very fluid and lightweight
    - Removed a lot of unnecessary motions to prevent UI flashes that can induce discomfort.
    - Added album art in the **Lyrics** panel.
- Minor changes in **Equalizer** UI.
- Changed _No Album Art_ image to more app appropriate one.
- Updated **Dashboard** UI.
    - Added library stats chip in the header instead of currently playing song.
    - Added **Top Artists** and **Top Albums**.
    - Added **Server Status** chip.
- Added **Immersive Mode** toggle for some screens.

### Bug Fixes

- Fixed empty theme state on app state restored by third parties.
- Fixed M3U icon tint in **Playlists**.
- Fixed padding loss in **Dashboard** causing the miniplayer and ui overlap.
- Fixed a major issue when items are few in the list causing the spacing to be very weird and
  inconsistent.

### Removed

- Removed **Excluded Folders** preferences since you can manually pick folders to scan now with SAF.
- Removed corner radius from the **ArtFlow**.