# Contributing to Nous Platform

Thanks for your interest in contributing! Nous Platform is a Kotlin Multiplatform
project licensed under AGPL-3.0-or-later. Contributions are welcome in any form:
bug reports, feature requests, documentation, and pull requests.

## Contributor License Agreement (CLA)

Before your first pull request can be merged, you must agree to the
[Contributor License Agreement](CLA.md). By submitting a pull request you confirm
that you accept its terms. The CLA allows the project owner to relicense
contributions under a commercial license, keeping the dual-licensing model intact.

## Getting Started

### Requirements

- JDK 17+ (Gradle 9.4). Builds are verified with Temurin 24.
- No Android Studio is required for the JVM/JS targets, but the Android SDK
  (`local.properties`) is needed for Android targets.

### Build

```bash
./gradlew build                # full build, all modules
./gradlew compileKotlinMetadata  # quick metadata compilation check
./gradlew :composeApp:run      # run the desktop application
```

## Project Structure

The project is a modular Kotlin Multiplatform codebase:

| Module | Purpose |
|--------|---------|
| `composeApp` | Desktop/Android application shell (Compose Multiplatform) |
| `platform-core` | Core services: networking, storage, workspace, shared UI components |
| `public-api/api-market` | Market data API (symbols, order book, trades, chart) |
| `public-api/api-trading` | Trading API (orders, positions) |
| `public-api/api-ui` | UI widget API (chart, order book widgets) |
| `features/dom`, `features/chart`, `features/trades`, `features/settings`, `features/localstorage` | Feature modules built on the public APIs |
| `providers/binance-provider` | Binance implementation of the market/trading APIs |
| `bidasker-web` | Web (Kotlin/JS) companion application |
| `build-logic` | Convention plugins shared by all modules |

## Code Style

- Kotlin official code style (`kotlin.code.style=official`).
- Keep file headers intact; every source file carries the SPDX notice:

  ```text
  Copyright (C) 2026 Sergey Orlov
  SPDX-License-Identifier: AGPL-3.0-or-later
  ```

- New feature modules follow the existing convention plugins
  (`KmpFeatureConvention`, `KmpLibraryConvention`).

## Submitting a Pull Request

1. Fork the repository and create a feature branch.
2. Make focused, atomic commits with clear messages.
3. Add or update tests where applicable.
4. Run `./gradlew build` and make sure it passes.
5. Open a pull request with a description of the change and its motivation.

By opening a pull request you confirm you have read and accepted the
[CLA](CLA.md).
