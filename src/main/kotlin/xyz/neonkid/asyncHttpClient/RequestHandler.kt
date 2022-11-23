package xyz.neonkid.asyncHttpClient

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.function.Consumer
import kotlin.Exception

class RequestHandler (
    val channel: AsynchronousSocketChannel,
    val success: Consumer<in ByteBuffer>,
    val failure: Consumer<in Exception>,
) {
    fun closeChannel() {
        try {
            channel.close()
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }

    fun headers(headers: ByteBuffer, body: ByteBuffer?) {
        this.channel.write(headers, this, object: CompletionHandler<Int, RequestHandler> {
            override fun completed(result: Int?, attachment: RequestHandler) {
                if (headers.hasRemaining()) channel.write(headers, attachment, this)
                else if (body != null) body(body, attachment)
                else response()
            }

            override fun failed(exc: Throwable?, attachment: RequestHandler) {
                attachment.failure.accept(Exception(exc))
                closeChannel()
            }
        })
    }

    fun body(body: ByteBuffer, handler: RequestHandler) {
        this.channel.write(body, handler, object: CompletionHandler<Int, RequestHandler> {
            override fun completed(result: Int?, attachment: RequestHandler) {
                if (body.hasRemaining()) channel.write(body, attachment, this)
                else response()
            }

            override fun failed(exc: Throwable?, attachment: RequestHandler?) {
                handler.failure.accept(Exception(exc))
                closeChannel()
            }
        })
    }

    fun response() {
        val buffer = ByteBuffer.allocate(2048)
        this.channel.read(buffer, this, object: CompletionHandler<Int, RequestHandler> {
            override fun completed(result: Int, attachment: RequestHandler) {
                if (result > 0) {
                    attachment.success.accept(buffer)
                    buffer.clear()

                    channel.read(buffer, attachment,this)
                } else if (result < 0) closeChannel()
                else channel.read(buffer, attachment, this)
            }

            override fun failed(exc: Throwable?, attachment: RequestHandler) {
                attachment.failure.accept(Exception(exc))
                closeChannel()
            }
        })
    }
}