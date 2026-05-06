### DSP

- Added **Replay Gain** support for all four gain modes.

### Library

- Added option to manually scan the library.
- Added option to re-index the whole library.
- Added toggle to run the scanner on every app resume.

### User Interface

- Revamped the whole **Tiled Home** design.
- Added porper home panel descriptions in the popup.
- Added **Playback Info** dialog for per song playback information. #44
- Added **Replay** count in the **Playback Info** database and dialog.
- Updated **Shuffle** dialog UI.
    - Added **No Reshuffle** toggle.
- Added **Global Shuffle** toggle and button.
- Added **Shuffle** button in **player screens**.
- Added **Favorite** button in the notifications.
- Added **Replay Gain** knob in the **Equalizer** screen.
- Added **Replay Gain** info in the **Audio Information** dialog.
- Added the fields to edit **Replay Gain** values in the **Metadata Editor** panel.
- Added **Global List Style** dialog in all panels to centralize the list styles.
- Added **Most Skipped** panel to view the most skipped songs in the library.

### Bug Fixes

- Fixed **Skip** and **Play** count not recording properly. #44
- Fixed **Media Playback Manager** not notifying the media states properly across panels.
- Fix a crash caused by no song data in **Songs Menu**.
- Fixed a crash caused by a memory leak in **Tag/Metadata Processor** in the app.
- Fixed the cursor drawable color for all input fields in the app.
- Fixed song info not updating in player screens when its metadata is changed.
- Fixed media state icons are not visible in grid mode.
- Fixed inconsistent notification behavior in the app.
- Fixed scanner jobs not cancelling itself properly.
- Fixed various nav bar padding issues in the app.
- Fixed invalid white color in line lrc view.
- Dedicated adapter for selections to prevent it from inheriting the **Songs** list styles.

### Improvements

- Improved **Metadata Editor** interface.
- Removed the drag icon from the **Playing Queue** to reduce one object overhead.
- Improved the icon rendering in the **Media Aware** layouts for better icon rendering.

### Removed

- Removed shuffle algorithms to be replaced with smart bucketed algorithm.
- Removed all menus from all panels.
- Removed **Sort Order** chips from all panels.