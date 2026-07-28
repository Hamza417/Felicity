#### User Interface

- Added toggle to stack **Waveform** and **Media Controls** in **Now Playing** screen.

#### Playback

- Added a route based audio sink to create exclusive audio playback pipeline. #137
    - Also, possibly fix the rainy audio issues in some devices due to exclusive audio output fixing
      many audio underrun issues.
    - Eliminates the need for double clock sync architecture reducing half the CPU load and saves
      battery.

#### Translations

- Added **Spanish** translations.