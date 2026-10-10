package com.github.jozott00.wokwiintellij.ide.execution.processHandler

import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.ide.simulator.SimExitCode
import com.intellij.execution.process.ProcessHandler

abstract class WokwiProcessHandler : ProcessHandler(), WokwiSession.Listener {
    open fun onShutdown(exitCode: SimExitCode) {}
}
