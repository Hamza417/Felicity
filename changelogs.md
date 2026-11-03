#### Equalizer

- Added option to import PEQ presets. #118
  - Supports presets from AutoEQ and other similar tools.
- Fixed weird preset biquad output matches caused by locale mismatch in the PEQ parser causing wrong sound adjustments. #118
- Fixed uneven spacing issues.
- Fixed knobs losing the touch focus on rotation.
- Added self-compensating graph for EQ/PEQ preset previews.
  - It is a nice looking real-time graph preview.
- Color support for PEQ bands.
- Auto save the imported presets.
- Resetting the PEQ should remove the bands as well.
- Eager presets matching in the EQ to aggressively match the changes against all existing presets.

#### DSP

- Added option to select FFT resolution in the **Audio** preferences.
  - Lower resolution saves battery and renders visualizer at a faster rate.

#### Lyrics

- Added new way to highlight lyrics words and lines.
  - Added a moving highlighting box with physics based animations imitating a magnifying glass effect.
- Optimized lyrics rendering to remove constant caching and dropping static layouts, significantly improving the rendering performance.
- Removed the clipping behavior from the rendering to let the text overflow the boundary to create a proper fluid effect.
- Romanization and adlibs support in the **LRC Parser**.
- Removed cancellation of the edge blur when interactions occur to reduce motion and increase behavioral stability.
- Improved lyrics scrolling.

#### User Interface

- Add album and artist menu in **Search**.
- Added grid layouts for all data types in the **Search**.
  - Should not show grid for songs and list layout for everything else.
- Updated the **Queue Management** dialog and added more options for efficient queue handling.
- Added option to select the zone for album art color styles.
  - You can not select from **Retro**, **Pastel**, and **Chic** palette styles to suit your color preferences.
- Added slider to adjust visualizer latency.
- Added new **Block** visualizer skin.
- Added **Spectrum Color** hits for the visualizer to highlight peak band hits.
- Added **Mixes** to enable dynamic playlists and suggestions in the **Dashboard**.
  - Added voronoi style playlist card generator.

#### Bug Fixes

- Fixed random OOM crash on faulty bitmaps processing stuck in the pipeline.
- Fixed scanner notifications cancelling out the media control notification.
  - Both notifications should show simultaneously.
- Fixed a potential deadlock in the DB scanner threads.
- Fixed album arts not shared between the list and menu in **Search**.
- Fixed a random negative padding issue causing the **Lyrics** to crash.
- Fixed empty **Shuffle** button behavior in some panels.
- Fixed wrong album art shown in the **Mini Player** sometimes.
- Fixed multiple view holders end up having same item ids causing the app to crash unexpectedly. #165
- Fixed hidden margin in the **Equalizer**.
- Fixed seekbar labels clipping on sliding.

#### Improvements

- Added last used time stamp on queue entries to efficiently find the oldest queue for priority sorting when replacing for **Queue Management**.
- Using spanned layout manager in the **Tiled Home** to enable more natural tile layouts.
- Palette generator has been improved to generate soothing colors in the dark mode.

#### Changes

- Default lyrics should be deleted too when a song is deleted.
- Decouple outline and highlight toggle in **Accessibility** preferences.

#### Removed

- Removed **Particles** and **Caps** toggle from the **Visualizer Menu**.
- Removed the **Recommended** section in favor of the new **Mixes** playlist generator in the **Dashboard**.