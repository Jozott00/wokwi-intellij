# IntelliJ Wokwi Simulator Plugin

This plugin integrates the [Wokwi](https://wokwi.com/) simulator for ESP32 devices into JetBrains' IntelliJ-based IDEs.
The project is currently in its early stages and has not yet been published on JetBrains' Marketplace.

## Project Status

The version in the `main` branch is very unstable and uses a non-maintaned version of the Wokwi simulator. Therefore, the plugin is rewritten and uses the same Wokwi simulator as the official VS-Code extension. In addition, the plugin configuration is aligned with the VS-Code configuration, i.e. the file `wokwi.toml` defines all relevant settings. This makes switching between IDEs effortless. 

The progress of the new plugin version is tracked in the pull request [#14](https://github.com/Jozott00/wokwi-intellij/pull/14).



## Main branch version

At present, it is possible to specify and run a binary on the Wokwi simulator within the IDE. By enabling binary watch,
the simulation automatically restarts after each new binary build.

![Simulation Configuration](https://github.com/Jozott00/wokwi-intellij/blob/main/blob/imgs/sim_screenshot0.png)
![Running Simulation](https://github.com/Jozott00/wokwi-intellij/blob/main/blob/imgs/sim_screenshot1.png)
