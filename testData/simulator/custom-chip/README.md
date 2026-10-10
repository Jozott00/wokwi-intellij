# Custom chip fixture

This fixture loads a real WebAssembly custom chip through `[[chip]]` in
`wokwi.toml`. Its binary lives in `chips/printer.chip.wasm` with the matching
`printer.chip.json` pin manifest. The diagram instantiates it as
`chip-integration-printer` and connects VCC/GND to an Arduino Uno.

The ATmega328P runs at 16 MHz and loops silently; it does not configure UART.
The chip prints `Custom chip simulation ready` once from `chip_init()`.
`CustomChipTest` defines its expected text directly, including the
`[chip-integration-printer]` prefix added by the plugin's Run console.
Only executing the chip and forwarding its `chipOutput` message can produce
that text. The integration test covers configuration resolution, nested chip
paths, JSON and WASM loading, the JCEF bridge, chip initialization and console
rendering, then checks clean simulator termination.

The chip implements the API-version and initialization exports declared by
[Wokwi's API header](https://github.com/wokwi/inverter-chip/blob/main/src/wokwi-api.h).
It uses only standard C output, so it does not need the peripheral API header.
See [Wokwi's compilation guide](https://docs.wokwi.com/guides/custom-chips-to-wasm)
for the imported-memory, exported-table and no-entry linker flags.

## Regeneration

Tests use committed `firmware.hex`, `firmware.elf` and `chips/printer.chip.wasm`;
they never download toolchains or compile the fixture during a test run.

The AVR firmware uses Arduino AVR GCC `7.3.0-atmel3.6.1-arduino7`, the same
toolchain documented in [avr-uart](../avr-uart/README.md). The custom chip uses
[WASI SDK 27.0](https://github.com/WebAssembly/wasi-sdk/releases/tag/wasi-sdk-27).
The committed WASM was built with the arm64 macOS archive:

https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-27/wasi-sdk-27.0-arm64-macos.tar.gz

Archive SHA-256: `055c3dc2766772c38e71a05d353e35c322c7b2c6458a36a26a836f9808a550f8`.

From this directory, with both toolchains extracted:

```sh
AVR_GCC=/path/to/avr/bin/avr-gcc \
AVR_OBJCOPY=/path/to/avr/bin/avr-objcopy \
WASI_SDK_PATH=/path/to/wasi-sdk-27.0-arm64-macos sh build.sh
shasum -a 256 wokwi.toml diagram.json main.c firmware.hex firmware.elf \
  chips/printer.chip.c chips/printer.chip.json chips/printer.chip.wasm > SHA256SUMS
```

Review the sources, test assertions and regenerated artifacts together. ELF debug
paths can change its hash between directories. `SHA256SUMS` pins the committed
inputs; the harness verifies them before opening the IDE and copies only listed
files into an isolated project.
