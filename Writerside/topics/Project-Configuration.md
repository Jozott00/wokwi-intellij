# Project Configuration

![Wokwi Config Showcase](sim_cfg.png)

After installing the plugin and activating the license as per the [installation instructions](starter-topic.md#installation), configure your project for Wokwi use.

For project simulation on Wokwi, create these two files in the project's root directory:
- `wokwi.toml`: This configuration file instructs Wokwi on project execution.
- `diagram.json`: This [diagram file](https://docs.wokwi.com/diagram-format) outlines the circuit.

## wokwi.toml
The `wokwi.toml` file is usually placed in the project's root directory. To store it in a different location, specify the new path in the simulator settings.

A basic `wokwi.toml` example:
```
[wokwi]
version = 1
elf = 'path-to-your-firmware.elf'
firmware = 'path-to-your-firmware.bin'
```
The `elf` and `firmware` fields in `wokwi.toml` are paths relative to the `wokwi.toml` file's location. 
For additional details, refer to the [official Wokwi configuration docs](https://docs.wokwi.com/vscode/project-config#wokwitoml).


> Some features available in the VS Code extension might not be supported. For future updates, check the [roadmap](Roadmap.md).
{style="warning"}

## diagram.json

To obtain a diagram file for your project, you can copy it from an existing project on [wokwi.com](https://wokwi.com). 
For example, if your project involves an ESP32, you can copy the `diagram.json` contents from the [default ESP32 template](https://wokwi.com/projects/new/esp32).

