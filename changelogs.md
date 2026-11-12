#### Library

- Added affinity based song recommendation system.
    - This significantly improves the recommendation accuracy by using a probabilistic next song
      determination based on the user's local activity and this lays the base of the app's
      recommendation system and this will be used to add features like **Dynamic Playlists** and
      improved suggestions everywhere in the app.

#### DSP

- Fixed sluggish visualizer updates especially while using the **AudioTrack** sink.
- Fixed scalar calculation instead of hardware accelerated NEON calculation for the FFT processing.
  #171
    - This should save battery and make visualizer even snappier and reacts more accurately to
      snares and basses.

#### User Interface

- Added best matches count and indicator in the lyrics panel to indicate the best matched lyrics for
  the song.
- Horizontal menu strip for **Equalizer** instead of popup menu.
- Added **Sleep Timer**.
    - Added smooth rolling timer picker for timer setup.
    - Added timer button in the **Dashboard** home.
    - Added final action to pause or quit the app.
    - Supports three modes: **Preset**, **Manual**, and **Till This Song**.

#### Bug Fixes

- Fixed unnecessary animations in **Visualizer Menu**.
- Fixed inconsistent presets loading in the **Equalizer** and **DSP**.
- Fixed elevation glitch in shared popup menus.

#### Improvements

- Use duration and other params while matching lyrics to improve the lyrics auto-download accuracy.

#### Changes

- Changed the default home to **Dashboard**. from **Simple**.

#### Removed

- Removed popup menu from the **Equalizer**.