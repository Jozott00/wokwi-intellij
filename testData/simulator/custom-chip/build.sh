#!/bin/sh
# Regenerate using Arduino AVR GCC 7.3.0-atmel3.6.1-arduino7 and WASI SDK 27.0.
set -eu
cd "$(dirname "$0")"
: "${WASI_SDK_PATH:?Set WASI_SDK_PATH to the extracted WASI SDK 27.0 directory}"
"${AVR_GCC:-avr-gcc}" -mmcu=atmega328p -DF_CPU=16000000UL -Os -g -Wall -Wextra -o firmware.elf main.c
"${AVR_OBJCOPY:-avr-objcopy}" -O ihex -R .eeprom firmware.elf firmware.hex
# Match the LF bytes pinned by Git attributes and SHA256SUMS.
LC_ALL=C tr -d '\r' < firmware.hex > firmware.hex.tmp
mv firmware.hex.tmp firmware.hex
"$WASI_SDK_PATH/bin/clang" --sysroot="$WASI_SDK_PATH/share/wasi-sysroot" \
    -Os -Wall -Wextra -Werror -nostartfiles \
    -Wl,--import-memory -Wl,--export-table -Wl,--no-entry \
    -o chips/printer.chip.wasm chips/printer.chip.c
# Simulation artifacts are not host executables.
chmod 644 firmware.elf chips/printer.chip.wasm
