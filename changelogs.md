### Library

- Added artist separator to split artist having **;** in artists fields. #62
- Split artists with **&** only when it is surrounded by spaces. #62
- Match single artist exclusively to avoid matching artists containing the artist name. #62
- Split **Album Artists** with artist separator.

### User Interface

- Update **Purchase** panel interface for play builds.
- Added **Lock** to move **Speed** and **Pitch** knob simultaneously. #53
- Added **Composers** panel.
- Added **Composers Page** showing albums and songs of the composer.
- Implemented functions for all menus for all page panels.

### Bug Fixes

- Fixed proguard removing TagLib classes leading to missing metadata for whole library. #57
- Fixed invalid theme states on cold app launches.
- Fixed an OOM in restoring media states. #48
- Fixed song state ballooning on each playback state restore on cold app launches. #48
- Fixed wrong **Playing Queue** time updates.
- Fixed app showing raw bitrates and without any formatting.
- Fixed PCM pipeline info inconsistencies.
- Fixed non-terminating recursion while initializing the preferences leading to a frozen/blank app
  launch.
- Fixed first song of the queue is not added to the **Recently Played** list. #58
- Fixed artist song count in the **Artists** panel and **Search** panel. #62
- Fixed **Album Page** showing **Artists** instead of **Album Artists**. #62
- Fixed app header stays offscreen when list count has changed while header is in partially/hidden
  state.

### Improvements

- Improved **Knob** highlight color to be distinguishable from the background.
- Multiple songs to playlist dialog should only parcel hashes to avoid potential OOMs.

### Changes

- Changed **Most Played** parameter to include songs played at least two times. #58

### Removed

- PCM info dialog for Hi-Res mode.
    - Since audio processors are not supported by ExoPlayer yet, the dialog will always show random
      info on Hi-Res mode.

### Development

- 32-bit support dropped for store builds.
    - 32-bit users can still get 32-bit builds from the GitHub releases page.