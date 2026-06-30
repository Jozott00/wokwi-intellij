package com.github.jozott00.wokwiintellij.simulator

import com.github.jozott00.wokwiintellij.core.protocol.InboundDecodeResult
import com.github.jozott00.wokwiintellij.core.protocol.InboundMessage
import com.github.jozott00.wokwiintellij.core.protocol.OutboundMessage
import com.github.jozott00.wokwiintellij.core.protocol.ProtocolCodec
import com.github.jozott00.wokwiintellij.jcef.BrowserPipe
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgsFirmware
import com.github.jozott00.wokwiintellij.simulator.gdb.GDBServerCommunicator
import com.github.jozott00.wokwiintellij.simulator.gdb.GDBServerEvent
import com.github.jozott00.wokwiintellij.ui.jcef.SimulatorJCEFHtmlPanel
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.containers.ContainerUtil
import io.ktor.util.*
import javax.swing.JComponent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val LOG = logger<WokwiSimulator>()

/**
 * Represents the Wokwi simulator, encapsulating the simulation's lifecycle, communication, and interaction
 * with the embedded web browser component. It implements the [Simulator], [ComponentContainer], and
 * [BrowserPipe.Subscriber] interfaces to provide comprehensive control and feedback mechanisms for the simulation.
 *
 * @property runArgs The arguments to run the simulation with, including firmware and diagram information.
 * @param parentDisposable The parent disposable for managing the lifecycle of this instance.
 */
class WokwiSimulator(
  private val runArgs: WokwiArgs,
  parentDisposable: Disposable,
) : Disposable,
  Simulator,
  ComponentContainer,
  BrowserPipe.Subscriber {

  private var browserReady = false
  private var startInvoked = false

  private val browser = SimulatorJCEFHtmlPanel(this)
  private val browserPipe = browser.browserPipe

  private val myEventMulticaster = createEventMulticaster()
  private val myListeners: MutableList<WokwiSimulatorListener> = ContainerUtil.createLockFreeCopyOnWriteList()

  private val ansiEscapeDecoder = AnsiEscapeDecoder()
  private var gdbServer: GDBServerCommunicator? = null

  private var simulationRunning = false

  init {
    Disposer.register(parentDisposable, this)
    browserPipe.subscribe(PIPE_TOPIC, this, this)
  }

  /**
   * Starts the simulation. If the browser component is not ready or the start has not been invoked,
   * it will delay the actual start of the simulation until these conditions are met.
   */
  override fun start() {
    startInvoked = true
    startInternal()
  }

  /**
   * Internal method to handle the actual starting of the simulation, sending the necessary commands
   * to the browser component.
   */
  private fun startInternal() {
    simulationRunning = false

    // if browser not yet ready just return
    if (!browserReady) return
    if (!startInvoked) return

    LOG.info("(Re)starting simulation...")

    @OptIn(ExperimentalEncodingApi::class)
    val firmwareString = Base64.encode(runArgs.firmware.buffer)

    val cmd = ProtocolCodec.encode(
      OutboundMessage.SimulatorStart(
        diagram = runArgs.diagram,
        firmware = firmwareString,
        firmwareFormat = runArgs.firmware.format.toString(),
        license = runArgs.license,
        pause = runArgs.waitForDebugger,
      )
    )
    browserPipe.send(PIPE_TOPIC, cmd)
    myEventMulticaster.onStarted(runArgs)
  }

  /**
   * Updates the firmware used in the simulation.
   *
   * @param firmware The new firmware to be used for the simulation.
   */
  override fun setFirmware(firmware: WokwiArgsFirmware) {
    runArgs.firmware = firmware
  }

  /**
   * Retrieves the current firmware used in the simulation.
   *
   * @return The current [WokwiArgsFirmware].
   */
  override fun getFirmware(): WokwiArgsFirmware {
    return runArgs.firmware
  }

  /**
   * Establishes a connection to a GDB server for debugging purposes, handling the communication
   * between the simulator and the GDB server.
   *
   * @param server The [GDBServerCommunicator] to connect to.
   */
  override suspend fun connectToGDBServer(server: GDBServerCommunicator) {
    gdbServer = server
    server.getMessageFlow().collect { event ->
      when (event) {
        is GDBServerEvent.Connected -> {}
        is GDBServerEvent.Error -> LOG.error("Error: ${event.error}")
        is GDBServerEvent.Message -> {
          val cmd = ProtocolCodec.encode(OutboundMessage.Gdb(message = event.message))
          browserPipe.send(PIPE_TOPIC, cmd)
        }

        is GDBServerEvent.Break -> {
          val cmd = ProtocolCodec.encode(OutboundMessage.GdbBreak())
          browserPipe.send(PIPE_TOPIC, cmd)
        }
      }
    }
  }

  /**
   * Called when start command is received. Indicates that the browser is ready
   * and calls [startInternal].
   */
  private fun startRecv() {
    browserReady = true
    startInternal()
  }

  /**
   * When uart data is received from the simulation, it gets
   * decoded to UTF8, converted to the correct content-type (by the [AnsiEscapeDecoder])
   * and then shared using the [myEventMulticaster].
   */
  private fun uartDataRecv(message: InboundMessage.UartData) {
    val bytes = message.toByteArray()
    if (bytes.isEmpty()) return

    val str = String(bytes, Charsets.UTF_8)

    ansiEscapeDecoder.escapeText(str, ProcessOutputTypes.STDOUT) { t, contentType ->
      myEventMulticaster.onTextAvailable(t, contentType)
    }
  }

  /**
   * Loads the requested resource (currently from the internet) and
   * sends it to the simulation.
   */
  private fun loadResourceRecv(message: InboundMessage.LoadResource) {
    // TODO: Make this offline
    val url = java.net.URI(message.url).toURL()
    val resource = url.readBytes().encodeBase64()
    val cmd = ProtocolCodec.encode(OutboundMessage.ResourceData(buffer = resource))
    browserPipe.send(PIPE_TOPIC, cmd)

    checkSimulationStartedRunning()
  }

  /**
   * Sends the received GDB response to the gdbServer.
   */
  private fun gdbResponseRecv(message: InboundMessage.GdbResponse) {
    gdbServer?.sendResponse(message.response)
  }

  /**
   * Receives messages from the browser component and handles them according to their type.
   *
   * @param data The message data received.
   * @return `true` if the message was processed successfully, `false` otherwise.
   */
  override fun messageReceived(data: String): Boolean {
    when (val result = ProtocolCodec.decode(data)) {
      InboundDecodeResult.Empty -> return true
      is InboundDecodeResult.Malformed -> {
        LOG.error("Malformed Wokwi message: ${result.reason}\n${result.raw}", Throwable())
        return false
      }
      is InboundDecodeResult.Decoded -> return handleIncomingMessage(result.message)
    }
  }

  private fun handleIncomingMessage(message: InboundMessage): Boolean {
    when (message) {
      is InboundMessage.Ready -> startRecv()
      is InboundMessage.LoadResource -> loadResourceRecv(message)
      is InboundMessage.UartData -> uartDataRecv(message)
      is InboundMessage.WifiConnect, is InboundMessage.WifiFrame -> {
        TODO("Not yet implemented")
      }
      is InboundMessage.GdbResponse -> gdbResponseRecv(message)
      is InboundMessage.Unknown -> {
        LOG.warn("Unknown command: ${message.command}")
        LOG.debug("Unknown command data: ${message.raw}")
        return false
      }
    }

    return true
  }

  /**
   * If the [simulationRunning] is not yet true,
   * it becomes true and all [myListeners] are notified
   * that the simulation is running.
   */
  private fun checkSimulationStartedRunning() {
    if (!simulationRunning) {
      simulationRunning = true
      myEventMulticaster.onRunning()
    }
  }

  companion object {
    private const val PIPE_TOPIC = "wokwi"
  }

  /**
   * Adds a listener to be notified of simulator events.
   *
   * @param listener The [WokwiSimulatorListener] to add.
   */
  fun addSimulatorListener(listener: WokwiSimulatorListener) {
    myListeners.add(listener)
  }

  /**
   * Disposes of the resources used by this simulator instance, notifying listeners of the shutdown.
   */
  override fun dispose() {
    createEventMulticaster().onShutdown(SimExitCode.OK)
    myListeners.clear()
  }

  /**
   * Retrieves the main component of the simulator, typically the browser view.
   *
   * @return The simulator's main [JComponent].
   */
  override fun getComponent(): JComponent = browser.component

  /**
   * Retrieves the component that should receive focus when the simulator is displayed.
   *
   * @return The [JComponent] that should be focused.
   */
  override fun getPreferredFocusableComponent() = component


  /**
   * Creates an event multicaster for dispatching simulator events to listeners.
   *
   * @return A [WokwiSimulatorListener] that multicasts events to all registered listeners.
   */
  private fun createEventMulticaster(): WokwiSimulatorListener {
    return object : WokwiSimulatorListener {
      override fun onStarted(runArgs: WokwiArgs) {
        notifyAll { it.onStarted(runArgs) }
      }

      override fun onShutdown(exitCode: SimExitCode) {
        notifyAll { it.onShutdown(exitCode) }
      }

      override fun onTextAvailable(text: String, outputType: Key<*>) {
        notifyAll { it.onTextAvailable(text, outputType) }
      }

      override fun onRunning() {
        notifyAll { it.onRunning() }
      }

      private fun notifyAll(m: (WokwiSimulatorListener) -> Unit) {
        for (l in myListeners) {
          m(l)
        }
      }

    }
  }

}
