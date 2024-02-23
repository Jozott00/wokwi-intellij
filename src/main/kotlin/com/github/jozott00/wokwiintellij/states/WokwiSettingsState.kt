package com.github.jozott00.wokwiintellij.states

import com.github.jozott00.wokwiintellij.WokwiConstants
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "WokwiProjectSettings")
data class WokwiSettingsState(
    var wokwiConfigPath: String = WokwiConstants.WOKWI_CONFIG_FILE,
    var wokwiDiagramPath: String = WokwiConstants.WOKWI_DIAGRAM_FILE,
    var watchFirmware: Boolean = true,
) : PersistentStateComponent<WokwiSettingsState> {

    override fun getState(): WokwiSettingsState {
        return this
    }

    override fun loadState(state: WokwiSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }
}