# Felicity

*_Felicity_ is the third and final app of the three projects I had planned for my own learning, the first
two are [Positional](https://github.com/Hamza417/Positional)
and [Inure App Manager](https://github.com/Hamza417/Inure).*

The development of the app has started and if you've used the first two apps you might want to join
the [Telegram Channel](https://t.me/felicity_music_player) and become the part of the whole initial
development process.

I am taking my time developing it because I want it to exceed my own expectations and by that I mean
the quality of the software. I hope you are patient enough until the first few results :)

The project will be developed under the codename Felicity, the final name maybe updated in the
future.

## Stats

[![](https://img.shields.io/github/downloads/Hamza417/Felicity/total?color=blue&label=Total%20Downloads%20(GitHub)&logo=github&logoColor=white)](https://tooomm.github.io/github-release-stats/?username=Hamza417&repository=Felicity)
[![](https://img.shields.io/endpoint?url=https://ghloc.vercel.app/api/Hamza417/Felicity/badge?style=flat&logo=kotlin&logoColor=white&label=Total%20Lines&color=indianred)](https://ghloc.vercel.app/Hamza417/Felicity?branch=master)
[![Release](https://img.shields.io/github/v/release/Hamza417/Felicity?color=52be80&label=Current%20Release)](https://github.com/Hamza417/Felicity/releases)
![](https://img.shields.io/github/languages/count/Hamza417/Felicity?color=white&label=Languages)
![](https://img.shields.io/github/license/Hamza417/Felicity?color=red&label=License)
![](https://img.shields.io/badge/Minimum%20SDK-29%20(Android%2010)-839192?logo=android&logoColor=white)
![](https://img.shields.io/badge/Target%20SDK-36%20(Android%2016)-566573?logo=android&logoColor=white)
[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/Hamza417/Felicity/build_preview.yml?branch=master&logo=github&logoColor=white&label=build%20(preview)&color=white)](https://github.com/Hamza417/Felicity/actions/workflows/build_preview.yml)

## Purchase
[![](https://img.shields.io/badge/License%20Key%20(GumRoad)-Purchase-23a094?logo=gumroad&logoColor=white)](https://hamza417.gumroad.com/l/Felicity/)

## Features

### Custom Audio Engine

- **Dual Decoder** utilizing both hardware and software decoding through FFmpeg.
- **Custom DSP:** The entire audio processing chain (EQ, Bass, Reverb) is written in C++ via JNI. It utilizes ARM NEON SIMD auto-vectorization to process audio arrays with absolute minimum CPU overhead.
    - Supports bass, treble and more.
    - Native downmixing support to pass multichannel audio to stereo output.
- **Advanced Effects:** Integrated spatial effects including stereo widening and tape saturation for
  an analog feel.
- **10-band Equalizer:** A powerful equalizer with 10 adjustable frequency bands up to +/-15 dB with
  dedicated PreAmp support.
- **Gapless Playback:** Seamless transition between tracks without any gaps or interruptions.
- **High-Resolution Audio Support:** Support for high-resolution audio formats such as FLAC, ALAC,
  and DSD for audiophile-grade sound quality.
- **Multi-Channel Audio Support:** Support for multichannel audio formats like 5.1 and 7.1 surround
  sound for an immersive listening experience.
- **Milkdrop Visualizer:** Twin buffer enabled Milkdrop visualizer support powered by a native DSP,
  rendering on GL surface at native fps in real-time.

### User Interface

- **Fully custom-built and highly optimized** interface inspired by Inure App Manager.
- **Dynamic Theming:** The app's theme dynamically adapts to the album art of the currently playing
  track, creating a visually cohesive and immersive experience.
- **Custom Animations:** Smooth and visually appealing animations throughout the app, enhancing the
  user experience and making interactions more engaging.
- **Themes:** Multiple themes including light, dark, AMOLED black, Material You and others.
- **Core:** Predictive back, edge to edge and adapted to all modern Android UI features.
- **Embedded Lyrics:** Reliable, on-the-fly LRC extraction and support for online downloading from
  LrcLib.
- **Dual Fast Scroll:** Simultaneous support for both slide to scroll and jump to letter fast
  scroll.
- **Realtime Audio Visualizer:** A lock-free, zero-allocation visualizer rendering on the Canvas at native fps, powered by a native PFFFT implementation.

### Library Management

- **Realtime Library Updates:** The app automatically detects and updates the music library in
  real-time as new tracks are added or removed from the device adapted from Peristyle app.
- **All Storage Support:** Full support for both internal and external storages including SD cards
  and USB drives.
- **Auto Scanning:** The app automatically scans for new music files and updates the library without
  requiring manual refreshes.
- **Server Mode:** Host Felicity as a local server to create a central music library for all local and possibly remote devices through Wi-Fi.

### Smart Core

- **True Randomized Shuffle:** Choose between Miller and Fisher-Yates shuffle algorithms.

This feature list is not exhaustive and only main features are listed.

## Roadmap

- [x] Initial development and setup
- [x] Custom audio engine implementation
- [x] Basic playback controls and UI
- [x] Library management and scanning
- [x] Advanced audio effects and equalizer
- [x] Dynamic theming and custom animations
- [x] Embedded lyrics support
- [x] Realtime audio visualizer
- [x] Milkdrop visualizer support
- [ ] Crossfade support
- [ ] Multiple Player interface styles. _(partially fulfilled)_
- [x] Playlist support
- [ ] m3u and pls playlist support
- [x] Metadata editing support
- [ ] Replay gain
- [ ] Cue sheet support
- [x] Local server for centralized music access across multiple devices.
- [ ] Selection support for library management and playlist creation.

... and more features will be updated here as development progresses.

#### Development Roadmap

The development release sequence will be like

- Preview release (preview is made available to the early users and have the app available to try in the public domain, there are 10 previews planned).
- Alpha release (alpha testing will be done after almost every feature I have planned has been added)
- Beta release (app is stable enough to be moved to pre-release stage, this stage should be where app is released on various app stores.)
- Release (app should be released for everyone)

F-Droid release is currently not sure, the Glide library is breaking the reproducible build and I have submitted a PR to fix that but it has not been addressed yet at the time of writing this. See bumptech/glide#5657

## Screenshots

Last Updated: 10 April 2026

|                                                                      |                                                                      |                                                                      |
|----------------------------------------------------------------------|----------------------------------------------------------------------|----------------------------------------------------------------------|
| ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/01.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/02.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/03.png) |
| ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/04.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/05.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/06.png) |
| ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/07.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/08.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/11.png) |
| ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/12.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/13.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/14.png) |
| ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/15.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/16.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/17.png) |
| ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/18.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/19.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/20.png) |
| ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/21.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/22.png) | ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/23.png) |

|                                                                      |
|----------------------------------------------------------------------|
| ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/09.png) |
| ![](/fastlane/metadata/android/en-US/images/phoneScreenshots/10.png) |

## License

**Felicity Music Player** Copyright © 2026 - Hamza Rizwan

**Felicity Music Player** is released as open source software under
the [GNU AGPL v3](https://www.gnu.org/licenses/agpl-3.0.en.html)
license, see the [LICENSE](./LICENSE) file in the project root for the full license text.
