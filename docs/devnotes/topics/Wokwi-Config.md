# Wokwi Config

We use the TOML intellij plugin as foundation layer of Wokwi configs.

Wokwi project configuration code lives in the top-level `config` package. It is intentionally outside `core` because it
uses IntelliJ project APIs, virtual files, settings, notifications, and TOML PSI helpers. `core` only receives loaded
runtime data such as `SimulationConfig`.

> Take a look at the [Rust-Intellij extension of TOML](https://github.com/intellij-rust/intellij-rust/pull/1982/files)
> And
> the [current implementation](https://github.com/intellij-rust/intellij-rust/tree/master/src/main/kotlin/org/rust/toml)
