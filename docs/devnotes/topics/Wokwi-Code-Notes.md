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

```javascript
// Listener for the 'start' event with an asynchronous callback function
t.listen("start", async settings => {
    // Destructuring the settings object for easier access to its properties
    let {
        diagram: diagramJSON,
        firmware: firmwareData,
        chips: chipSettings,
        useGateway: shouldUseGateway,
        pause: shouldPause,
        firmwareB64: isFirmwareBase64,
        disableSerialMonitor: shouldDisableSerialMonitor
    } = settings;

    try {
        // Attempt to process the license and initialize some component (represented by L())
        x(await k(settings.license, L()))
    } catch (error) {
        // Handle any errors during the process
        j(true)
    }

    // Check if the firmware data is not in the expected format
    if (typeof firmwareData === "object" && !(firmwareData instanceof ArrayBuffer)) {
        // Send a command to switch to Base64 if the condition is met
        t.write({
            command: "switchToBase64"
        });
        return;
    }

    // Processing chip settings if available
    for (let chip of chipSettings || []) {
        e.addFile(`${chip.name}.chip.json`, JSON.stringify(chip.json), true);
    }

    // Adding diagram and configuring firmware data
    e.addFile("diagram.json", diagramJSON, true),
        r.overrideHex = isFirmwareBase64 ? (0, A.Xs)(firmwareData).buffer : firmwareData,
        r.wifiGateway = shouldUseGateway ? i : undefined,
        e.setChips(chipSettings),

        // Handling chip output
        r.onChipOutput = (chipName, message) => {
            t.write({
                command: "chipOutput",
                chipName: chipName,
                message: message
            })
        },

        // Setting up the current state
        q.current = {
            diagram: e.diagram,
            files: [],
            chips: chipSettings,
            autoPause: shouldPause,
            hideSerialMonitor: shouldDisableSerialMonitor
        },

        // Starting the process and handling completion
        r.start(q.current).then(() => {
            let wifiStatus;
            g(false),
                wifiStatus = r.wifi?.status;
            wifiStatus?.setGatewayType("vscode")
        })
});
```

{collapsible="true"}