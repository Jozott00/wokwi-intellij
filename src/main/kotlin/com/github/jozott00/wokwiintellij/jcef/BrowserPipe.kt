package com.github.jozott00.wokwiintellij.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * The `BrowserPipe` interface represents a pipe for communication between a browser and its subscribers.
 * It provides methods for sending messages, subscribing to receive messages, and removing subscribers.
 *
 * This interface extends the `Disposable` interface, which means it can be disposed to release any resources
 * it may be holding.
 */
interface BrowserPipe : Disposable {

    fun send(type: String, data: String)


    fun subscribe(type: String, subscriber: Subscriber)

    fun subscribe(type: String, subscriber: Subscriber, parent: Disposable) {
        Disposer.register(parent) { removeSubscriber(type, subscriber) }
        subscribe(type, subscriber)
    }


    fun removeSubscriber(type: String, subscriber: Subscriber)

    interface Subscriber {
        fun messageReceived(data: String): Boolean
    }

}