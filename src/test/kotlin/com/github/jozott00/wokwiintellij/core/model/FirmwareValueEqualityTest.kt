package com.github.jozott00.wokwiintellij.core.model

import com.github.jozott00.wokwiintellij.core.firmware.FirmwarePackResult
import com.github.jozott00.wokwiintellij.core.session.WokwiSessionStartConfig
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FirmwareValueEqualityTest {
    private val path = Path.of("firmware.bin")
    private val chip = CustomChip("chip", "AQ==", JsonObject(emptyMap()))

    @Test
    fun firmwareImagesCompareBytesAndMetadata() {
        val image = FirmwareImage(byteArrayOf(1, 2), FirmwareFormat.BIN, path, false, 2u, listOf(path))
        val equal = image.copy(buffer = image.buffer.copyOf())
        assertEquals(image, equal)
        assertEquals(image.hashCode(), equal.hashCode())
        assertTrue(equal in hashSetOf(image))

        listOf(
            image.copy(buffer = byteArrayOf(1, 3)),
            image.copy(format = FirmwareFormat.HEX),
            image.copy(rootPath = Path.of("other.bin")),
            image.copy(isFlasherFile = true),
            image.copy(size = 3u),
            image.copy(watchPaths = emptyList()),
        ).forEach { assertNotEquals(image, it) }
    }

    @Test
    fun packedFirmwareComparesBytesAndWatchPaths() {
        val packed = FirmwarePackResult.Success(byteArrayOf(1, 2), listOf(path))
        val equal = packed.copy(image = packed.image.copyOf())
        assertEquals(packed, equal)
        assertEquals(packed.hashCode(), equal.hashCode())
        assertTrue(equal in hashSetOf(packed))
        assertNotEquals(packed, packed.copy(image = byteArrayOf(1, 3)))
        assertNotEquals(packed, packed.copy(watchPaths = emptyList()))
    }

    @Test
    fun sessionConfigsCompareBytesAndStartupOptions() {
        val config = WokwiSessionStartConfig("license", "{}", byteArrayOf(1, 2), "bin", false)
        val equal = config.copy(firmware = config.firmware.copyOf())
        assertEquals(config, equal)
        assertEquals(config.hashCode(), equal.hashCode())
        assertTrue(equal in hashSetOf(config))

        listOf(
            config.copy(license = "other"),
            config.copy(diagram = "{\"parts\":[]}"),
            config.copy(firmware = byteArrayOf(1, 3)),
            config.copy(firmwareFormat = "hex"),
            config.copy(waitForDebugger = true),
            config.copy(gdbPort = 3333),
            config.copy(customChips = listOf(chip)),
        ).forEach { assertNotEquals(config, it) }

        val debugConfig = config.copy(gdbPort = 3333, customChips = listOf(chip))
        val equalDebugConfig = debugConfig.copy(firmware = debugConfig.firmware.copyOf())
        assertEquals(debugConfig, equalDebugConfig)
        assertEquals(debugConfig.hashCode(), equalDebugConfig.hashCode())
    }
}
