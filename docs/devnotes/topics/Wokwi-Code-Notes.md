# Wokwi Code Notes

## Firmware Packaging

Reads all required flash files and combines them in a `[[{offset: _, data: _}}]]` array at the right offset.
This is only used for **ESP-IDF** projects (C++ only), with a `build/flasher_args.json`.

```Javascript
a.packageEspIdfFirmware = async function (flasherArgsJson, firmwareBasePath) {
    const flasherArgs = JSON.parse(flasherArgsJson.toString());

    if (!flasherArgs.flash_files) {
        throw new Error("flash_files key is missing in flasher_args.json");
    }

    let fileContents = [];
    let filePaths = [];
    let maxFirmwareSize = 0;

    for (const [offsetHex, filePath] of Object.entries(flasherArgs.flash_files)) {
        const offset = parseInt(offsetHex, 16);
        if (isNaN(offset)) {
            throw new Error(`Invalid offset in flasher_args.json: ${offsetHex}`);
        }

        const resolvedFilePath = path.resolve(firmwareBasePath, filePath);
        filePaths.push(resolvedFilePath);

        const fileData = await readFileOrNull(resolvedFilePath);
        if (!fileData) {
            throw new Error(`Could not read file: ${filePath}`);
        }

        fileContents.push({offset, data: fileData});
        maxFirmwareSize = Math.max(maxFirmwareSize, offset + fileData.byteLength);
    }

    if (maxFirmwareSize > 16777216) {
        throw new Error(`Firmware size (${maxFirmwareSize} bytes) exceeds maximum supported size (16777216 bytes)`);
    }

    const firmwareData = new Uint8Array(maxFirmwareSize);
    for (const {offset, data} of fileContents) {
        firmwareData.set(new Uint8Array(data), offset);
    }

    return {
        firmware: firmwareData,
        watchPaths: filePaths
    };
};

```

{collapsible="true"}
