# compose-danmaku

[![JitPack](https://jitpack.io/v/larkz-hh/compose-danmaku.svg)](https://jitpack.io/#larkz-hh/compose-danmaku)
[![CI](https://github.com/larkz-hh/compose-danmaku/actions/workflows/ci.yml/badge.svg)](https://github.com/larkz-hh/compose-danmaku/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-23-green.svg)](#download)
[![Compose BOM](https://img.shields.io/badge/Compose%20BOM-2026.02.01-4285F4.svg)](#download)
[![Stars](https://img.shields.io/github/stars/larkz-hh/compose-danmaku?style=flat)](https://github.com/larkz-hh/compose-danmaku/stargazers)

[English](README.md) · [简体中文](README.zh-CN.md)

compose-danmaku is a Compose library that scrolls bullet comments across a video surface, distributing
entries over a fixed number of lanes without overlapping inside a lane. It depends on no player, reading the
timeline through a single-method interface.

## Features

- Entries distributed over a configurable number of lanes, without overlapping inside a lane.
- No player dependency: `DanmakuClock` is a single method.
- Drawing in the draw phase, with no composition or layout work per frame.
- Per-entry colour and font size.
- A highlight plate for the current user's own entries, and an optional outline behind the text.
- A cap on how many entries may be on screen at once, and a choice of overlap or drop when every lane is busy.
- Hit testing against the position entries are at now, and a pinned selection whose position is reported.
- A default bubble that follows the selected entry and keeps itself inside the layer.
- `DanmakuItemRenderer` replaces the default look, and `drawDefaultDanmaku` extends it.

## Download

```kotlin
implementation("com.github.larkz-hh:compose-danmaku:0.2.0")
```

Requires minSdk 23 and Compose BOM 2026.02.01. Runtime dependencies are `compose-ui`, `compose-foundation` and `compose-runtime`.

## Usage

Overlay `DanmakuOverlay` on the video surface and give it a timeline:

```kotlin
@Composable
fun VideoScreen(player: ExoPlayer, entries: List<DanmakuItem>) {
    Box {
        PlayerSurface(player)

        DanmakuOverlay(
            items = entries,
            clock = DanmakuClock { player.currentPosition },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )
    }
}
```

`DanmakuClock { player.currentPosition }` is the complete integration. Any source reporting a position in
milliseconds works, including a clock advanced by hand.

The layer is `laneCount * laneHeight` tall and does not size itself to its parent.

To customise a single entry, pass a `DanmakuItemRenderer` and call `drawDefaultDanmaku` for the parts to keep:

```kotlin
val outlined = DanmakuItemRenderer { context ->
    drawRect(
        color = Color.Black.copy(alpha = 0.3f),
        topLeft = context.topLeft,
        size = context.size.toSize(),
    )
    drawDefaultDanmaku(context)
}

DanmakuOverlay(items = entries, clock = clock, itemRenderer = outlined)
```

To pin a tapped entry and anchor your own menu to it, hold the selection the layer reports and feed it back:

```kotlin
var selection by remember { mutableStateOf<DanmakuSelection?>(null) }

DanmakuOverlay(
    items = entries,
    clock = clock,
    selection = selection,
    onSelectionChange = { selection = it },
) { selected ->
    // The plate, its arrow and the clamping come from the library.
    DanmakuBubble(selection = selected) {
        DanmakuBubbleItem("Copy") { copy(selected.item.text) }
        DanmakuBubbleItem("Report") { report(selected.item) }
    }
}
```

Leaving `selection` at `null` keeps every entry moving; the layer only pins the entry that selection names.


## API reference

Parameters of `DanmakuOverlay`:

| Parameter | Default | Description |
| --- | --- | --- |
| `items` | — | Entries to draw; order does not matter, they are sorted by `timeMs` |
| `clock` | — | Playback timeline, read once per frame |
| `modifier` | `Modifier` | Applied to the layer itself |
| `style` | `DanmakuStyle.Default` | Appearance and layout |
| `enabled` | `true` | Whether entries are drawn; the layer keeps its placeholder height either way |
| `opacity` | `1f` | Opacity of everything the layer draws |
| `itemRenderer` | `DefaultDanmakuItemRenderer` | How a single entry is drawn |
| `selection` | `null` | Entry to pin in place, normally the value last reported by `onSelectionChange` |
| `onSelectionChange` | `null` | Called with the tapped entry, and with `null` on a tap on empty space while something is selected; `null` consumes no touches |
| `selectionContent` | `{}` | Content composed inside the layer's coordinate space while `selection` resolves to an entry |

Properties of `DanmakuItem`:

| Property | Default | Description |
| --- | --- | --- |
| `id` | — | Stable identity, unique within the list |
| `text` | — | Text to draw, never wrapped or clipped |
| `timeMs` | — | Moment the entry enters from the right, on the clock's basis |
| `color` | `Color.White` | Text colour, overriding `DanmakuStyle.textStyle` |
| `isSelf` | `false` | Whether the current user sent it, decided by the caller |
| `scale` | `1f` | Font size multiplier applied to `DanmakuStyle.textStyle` |
| `width` | `null` | Width reserved for the entry, or `null` to use the measured text width |

Properties of `DanmakuStyle`:

| Property | Default | Description |
| --- | --- | --- |
| `laneCount` | `3` | Number of lanes; the layer is `laneCount * laneHeight` tall |
| `laneHeight` | `22.dp` | Height of one lane, text centred inside it |
| `durationMillis` | `8_000L` | Time one entry takes to cross the layer |
| `itemGap` | `16.dp` | Minimum horizontal gap between two entries sharing a lane |
| `textStyle` | `DefaultDanmakuTextStyle` | Style used for measuring and drawing |
| `touchPadding` | `8.dp` | How far the tap target extends past the text |
| `selfHighlight` | `DanmakuSelfHighlight.Default` | Plate behind own entries, `null` disables it |
| `maxVisible` | `Int.MAX_VALUE` | Upper bound on how many entries may be on screen at once; the rest are dropped |
| `overflowPolicy` | `DanmakuOverflowPolicy.Overlap` | What to do with an entry that arrives while every lane is busy |
| `textOutline` | `null` | Outline stroked behind the text, or `null` for none |

`DanmakuSelection`, reported by `onSelectionChange`:

| Property | Description |
| --- | --- |
| `item` | The selected entry |
| `topLeft` | Where the entry sits in pixels, relative to the layer's top left corner |
| `size` | Measured size of the entry in pixels |
| `layerSize` | Size of the layer, for keeping a menu inside it |
| `frozenAtMs` | The clock value the entry is pinned at |

`DanmakuOverflowPolicy` is either `Overlap`, which draws the entry on the lane that clears first even though it
overlaps one already there, or `Drop`, which leaves it out.

`DanmakuBubble(selection, style) { content }` is the default plate: it follows the selected entry, keeps
itself inside the layer and aims an arrow at the entry.
`DanmakuBubbleItem(text) { ... }` is a plain item that matches the plate, and `DanmakuBubbleStyle` carries
`background`, `contentColor`, `cornerRadius`, `itemTextStyle`, `arrowWidth` and `arrowHeight`.

The drawing side also includes `DanmakuItemRenderer` and `DanmakuDrawContext`; `DanmakuSelfHighlight` carries
the plate colours and padding, and `DefaultDanmakuTextStyle` is the text style used when none is passed.

## How it works

Lanes are assigned greedily in time order: each entry takes the earliest lane that has freed up, and a lane stays taken until the tail of its last entry has cleared the right edge, which is `durationMillis * (width + itemGap) / (containerWidth + width)` after that entry entered. `laneCount` is therefore a ceiling rather than a fixed number of rows: when entries arrive further apart than that duration divided by the lane count, the spare lanes are never used.

The playback position is written into state that only the draw phase reads, so the frame loop causes redraws and never recomposition or layout.

Text is measured once, whenever the entry list, the layer width or `DanmakuStyle` changes. That is why `style` is one of the measurement cache keys, and why `opacity`, which only affects drawing, is a parameter of its own.

## Notes

- When every lane is occupied, an entry takes the lane that clears first and may overlap an earlier entry. With
  `DanmakuOverflowPolicy.Drop` it is not drawn at all.
- `maxVisible` counts only the entries actually on screen, so an entry dropped by the overflow policy never uses
  up room another entry could have taken.
- `overflowPolicy` is only reached while `maxVisible` still has room. A lane counts as busy until the tail of
  its last entry has cleared the right edge, well before that entry leaves the screen, so with
  `maxVisible <= laneCount` the cap always fires first.
- `maxVisible` and `overflowPolicy` change the layout, so they live in `DanmakuStyle` and changing them
  re-measures the entries. `opacity` changes no layout and stays a parameter of `DanmakuOverlay`.
- `DanmakuItem.width` only overrides the horizontal space reserved. The height still comes from the text, so
  content that is not text is expected to fit within `DanmakuStyle.laneHeight`.
- The layer only sees taps inside its own `laneCount * laneHeight` band, which is 66 dp at the defaults. A tap
  anywhere else never reaches it, so dismissing a selection from the rest of the screen is the host's to wire
  up. A tap that does land on an entry is consumed by the layer, so a handler on the surface underneath does
  not fire for it as well.
- `timeMs` has to share the origin and the unit of the clock. A different unit, or a different starting point
  than the data was recorded against, shifts every entry.
- With `onSelectionChange` left at `null` the layer consumes no touches, which suits a layer over a surface
  that toggles playback on tap.
- `selectionContent` is composed inside the layer, so `DanmakuSelection.topLeft` is an offset already.
  Content placed outside the layer would need the layer's own position added to it.
- Re-tapping the pinned entry keeps it where it is instead of pinning it again at the current moment.
- Entries wider than the layer are not wrapped and scroll through whole, which keeps lane allocation
  predictable.
- The clock is called once per frame, so it must be lightweight and must not block.
- Building `DanmakuClock { ... }` inline is safe: the frame loop always reads the most recent clock and never
  restarts.
- Changing `opacity` triggers no re-measure, as it is applied while drawing.

## Sample

`:app` is a sample whose clock is advanced by the frame loop rather than a player, so it runs without a media
dependency:

```bash
./gradlew :app:installDebug
```

Tapping an entry pins it and opens a menu built from the library's bubble. The `Settings` button opens a panel
that drives the opacity, the on-screen bound, the font size, the speed, the lane count and the outline.
Tapping the same entry again releases it.

## Used by

- [Lime](https://github.com/larkz-hh/lime-app): a Compose community app.

## Contributing

Issues and pull requests are welcome. Build commands, code style and commit conventions are in
[CONTRIBUTING.md](CONTRIBUTING.md), and released changes are listed in [CHANGELOG.md](CHANGELOG.md).

## Acknowledgements

- [Jetpack Compose](https://developer.android.com/jetpack/compose) for drawing, text measurement and gesture handling.
- [Kotlin](https://kotlinlang.org/) for the language and coroutines.
- [Gradle](https://gradle.org/) and the [Android Gradle Plugin](https://developer.android.com/build) for the build and the AAR publishing.
- [JUnit 4](https://junit.org/junit4/) for the lane allocation unit tests.

## License

```
Copyright 2026 larkz-hh

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Maintained by [@larkz-hh](https://github.com/larkz-hh).