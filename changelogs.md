#### DSP and Sinks

- Fixed delay while pausing the playback in the **AAudio** and **Oboe** sinks.

#### User Interface

- Added **Queue Management** to ask player to manage state before loading the new queue.
- Added a slider to adjust the **Mini Player** width in the landscape mode, useful if you do not
  want really wide **Mini Player** in landscape mode.
- Revamped volume dialog.
    - Slightly smaller volume knob.
    - Added tone knobs to adjust the treble and bass on the fly anywhere in the app.
- Added **Composer** and **Year** filter in the **Search** panel.
- Added **Shuffle** button in **Folders Hierarchy** to shuffle either the current folder or the
  current folder and all its subfolders.

#### Bug Fixes

- Fixed artist cache key collision causing incorrect artist images displayed in the **Artist Page
  **. (by @yossijaki)
- Fixed keyboard IMEs obscuring the buttons in the **Metadata Editor**. #155
- Fixed hiding keyboard also hides the current panel happening in **Search**, **Lyrics** and *
  *Metadata Editor**. #155
- Fixed albums getting sorted before adding to the queue. #158
- Fixed artist's songs getting sorted before adding to the queue. #158
- Fixed **Artists** button not working in **ArtFlow Home** panel.
- Fixed persistent scanning notification not being removed after the scan is completed.
- Fixed cancel button not working in the **App Label** dialog.
- Fixed the text overlapping with the drawn buttons in the list of the media aware layouts.
    - If you faced the text overlapping with the play or drag icons in the list of the media aware
      layouts, this fixes that issue.
- Fixed first line is never highlighted in the **Lyrics** panel.

#### Improvements

- Hide shuffle button if only one song is in the queue.
- Use camera optic deform to properly create a yaw bar rotations in the **Waveform Seekbar** to
  create more natural optic deform effect.
- Use relevance based search results matching in the **Search** panel.
- Match filenames and file paths too in the search panel to ensure tagless files are indexed in the
  search as well.

#### Changes

- Reorganized the **Player** screens removing the extra queue button.
- Removed the highlighted lyrics button from the **Player** panels.

#### Translations

- Added complete **Ukrainian** translations.
- Added complete **Persian** translations.
- Updated **Russian** and **Polish** translations.
