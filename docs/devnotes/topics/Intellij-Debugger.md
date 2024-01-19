# Intellij Debugger

Research results regarding the Intellij platform debuggers.

**Intellij Community Classes**
- [XDebugProcess Abstract Class](https://github.com/JetBrains/intellij-community/blob/master/platform/xdebugger-api/src/com/intellij/xdebugger/XDebugProcess.java#L37)
  provides debugging capabilities for a custom language/framework


## Python Intellij Debugger
[Source Directory](https://github.com/JetBrains/intellij-community/tree/master/python/src/com/jetbrains/python/debugger)

The [PyRemoteDebugProcess](https://github.com/JetBrains/intellij-community/blob/master/python/src/com/jetbrains/python/debugger/PyRemoteDebugProcess.java) 
and its [PyDebugProcess](https://github.com/JetBrains/intellij-community/blob/master/python/src/com/jetbrains/python/debugger/PyDebugProcess.java) might be 
a good starting point to implement a debug process to attach to Wokwi's GDB stub.

For the Runner take a look at the [Execution Documentation](https://plugins.jetbrains.com/docs/intellij/execution.html)