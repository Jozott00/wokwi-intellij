package com.github.jozott00.wokwiintellij.core.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.exceptions.TomlDecodingException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString

object WokwiTomlParser {
    private val toml = Toml(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = true,
            allowNullValues = true,
        )
    )

    fun parse(content: String): WokwiTomlParseResult {
        return try {
            WokwiTomlParseResult.Success(toml.decodeFromString<WokwiTomlConfig>(content))
        } catch (e: TomlDecodingException) {
            WokwiTomlParseResult.Failure(e.message ?: "Unable to decode wokwi.toml")
        } catch (e: SerializationException) {
            WokwiTomlParseResult.Failure(e.message ?: "Unable to decode wokwi.toml")
        }
    }
}

sealed interface WokwiTomlParseResult {
    data class Success(val config: WokwiTomlConfig) : WokwiTomlParseResult
    data class Failure(val message: String) : WokwiTomlParseResult
}
