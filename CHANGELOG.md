<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# wokwi-intellij Changelog

## [Unreleased]

### Added

- Support for IntelliJ Platform 2025.1.*

## [0.10.3] - 2024-09-18

### Added

- Support for IntelliJ Platform 2024.3.*

### Chore

- CI: Fix gradle-setup action by upgrading to v4 

## [0.10.2] - 2024-09-18

### Added

- Support for IntelliJ Platform 2024.2.*

## [0.10.1] - 2024-03-06

### Bugfix

- UI Freezes on startup (when debug config selected)

### Added

- Support for Intellij platform 2024.1

## [0.10.0] - 2024-03-04

This update switches from the custom run toolwindow
to a headless run configuration, which is more familiar for
most Intellij users.

### Added

- Run configuration for simulator instance
- Automatic GDB port selection

### Removed

- WokwiDebugStart configuration
- Wokwi Run toolwindow

## [0.9.0] - 2024-02-23

### Added

- Embedded Wokwi simulator
- Wokwi console to control simulator
- wokwi.toml analysis support
- Wokwi simulation debugging support

[Unreleased]: https://github.com/Jozott00/wokwi-intellij/compare/v0.10.3...HEAD
[0.10.3]: https://github.com/Jozott00/wokwi-intellij/compare/v0.10.2...v0.10.3
[0.10.2]: https://github.com/Jozott00/wokwi-intellij/compare/v0.10.1...v0.10.2
[0.10.1]: https://github.com/Jozott00/wokwi-intellij/compare/v0.10.0...v0.10.1
[0.10.0]: https://github.com/Jozott00/wokwi-intellij/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/Jozott00/wokwi-intellij/commits/v0.9.0
