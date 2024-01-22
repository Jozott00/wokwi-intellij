# IntelliJ Wokwi Simulator Plugin

<!-- Plugin description -->

This plugin integrates the [Wokwi](https://wokwi.com/) simulator for ESP32 devices into JetBrains' IntelliJ-based IDEs.
The project is currently in its early stages and has not yet been published on JetBrains' Marketplace.

<!-- Plugin description end -->

## Roadmap

- [x] Execute simulations on a specified binary
- [x] Automatically restart simulations on new-build
- [ ] Support for a custom partition table
- [ ] Support for a custom bootloader
- [ ] Support for a custom Wokwi diagram
- [ ] GDB debugging support with IDE integration
- [ ] Native IDE logging window
- [ ] Automatic binary detection

## Current State

At present, it is possible to specify and run a binary on the Wokwi simulator within the IDE. By enabling binary watch,
the simulation automatically restarts after each new binary build.
![Simulation Configuration](https://github.com/Jozott00/wokwi-intellij/blob/main/blob/imgs/sim_screenshot0.png)
![Running Simulation](https://github.com/Jozott00/wokwi-intellij/blob/main/blob/imgs/sim_screenshot1.png)