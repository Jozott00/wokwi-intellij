# AVR HEX serial fixture

An Arduino Uno / ATmega328P at 16 MHz executes the bare-metal `main.c` and prints
`HEX simulation ready` followed by LF every second over UART0 at 9600 baud.
`diagram.json` connects Uno pin `1` (TX) to Wokwi's serial-monitor RX and pin `0`
(RX) to its TX, using the [official Uno pin names](https://docs.wokwi.com/parts/wokwi-arduino-uno).
No bootloader or
Arduino libraries are required. `firmware.hex` and `firmware.elf` come from the
same build; tests load the HEX file through the production configuration path.
`HexFirmwareTest` defines its expected UART text directly in the test.

## Regeneration

The committed binaries were built using Arduino's AVR GCC
`7.3.0-atmel3.6.1-arduino7` (macOS x86_64 distribution, usable under Rosetta).
Download the official archive:

https://downloads.arduino.cc/tools/avr-gcc-7.3.0-atmel3.6.1-arduino7-x86_64-apple-darwin14.tar.bz2

Archive SHA-256: `f6ed2346953fcf88df223469088633eb86de997fa27ece117fd1ef170d69c1f8`.
Arduino's package index records the compiler version and archive checksum:
https://downloads.arduino.cc/packages/package_index.json

From this directory, with that toolchain extracted:

```sh
AVR_GCC=/path/to/avr/bin/avr-gcc AVR_OBJCOPY=/path/to/avr/bin/avr-objcopy sh build.sh
shasum -a 256 wokwi.toml diagram.json main.c firmware.hex firmware.elf > SHA256SUMS
```

Review changes to the source, test assertions and artifacts together. ELF debug
information can contain build paths; its hash may change when regenerated in a
different directory. `SHA256SUMS` pins the exact committed inputs, not a guarantee
that every host produces a byte-identical ELF. Tests verify all entries before
opening an IDE and never invoke an embedded compiler implicitly.
