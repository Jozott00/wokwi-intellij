package com.github.jozott00.wokwiintellij.execution.processHandler

import com.github.jozott00.wokwiintellij.simulator.WokwiSimulatorListener
import com.intellij.execution.process.ProcessHandler

abstract class WokwiProcessHandler : ProcessHandler(), WokwiSimulatorListener

