# Contributing

Thanks for taking the time to contribute. Issues and pull requests are both welcome.

## Requirements

- JDK 21
- Android SDK with platform 37
- Android Studio with AGP 9 support

## Build and test

```bash
./gradlew :danmaku:assembleDebug
./gradlew :danmaku:testDebugUnitTest
./gradlew :app:assembleDebug
```

`:danmaku` holds the published code, `:app` is a sample that consumes it.

On Windows, git does not record the executable bit. After adding or replacing `gradlew`, run
`git update-index --chmod=+x gradlew` before committing: CI runs the wrapper on Linux, where a
non-executable `gradlew` fails the build.

Dependencies resolve from Google and Maven Central.

## Code style

- The build runs in Kotlin explicit API mode, so a public declaration needs an explicit visibility modifier
  and an explicit return type. The compiler enforces this, not review.
- Public API is documented with English KDoc, including `@param` and `@property` for everything that is
  not obvious.
- Inline comments explain why something is done, not what the code already says.
- No commented out code and no `TODO` without a linked issue.
- Lane allocation, placement and hit testing stay pure and live in `xyz.larkzhh.danmaku.engine`, so they
  can be covered on the JVM without a device. New layout rules need unit tests there.

## Commits

The project uses [Conventional Commits](https://www.conventionalcommits.org/): `feat(scope): ...`,
`fix(scope): ...`, `test(scope): ...`, `docs: ...`.

## Pull requests

1. Keep the change focused, one topic per pull request.
2. Add or update tests when behaviour changes.
3. Run `./gradlew :danmaku:testDebugUnitTest` and `./gradlew :app:assembleDebug` before opening the pull
   request.
4. Update `CHANGELOG.md` under `Unreleased`.