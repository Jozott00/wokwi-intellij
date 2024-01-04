# Intellij Wokwi Simulator Plugin

Integrates the [Wokwi](https://wokwi.com/) simulator for ESP32 devices in Intellij based IDEs by Jetbrains.

The project is in early stage and not yet published on Jetbrains' marketplace.

# Roadmap

- [x] Run simulation on specified binary
- [x] Auto restart simulation on new build
- [ ] Custom partition table support
- [ ] Custom bootloader support
- [ ] Custom Wokwi diagram support
- [ ] Support GDB debugging with IDE integration
- [ ] IDE native logging window
- [ ] Automatic binary detection

# Current State

Currently it is possible to specify and run a binary on the Wokwi simulator within
the IDE. By enabling the binary watch, the simulation restarts on every new binary build.

![Simulation Configuration](https://github.com/Jozott00/wokwi-intellij/blob/main/blob/imgs/sim_screenshot0.png)

![Running Simulation](https://github.com/Jozott00/wokwi-intellij/blob/main/blob/imgs/sim_screenshot1.png)