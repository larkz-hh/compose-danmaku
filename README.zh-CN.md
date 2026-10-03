# compose-danmaku

[![JitPack](https://jitpack.io/v/larkz-hh/compose-danmaku.svg)](https://jitpack.io/#larkz-hh/compose-danmaku)
[![CI](https://github.com/larkz-hh/compose-danmaku/actions/workflows/ci.yml/badge.svg)](https://github.com/larkz-hh/compose-danmaku/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-23-green.svg)](#引入)
[![Compose BOM](https://img.shields.io/badge/Compose%20BOM-2026.02.01-4285F4.svg)](#引入)
[![Stars](https://img.shields.io/github/stars/larkz-hh/compose-danmaku?style=flat)](https://github.com/larkz-hh/compose-danmaku/stargazers)

[English](README.md) · [简体中文](README.zh-CN.md)

compose-danmaku 是一个将弹幕投放到视频画面之上滚动的 Compose 库，把弹幕分配到固定数量的轨道上，同轨道内的弹幕互不重叠，单条弹幕的渲染方式由调用方决定。它不依赖任何播放器，播放进度通过单方法接口传入。

## 特性

- 弹幕按可配置的轨道数分配，同轨道内做碰撞规避。
- 无播放器依赖。`DanmakuClock` 为单方法接口，ExoPlayer、MediaPlayer 与自行推进的时钟接入方式一致。
- 绘制位于 draw 阶段，逐帧不触发重组与重新布局。
- 支持逐条颜色，以及本人弹幕的高亮底板。
- `DanmakuItemRenderer` 可整体替换默认样式，`drawDefaultDanmaku` 可在默认样式上追加。
- 点击命中按弹幕的当前位置判定。
- 选中一条弹幕会把它钉在原地并给出它当前的位置，供调用方锚定自己的菜单。
- 可限制同屏弹幕数量，并可选择轨道占满时是重叠还是丢弃。

## 引入

```kotlin
implementation("com.github.larkz-hh:compose-danmaku:0.2.0")
```

要求 minSdk 23、Compose BOM 2026.02.01。库仅依赖 `compose-ui`、`compose-foundation` 与 `compose-runtime`，不含 Material，也不含播放器。

## 用法

将 `DanmakuOverlay` 叠加在视频画面之上并传入时间轴：

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

`DanmakuClock { player.currentPosition }` 即完整接入代码。任何以毫秒为单位提供播放进度的实现均可使用，包括自行推进的时钟。

图层高度固定为 `laneCount * laneHeight`，对齐方式由调用方决定，不自动撑满屏幕。

如需自定义单条弹幕的渲染，传入 `DanmakuItemRenderer`，需要保留默认外观的部分调用 `drawDefaultDanmaku`：

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

若要把点击的弹幕钉住、并把自定义菜单锚定到它上面，持有图层上报的选中状态并回传即可：

```kotlin
var selection by remember { mutableStateOf<DanmakuSelection?>(null) }

DanmakuOverlay(
    items = entries,
    clock = clock,
    selection = selection,
    onSelectionChange = { selection = it },
) { selected ->
    // 该插槽在图层内部组合，上报的坐标可直接当作偏移量使用。
    MyMenu(anchor = selected.topLeft, onDismiss = { selection = null })
}
```

`selection` 保持为 `null` 时所有弹幕照常滚动；图层只会钉住该状态指向的那一条。菜单里放什么、某条弹幕能否被删除，仍由调用方决定。

## API 参考

`DanmakuOverlay` 的参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `items` | — | 要绘制的弹幕，顺序不限，内部按 `timeMs` 排序 |
| `clock` | — | 播放时间轴，每帧读取一次 |
| `modifier` | `Modifier` | 作用于图层本身 |
| `style` | `DanmakuStyle.Default` | 外观与布局 |
| `enabled` | `true` | 是否绘制；关闭后图层仍保留占位高度 |
| `opacity` | `1f` | 图层全部内容的透明度 |
| `itemRenderer` | `DefaultDanmakuItemRenderer` | 单条弹幕的绘制方式 |
| `selection` | `null` | 要钉住的弹幕，通常回传 `onSelectionChange` 最近上报的值 |
| `onSelectionChange` | `null` | 点中弹幕时上报该弹幕；已选中时点空白处上报 `null`；为 `null` 时不消费触摸 |
| `selectionContent` | `{}` | 当 `selection` 能对应到弹幕时，在图层坐标系内组合的内容 |

`DanmakuItem` 的属性：

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `id` | — | 稳定标识，列表内唯一 |
| `text` | — | 绘制文本，不换行、不裁剪 |
| `timeMs` | — | 从右侧进入的时刻，基准与时钟一致 |
| `color` | `Color.White` | 文字颜色，覆盖 `DanmakuStyle.textStyle` |
| `isSelf` | `false` | 是否本人发送，由调用方判断 |

`DanmakuStyle` 的属性：

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `laneCount` | `3` | 轨道数；图层高度为 `laneCount * laneHeight` |
| `laneHeight` | `22.dp` | 单条轨道高度，文字在轨道内垂直居中 |
| `durationMillis` | `8_000L` | 单条弹幕穿过图层的时长 |
| `itemGap` | `16.dp` | 同轨道相邻弹幕的最小水平间隔 |
| `textStyle` | `DefaultDanmakuTextStyle` | 测量与绘制使用的文字样式 |
| `touchPadding` | `8.dp` | 点击判定相对文字的外扩量 |
| `selfHighlight` | `DanmakuSelfHighlight.Default` | 本人弹幕底板，`null` 表示关闭 |
| `maxVisible` | `Int.MAX_VALUE` | 同屏弹幕数量上限，超出的丢弃 |
| `overflowPolicy` | `DanmakuOverflowPolicy.Overlap` | 轨道占满时到达的弹幕如何处理 |

`DanmakuSelection`，由 `onSelectionChange` 上报：

| 属性 | 说明 |
| --- | --- |
| `item` | 被选中的弹幕 |
| `topLeft` | 该弹幕的位置（像素），相对图层左上角 |
| `size` | 该弹幕的测量尺寸（像素） |
| `layerSize` | 图层尺寸，用于把菜单收在图层范围内 |
| `frozenAtMs` | 该弹幕被钉住时所处的播放进度 |

`DanmakuOverflowPolicy` 有两个取值：`Overlap` 占用最早空出的轨道绘制，即使与已有弹幕重叠；`Drop` 则不绘制该条。

绘制侧还包含 `DanmakuItemRenderer` 与 `DanmakuDrawContext`，`DanmakuSelfHighlight` 定义底板配色与内边距，`DefaultDanmakuTextStyle` 为未传入样式时使用的文字样式。

## 注意事项

- 所有轨道均被占用时，弹幕占用最早空出的轨道，可能与更早的弹幕重叠；`DanmakuOverflowPolicy.Drop` 则不绘制该条。
- `maxVisible` 只统计真正在屏上的弹幕，被占满策略丢弃的弹幕不会占用其它弹幕的名额。
- `overflowPolicy` 只在 `maxVisible` 还有余量时才会生效。轨道的占用持续到其上一条弹幕的尾部越过右边界为止，这远早于该弹幕离屏；因此 `maxVisible <= laneCount` 时上限总是先触发。
- `maxVisible` 与 `overflowPolicy` 影响布局，因此放在 `DanmakuStyle` 中，修改它们会重新测量弹幕；`opacity` 不影响布局，仍作为 `DanmakuOverlay` 的参数。
- `timeMs` 必须与时钟的原点及单位一致。单位或起点与弹幕数据不同会导致全部弹幕错位。
- `onSelectionChange` 为 `null` 时图层不消费触摸事件，适用于覆盖在点击即暂停的表面上。
- `selectionContent` 在图层内部组合，因此 `DanmakuSelection.topLeft` 可直接当作偏移量。放在图层外部的
  内容需要自行叠加图层自身的位置。
- 再次点击已被钉住的弹幕，它会留在原处，而不是按当前时刻重新钉一次。
- 宽于图层的弹幕不换行，整条滚动通过，以保证轨道分配的可预测性。
- 时钟每帧调用一次，实现需保持轻量且不得阻塞。
- 内联构造 `DanmakuClock { ... }` 是安全的：帧循环始终读取最新的时钟且不会重启。
- 修改 `opacity` 不触发重新测量，仅在绘制阶段生效。

## 示例

`:app` 模块的时钟由帧循环推进而非播放器，无需媒体依赖即可运行：

```bash
./gradlew :app:installDebug
```

点击弹幕会把它钉住并弹出示例自己的菜单，位置取自图层上报的坐标。暂停会冻结时钟，关闭图层后布局保持不变。

## 使用方

- [Lime](https://github.com/larkz-hh/lime-app)：一个 Compose 社区应用。

## 参与贡献

欢迎提交 Issue 与 PR。构建命令、代码风格和提交规范见 [CONTRIBUTING.md](CONTRIBUTING.md)，版本变更见 [CHANGELOG.md](CHANGELOG.md)。

## 鸣谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose)：图层绘制、文本测量与手势处理。
- [Kotlin](https://kotlinlang.org/)：语言与协程。
- [Gradle](https://gradle.org/) 与 [Android Gradle Plugin](https://developer.android.com/build)：构建与 AAR 发布。
- [JUnit 4](https://junit.org/junit4/)：轨道分配规则的单元测试。

## 许可证

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

维护者 [@larkz-hh](https://github.com/larkz-hh)