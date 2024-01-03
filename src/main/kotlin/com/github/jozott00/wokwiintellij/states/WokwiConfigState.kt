package com.github.jozott00.wokwiintellij.states

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "WokwiConfigModel")
data class WokwiConfigState(
    var espDevice: ESPDevice = ESPDevice.ESP32,
    var flashSize: FlashSize = FlashSize._4MB,
    var elfPath: String = "",
    var watchElf: Boolean = true,
) : PersistentStateComponent<WokwiConfigState> {

    override fun getState(): WokwiConfigState {
        return this
    }

    override fun loadState(state: WokwiConfigState) {
        XmlSerializerUtil.copyBean(state, this)
    }
}

enum class ESPDevice {
    ESP32,
    ESP32s2,
    ESP32s3,
    ESP32c3,
    ESP32c6;
}

enum class FlashSize {
    _2MB,
    _4MB,
    _8MB,
    _16MB,
    _32MB;

    override fun toString(): String {
        return name.removePrefix("_").removeSuffix("MB") + " MB"
    }
}