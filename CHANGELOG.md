# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `DanmakuSelection` reports a tapped entry together with `topLeft`, `size`, `layerSize` and the moment it was
  pinned, so a host can anchor its own menu to an entry that is still moving.
- `DanmakuOverlay` takes `selection` and pins that entry in place while the rest keeps scrolling.
- `DanmakuOverlay` takes a `selectionContent` slot, composed inside the layer so the reported position can be
  used as an offset directly.

### Changed

- `DanmakuOverlay` replaces `onItemClick` with `onSelectionChange`, which also reports a tap on empty space
  that clears the current selection.

## [0.1.0] - 2026-10-03

### Added

- `DanmakuOverlay` draws entries scrolling over a video surface, spread over configurable lanes.
- `DanmakuClock` reads the playback timeline through a single method, so the library depends on no player.
- `DanmakuItem` carries the text, the moment it enters, its colour and whether it is the current user's.
- `DanmakuStyle` collects the lane count, the travel duration, the text style, the touch padding and the
  own-entry highlight.
- `DanmakuItemRenderer` replaces the stock look, and `drawDefaultDanmaku` extends it.
- `LaneAllocator` keeps entries sharing a lane from overlapping, falling back to the lane that clears
  first when every lane is busy so that no entry is dropped.
- Unit tests for the lane allocation rules.
- A sample screen that drives the clock from the frame loop, so it scrolls without a media dependency.