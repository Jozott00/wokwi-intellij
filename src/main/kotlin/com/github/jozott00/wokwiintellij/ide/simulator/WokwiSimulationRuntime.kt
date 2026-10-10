package com.github.jozott00.wokwiintellij.ide.simulator

import com.github.jozott00.wokwiintellij.core.model.SimulationConfig
import com.github.jozott00.wokwiintellij.core.session.WokwiSession
import com.github.jozott00.wokwiintellij.extensions.disposeByDisposer
import com.github.jozott00.wokwiintellij.ui.jcef.JcefWokwiView
import com.intellij.openapi.Disposable
import javax.swing.JComponent

/** One owned browser/session pair, replaceable by a fake in lifecycle tests. */
interface SimulationRuntime : Disposable {
    val component: JComponent
    val session: WokwiSession
    var simulationConfig: SimulationConfig
}

/**
 * Runtime objects for one active simulator view/session pair.
 *
 * The controller keeps a single instance while a simulator is running. Runtime disposal stops the core session and
 * releases the JCEF view through IntelliJ's disposer.
 *
 * @property view browser view hosting the Wokwi frontend.
 * @property session core simulator session connected to the browser transport.
 * @property simulationConfig latest simulation config used by this runtime, updated on firmware reloads.
 */
class WokwiSimulationRuntime(
    val view: JcefWokwiView,
    override val session: WokwiSession,
    override var simulationConfig: SimulationConfig,
) : SimulationRuntime {
    override val component: JComponent get() = view.component

    override fun dispose() {
        session.dispose()
        view.disposeByDisposer()
    }
}
