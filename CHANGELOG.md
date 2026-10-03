# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0] - 2026-10-03

### Added

- Measured layouts are kept across placement passes, so an entry list that grew by one entry costs one
  measurement rather than one per entry. A layer of 8,000 entries re-places in 30 ms, where re-measuring the
  whole list took 1,270 ms.
- A measured cost report for a placement pass, with charts and reproduction commands, in `PERFORMANCE.md`
  and `PERFORMANCE.zh-CN.md`.

### Changed

- `DanmakuDrawContext.textLayout` carries the colour of `DanmakuStyle.textStyle` rather than
  `DanmakuItem.color`. One layout is shared by every entry of the same text and size, and a layout holds only
  one colour. A custom `DanmakuItemRenderer` that hands it to `drawText` without a colour has to pass
  `context.item.color`, which `drawDefaultDanmaku` does.

### Fixed

- An entry no longer takes the colour of another entry sharing its text and size. Compose caches layouts under
  layout-affecting attributes only, so a colour difference between two such entries was not part of the cache
  key and could be dropped.

## [0.2.0] - 2026-10-03

### Added

- `DanmakuSelection` reports a tapped entry together with `topLeft`, `size`, `layerSize` and the moment it was
  pinned, so a host can anchor its own menu to an entry that is still moving.
- `DanmakuOverlay` takes `selection` and pins that entry in place while the rest keeps scrolling.
- `DanmakuOverlay` takes a `selectionContent` slot, composed inside the layer so the reported position can be
  used as an offset directly.
- `DanmakuStyle.maxVisible` bounds how many entries may be on screen at once; an entry arriving while the layer
  is full is dropped.
- `DanmakuStyle.overflowPolicy` chooses between overlapping on the lane that clears first and dropping, for an
  entry that arrives while every lane is busy.
- `DanmakuItem.scale` sets a per-entry font size, and `DanmakuItem.width` reserves horizontal space for content
  that is not text.
- `DanmakuStyle.textOutline` strokes an outline behind the text.
- `DanmakuBubble` draws a default plate under the selected entry with an arrow aimed at it, and
  `DanmakuBubbleItem` is a plain item that matches the plate.
- Unit tests for the placement maths, the bubble geometry and the tap rules.
- A sample settings panel for the opacity, density, font size, speed, lane count and outline.

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