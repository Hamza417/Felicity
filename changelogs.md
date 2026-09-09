#### User Interface

- Added two waveforms styles in the **Player Screens**.
    - A scrolling mode waveform that scrolls from right to left as the song plays.
    - A static waveform that shows the entire waveform of the song and highlights the current
      playback position.
- Added **Reduce Animations** toggle in the **Accessibility Preferences** panel.

#### Bug Fixes

- Fixed some waveform optics deformity issues.
- Fixed _ForegroundServiceDidNotStartInTimeException_ crash in the player service.
- Fixed non-monotonic behavior of the waveform causing the waveform to jump back and forth while the
  song is playing.
- Fixed inconsistent global favorite button state in the **Player Screens** when the song is added
  or removed from favorites.
- Fixed various issues with scanner progress notification.
- Fixed extra divider in song menu when the song has no metadata.
- Fixed scroll view padding state loss in **Dashboard** causing the miniplayer to overlap the
  content.

#### Improvements

- Tapping artist and album names in the **Player Screens** now opens the corresponding artist and
  album pages.

#### Changes

- Changed some button groups preferences to popup menus in **Behavior Preferences** panel to avoid
  localized text overflowing the layout boundary.
- Apply waveform preferences to the waveform in **Lyrics** panel.
