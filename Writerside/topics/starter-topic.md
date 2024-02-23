# Wokwi Intellij Plugin Guide

The Wokwi Intellij plugin, an open-source tool, integrates the [Wokwi](https://wokwi.com) simulator with Jetbrains IDEs like CLion and RustRover.
It adopts the Wokwi VS Code extension's configuration approach for seamless IDE transitions, supporting the same platforms.

> This plugin is a community plugin and not maintained by the [Wokwi](https://wokwi.com) team.

## Installation {id="installation"}

> To run the embedded simulator, a Wokwi community license is required. 
> The license is free for open source projects but requires a Wokwi account.
> 
> Sign up on [wokwi.com](https://wokwi.com)


<procedure title="Install and Setup Wokwi Intellij">
<step>
Install the Wokwi Intellij plugin from Jetbrains' marketplace.
</step>
<step>
Click on the <img src="pluginIcon.svg" alt="Wokwi"/> icon to open the Wokwi toolwindow
</step>
<step>
Click <b>Activate License</b>. This will open a dialog window where you can enter your license key.
<img src="activate_license_dialog.png" alt="Activate License Dialog" width="500"/>
</step>
<step>
Click <b>Open wokwi.com</b> to open the browser. Copy your license key and paste it into the dialog window.

> If the browser tries to open VS Code, abort it.
> {style="warning"}
</step>
<step>
Click <b>Ok</b> to save the license key.
</step>
</procedure>

## First Project

The Wokwi documentation has a rich list
of [example projects](https://docs.wokwi.com/vscode/getting-started#example-projects).
Checkout the next page to get started with a custom Wokwi project.

<seealso>
    <category ref="wd">
        <a href="https://docs.wokwi.com/">Wokwi Getting Started</a>
        <a href="https://docs.wokwi.com/vscode">Wokwi VS Code Docs</a>
        <a href="https://wokwi.com/">Wokwi Simulator</a>
    </category>
</seealso>