# AVR UART and debugger fixture

An Arduino Uno / ATmega328P at 16 MHz executes the bare-metal `main.c` with
UART0 at 9600 baud. It initializes a local volatile `counter` to 41, increments
it on two separate source lines, then repeatedly prints `AVR simulation ready`
followed by LF, approximately once per second. No bootloader or Arduino libraries
are required. The delay uses `util/delay_basic.h`, which supports unoptimized builds.
`diagram.json` connects Uno pin `1` (TX) to Wokwi's serial-monitor RX and pin `0`
(RX) to its TX, using the [official Uno pin names](https://docs.wokwi.com/parts/wokwi-arduino-uno).

Both tests use the same `wokwi.toml`, loading `firmware.hex` through the production
configuration path and using `firmware.elf` from the same build for debug symbols:

- `HexFirmwareTest` starts normal simulation and asserts the UART marker.
- `ClionDebuggerTest` stops at `DEBUG_BREAKPOINT`, inspects 41, steps to
  `DEBUG_STEP`, inspects 42, then resumes and asserts the same UART marker.

The ELF is built with `-O0 -gdwarf-2` for predictable source stepping and variable
inspection. Its source directory is normalized to `/wokwi-avr-fixture`; the
Remote Debug configuration maps that directory to each isolated project copy.
No original checkout or local SDK settings are required. The absent
`gdbServerPort` requests an ephemeral port, exercising `$WokwiGdbServer$` after
the before-launch task binds it.

## Regeneration

The committed binaries use Arduino's AVR GCC `7.3.0-atmel3.6.1-arduino7`
(macOS x86_64 distribution, usable under Rosetta). Official archive:

https://downloads.arduino.cc/tools/avr-gcc-7.3.0-atmel3.6.1-arduino7-x86_64-apple-darwin14.tar.bz2

Archive SHA-256: `f6ed2346953fcf88df223469088633eb86de997fa27ece117fd1ef170d69c1f8`.
Arduino's package index records the compiler version and archive checksum:
https://downloads.arduino.cc/packages/package_index.json

From this directory, with that toolchain extracted:

```sh
AVR_GCC=/path/to/avr/bin/avr-gcc AVR_OBJCOPY=/path/to/avr/bin/avr-objcopy sh build.sh
```

This refreshes `firmware.hex`, `firmware.elf` and `SHA256SUMS`. Test runs verify
the manifest and use committed binaries without requiring a compiler. Review
source, assertions and artifacts together when regenerating. Use a recent
AVR-capable GDB through `WOKWI_TEST_GDB` if CLion's bundled GDB lacks AVR support.
