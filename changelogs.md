### Library

- Improved library builder to prevent HASH collisions on duplicate Audio files causing same songs to
  be rescanned multiple times.
- Added **Excluded Folders** support to exclude out a folder from the scans.
- Added new way to generate song identities improving the loading speed significantly.
    - Songs now loads 5-10x times faster depending on the device. In my tests ~1400 were fully
      processed in less than 5 seconds. Earlier the speed was over 50 seconds.

### Players

- Added **Line Lrc View** to show lyrics on the now playing screen itself.
- Added dedicated **Media Controls** wth better UI and motion feedbacks.

### User Interface

- Added **Word by Word** LRC support.
- Added **Album Artists** panel.
- Added song count in **Genres**.
- Added **Refresh Library** in **Library Preferences**.
- Added **Dividers** accessibility toggle to add divider lines in the whole app.
- Updated **Dashboard** interface with smaller buttons for ideal spacing.
- Added **Selections** panel to view currently selected songs.
- Added toggle to replace **Heart** icon with **Thumbs Up** icon for favorites.

### Bug Fixes

- Fixed song states not saving when the player is paused and user is changing songs.
- Fixed player starts playing when decoder is changed in the background.
- Fixed **Folders Hierarchy** list and UI issues.
- Fixed equalizer button on the volume knob not working.
- Fixed **Add to Queue** option not moving the positions after the last song leading to inconsistent
  playback states.
- Fixed play button tint issues in various panels.
- Fixed initial stutters in **Typeface** selection panel.
- Fixed a crash caused by starting scanner service in a wrong app state.

### Improvements

- Improved lyrics menu states based on lyrics availability.

### Changes

- Reduced title font size a bit for the whole app.

### Removed

- Removed weird hover animations when using in PC or cursor modes.
- Replaced the morphing play button with a simple play button.
- Removed file hasher to reduce I/O overhead and slow scan times.