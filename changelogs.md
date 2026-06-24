#### User Interface

- Added current queue indicator in **Dashboard** header.

#### Bug Fixes

- Fixed queue reshuffling when app state is restored. #106
- Fixed various layout issues where the elements overlap with status and navigation bars. #109
- Fixed notifications not showing song title in broken audio metadata. #84
- Fixed broken **Total Time** dialog. #128
- Fixed page panels taking too long to load.
    - Current loading is optimized to load data over 8 times faster now.

#### Removed

- Removed USB DAC toggle.
    - DAC driver implementation has been postponed for later stages of development. DAC output will
      continue to work through other audio sinks.