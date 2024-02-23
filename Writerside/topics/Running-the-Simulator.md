# Running the Simulator

![Simulator Running Showcase](sim_running.png)

Clicking the **Start Simulator** button in the Wokwi tool window launches an embedded Wokwi simulator, loading the simulation context based on project configurations.

### Simulator Window
This window displays the circuit and offers controls to restart, stop, and pause the simulation.

The simulator window must be open upon starting the simulator; if it's closed, Wokwi won't load, preventing simulation start. This issue may arise if you hit the **Restart Simulator** button in the [Run Window](#run-window) after previously closing the Simulator.

> Once the simulator starts, you can hide the simulator window and still restart the simulator from the run window without needing to reopen the simulator window.

## Run Window

The Run Window becomes visible upon starting the simulator. It manages the simulator and displays simulation output in its console. 
The control panel enables the following actions:

{type="narrow"}
Restarting the simulator 
:  Initiates a (fast) restart of the simulator, triggering a firmware update.

Stopping the simulator
: Shuts down the simulator and related services (e.g., the  [GDB Server](Debugging.md)).

Toggling the Firmware-Watcher
: When activated, the plugin automatically restarts the simulation upon detecting a firmware update, 
ensuring the simulation runs with the latest firmware after each rebuild.

> Currently, writing to the console is not supported. For future updates, refer to [the roadmap](Roadmap.md)