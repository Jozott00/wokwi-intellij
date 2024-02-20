# Debugging

> Debugging is currently only possible with the **CLion IDE**.
> {style="warning"}

<procedure title="Create Wokwi Debug Configuration">
<step>
Create a new <b>Remote Debug</b> configuration
</step>
<step>
For the <b>Debugger</b> set the GDB executable for your target platform.

E.g. the `xtensa-esp32s3-elf-gdb` executable is suitable to debug esp32s3 target
programs.

> You may download ESP GDB binaries from
> their [binutils-gdb releases](https://github.com/espressif/binutils-gdb/releases/tag/esp-gdb-v12.1_20231023).
</step>

<step>

For the <b>'target remote' args</b> enter `$WokwiGdbServer$` to the field.

Alternatively click on the **＋** icon and select the `WokwiGdbServer` macro.
This will automatically insert the correct GDB server address, specified
in the `wokwi.toml`.
</step>

<step>

In the **Symbol file** field enter `$WokwiElfPath$` or select it from the
macro list by clicking on the **＋** icon.

This will set the debug symbol file path to the ELF path defined in the
`wokwi.toml`.
</step>

<step>

Under the **Before launch** task list, click on the **＋** icon. From the list
select the `Start Wokwi Debug` task.

<img src="select_start_debug_task.png" alt="Select Start Wokwi Debug" width="250"/>

</step>

<step>

Click **Apply** to safe the configuration. When running the configuration, Wokwi is started in debug mode,
starts a GDB server and waits for some client to connect. Then the debugging client is started and
connects to the GDB server which starts the program execution.

<img src="wokwi_debug_config.png" alt="Select Start Wokwi Debug" />

</step>

</procedure>

## Limitations

Because the **Remote Debug** configuration is only available in CLion,
it is not possible to debug Wokwi simulations in other IDEs such as Intellij
or RustRover. However, it is most likely that RustRover will get a similar
configuration once it is out of EAP.