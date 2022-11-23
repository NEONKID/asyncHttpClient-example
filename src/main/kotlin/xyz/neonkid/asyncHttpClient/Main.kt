package xyz.neonkid.asyncHttpClient

import java.io.IOException
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.Channels
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import kotlin.jvm.Throws

@Throws(Exception::class)
fun doGet(url: Supplier<out String>, headers: Supplier<out String>) {
    val target = Channels.newChannel(System.out)
    val pass = AtomicBoolean(true)
    val latch = CountDownLatch(1)

    AsyncHttpClient.create(AsynchronousChannelGroup.withFixedThreadPool(2, Executors.defaultThreadFactory())).use {client ->
        client.get(url.get(), headers.get(), {buffer ->
            try {
                buffer.flip()
                while (buffer.hasRemaining())
                    target.write(buffer)
            } catch (ex: IOException) {
                pass.set(false)
            } finally {
                latch.countDown()
            }
        }) {ex ->
            ex.printStackTrace()
            pass.set(false)
            latch.countDown()
        }
    }

    latch.await()
}

fun main(args: Array<String>) {
    val HEADERS_TEMPLATE = "%s /%s HTTP/1.1\r\n" + "Accept: %s\r\n" + "Content-Length: %s\r\n" + "Content-Type: text/plain\r\n";

    doGet(
        {
            "http://localhost:8211"
        },
        {
            String.format(HEADERS_TEMPLATE, "GET", "_hcheck_hbn", "application/json", 0)
        }
    )
}