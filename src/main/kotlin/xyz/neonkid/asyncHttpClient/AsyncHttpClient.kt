package xyz.neonkid.asyncHttpClient

import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.function.Consumer
import kotlin.jvm.Throws

class AsyncHttpClient private constructor (
    val httpChannelGroup: AsynchronousChannelGroup,
) : AutoCloseable {
    companion object {
        fun create(httpChannelGroup: AsynchronousChannelGroup): AsyncHttpClient = AsyncHttpClient(httpChannelGroup)
    }

    @Throws(URISyntaxException::class, IOException::class)
    fun get (
        url: String,
        headers: String,
        success: Consumer<in ByteBuffer>,
        failure: Consumer<in Exception>,
    ) {
        process(url, null, headers, success, failure)
    }

    @Throws(URISyntaxException::class, IOException::class)
    fun post (
        url: String,
        data: String,
        headers: String,
        success: Consumer<in ByteBuffer>,
        failure: Consumer<in Exception>,
    ) {
        process(url, ByteBuffer.wrap(data.toByteArray()), headers, success, failure)
    }

    fun process (
        url: String,
        data: ByteBuffer?,
        headers: String,
        success: Consumer<in ByteBuffer>,
        failure: Consumer<in Exception>,
    ) {
        if (url.isEmpty())
            return

        val uri = URI(url)
        val serverAddress = InetSocketAddress(uri.host, uri.port)
        val handler = RequestHandler(AsynchronousSocketChannel.open(httpChannelGroup), success, failure)

        doConnect(uri, handler, serverAddress, ByteBuffer.wrap(createRequestHeaders(headers, uri).toByteArray()), data)
    }

    fun doConnect (
        uri: URI,
        handler: RequestHandler,
        address: SocketAddress,
        headers: ByteBuffer,
        body: ByteBuffer?,
    ) {
        handler.channel.connect(address, null, object: CompletionHandler<Void?, Void?> {
            override fun completed(result: Void?, attachment: Void?) {
                handler.headers(headers, body)
            }

            override fun failed(exc: Throwable?, attachment: Void?) {
                handler.failure.accept(Exception(exc))
            }
        })
    }

    fun createRequestHeaders (
        headers: String,
        uri: URI,
    ): String = "$headers Host: ${uri.host} \r\n\r\n"

    override fun close() {
        this.httpChannelGroup.shutdown()
    }
}