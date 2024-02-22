package com.github.jozott00.wokwiintellij.utils

import com.intellij.openapi.application.ApplicationManager

fun runInBackground(task: () -> Unit) {
    ApplicationManager.getApplication().executeOnPooledThread(task)
}