### Library

- Toggle to pause activity records. #74
- Option to clear all activity records. #74
- Cache downloaded artist images internally to avoid redownloading. #71

### User Interface

- Added **Bookmarks** support.
    - Added tap to add bookmarks in **Waveform** on player screen.
    - Added **Bookmarks** panel to view all songs with atleast one bookmark.
    - Added quick jump to bookmarked positions in the player screen.
    - Dedicated **Bookmarks** menus.
    - Added bookmark indicator in the **Waveform**.
- Add **monochrome** app icon. #70
- Added option to sort by **As Added** in playlist to show songs in order as they were added. #73

### Bug Fixes

- Fixed **EQ params** not loaded in first launches.
- Fixed weird filenames for metadata-less files. #84
- Fixed cover art not showing for some formats like WAV. #75
- Fixed audio quality badge showing incorrect quality for some formats. #77
- Fixed metadata extractor not parsing some fields like **NumTracks** etc. #77, #75
- Fixed **Audio Information** showing no for _Embedded Album Art_ field. #75
- Fixed external art covers are never queried due to SAF migration.
- Fixed multiple OOMs in database and playback managers causing the app to crash when library grew
  too big.
- Fixed a crash caused by **Server Service** not starting properly.

### Improvements

- Swipe down to hide **Mini Player** temporarily. #74

### Changes

- Disable visualizer and equalizer in Hi-Res mode.
- Changed the stats information to show first in stats panels like **Most Played** etc. #60
- Include sample rate to check audio quality for lossless formats. #77
- Large heap to create more memory buffer for burst library parsing in lower end devices.
