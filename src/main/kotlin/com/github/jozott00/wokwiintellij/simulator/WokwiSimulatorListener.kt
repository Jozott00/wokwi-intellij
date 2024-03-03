package com.github.jozott00.wokwiintellij.simulator

import com.github.jozott00.wokwiintellij.simulator.args.WokwiArgs
import com.intellij.openapi.util.Key

interface WokwiSimulatorListener {
    fun onStarted(runArgs: WokwiArgs) {}
    fun onShutdown(exitCode: EXIT_CODE) {}
    fun onTextAvailable(text: String, outputType: Key<*>) {}
    fun onRunning() {}
}

enum class EXIT_CODE(val int: Int) {
    OK(0),
    CONFIG_ERROR(1),
}