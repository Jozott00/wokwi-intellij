package com.github.jozott00.wokwiintellij.ui.jcef

import com.google.api.ResourceProto.resource
import com.intellij.openapi.application.ApplicationInfo
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.FileResponses.checkCache
import org.jetbrains.io.FileResponses.getContentType
import org.jetbrains.io.send
import java.util.*

class StaticRequestHandler : HttpRequestHandler() {

    override fun isSupported(request: FullHttpRequest): Boolean {
        if (!super.isSupported(request))
            return false
        val path = request.uri()
        return path.startsWith(pathPrefix)
    }

    private fun getStaticPath(path: String): String {
        return path.split('/').drop(2).joinToString(separator = "/")
    }

    override fun process(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): Boolean {
        val path = urlDecoder.path()
        check(path.startsWith(pathPrefix)) { "prefix should have been checked by #isSupported" }
        val resourceName = getStaticPath(path)
        if (!ResourceLoader.canLoadResource(this.javaClass, resourceName)) return false
        val resource = ResourceLoader.loadInternalResource(this.javaClass, resourceName, null)
        sendResource(request, context.channel(), resource, resourceName)
        return false
    }


    companion object {
        private const val pathPrefix = "/wokwiStatic"

        /**
         * The types for which `";charset=utf-8"` will be appended (only if guessed by [guessContentType]).
         */
        private val typesForExplicitUtfCharset = arrayOf(
            "application/javascript",
            "text/html",
            "text/css",
            "image/svg+xml"
        )


        private fun guessContentType(resourceName: String): String {
            val type = getContentType(resourceName)
            return if (type in typesForExplicitUtfCharset) {
                "$type; charset=utf-8"
            } else type
        }

        private fun sendResource(
            request: HttpRequest,
            channel: Channel,
            resource: ResourceLoader.Resource?,
            resourceName: String
        ) {
            val lastModified = ApplicationInfo.getInstance().buildDate.timeInMillis
            if (checkCache(request, channel, lastModified)) {
                return
            }
            if (resource == null) {
                HttpResponseStatus.NOT_FOUND.send(channel, request)
                return
            }
            val response = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(resource.content)
            )
            with(response) {
                headers()[HttpHeaderNames.CONTENT_TYPE] = when (val type = resource.type) {
                    null -> guessContentType(resourceName)
                    else -> type
                }
                headers()[HttpHeaderNames.CACHE_CONTROL] = "no-cache"
                headers()[HttpHeaderNames.LAST_MODIFIED] = Date(lastModified)
                send(channel, request)
            }
        }
    }
    

}