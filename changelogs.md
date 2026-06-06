### PEQ and DSP

- Added **Parametric Equalizer (PEQ)** with **_n_-bands suppor**t.
    - Added a dedicated **PEQ engine to the DSP**.
- Added **coeffs smoothing** to solve the zipper noise issue when changing EQ settings.

### Output

- Added native **USB DAC driver** for exclusive output through the DAC devices.
- Added USB DAC to **PCM Info**.
- Added **DAC and AAudio bypass** to **support DSP in Hi-Res mode**. #25
- Fixed Hi-Res stuttering via AAudio sink.
- Force float32 bypass via AAudio, Oboe and USB DAC for ExoPlayer.
- Feed audio data to visualizer in Hi-Res mode to make sure visualizer works. #25
- Fixed audio blips in AAudio and Oboe modes.
- Fixed audio reroute failure on output device change.

### Library

- Added option to manually pick an artist image.
- Add webm audio support. #80

### User Interface

- Added new **Carousel** style player UI. #105
- Added option to play selected songs as queue.
- Updated EQ panel UI.
- Organize **Audio Preferences**.
- Added toggle to show/hide **Lyrics** in **Player** page.
- Add toggle for visualizer caps.

### Bug Fixes

- Fixed volume button events getting triggered twice on button presses causing inaccurate volume
  changes. #90
- Fixed app crashing randomly when changing songs.
- Fixed downloaded artist image not showing in **Artist** page. #71

### Improvements

- Increase volume gradually when volume buttons are long pressed. #102
- Open IMEs automatically when **Search** panel is opened. #76
- Auto hide volume panels.
- Easier to swipe away the player screen.
- Added GPU accelerated blur to blur album arts faster.

### Translations

- Added **Russian** translations.
- Added **German** translations.
- Added **Turkish** translations.
- Added **Italian** translations.
- Added **Chinese (Simplified)** translations.