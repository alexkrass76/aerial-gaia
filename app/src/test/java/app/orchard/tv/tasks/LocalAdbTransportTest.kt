package app.orchard.tv.tasks

import java.io.DataInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class LocalAdbTransportTest {
    private data class Frame(val id: String, val a: Int, val b: Int, val data: ByteArray)
    private fun word(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun read(s: Socket): Frame {
        val input = DataInputStream(s.getInputStream())
        val header = ByteArray(24).also(input::readFully)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val id = ByteArray(4).also { b.get(it) }.toString(Charsets.US_ASCII)
        val a = b.int; val remote = b.int; val size = b.int
        return Frame(id, a, remote, ByteArray(size).also(input::readFully))
    }
    private fun send(s: Socket, id: String, a: Int = 0, b: Int = 0, data: ByteArray = byteArrayOf(), corrupt: Boolean = false) {
        val command = ByteBuffer.wrap(id.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).int
        val bytes = id.toByteArray() + word(a) + word(b) + word(data.size) +
            word(data.sumOf { it.toInt() and 255 } + if (corrupt) 1 else 0) + word(command.inv()) + data
        // Fragment even the header to exercise readFully, as real TCP is a byte stream.
        bytes.forEach { s.getOutputStream().write(it.toInt()); s.getOutputStream().flush() }
    }
    private fun peer(server: (Socket) -> Unit, client: (LocalAdbTransport) -> Unit) {
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            val future = executor.submit { listener.accept().use { it.soTimeout = 3000; server(it) } }
            try {
                LocalAdbTransport(Socket(java.net.InetAddress.getLoopbackAddress(), listener.localPort)).use(client)
                future.get(5, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }
    private fun connect(s: Socket) { assertEquals("CNXN", read(s).id); send(s, "CNXN", 0x01000000, 4096) }

    @Test fun refusesAuthenticationWithoutSendingKeysOrCommands() = peer({ s ->
        read(s); send(s, "AUTH", 1, 0, ByteArray(20))
    }, { client -> assertTrue(assertThrows(IOException::class.java) { client.handshake() }.message!!.contains("authorization")) })

    @Test fun rejectsCorruptedPayload() = peer({ s ->
        read(s); send(s, "CNXN", 0x01000000, 4096, byteArrayOf(1), corrupt = true)
    }, { client -> assertThrows(IOException::class.java) { client.handshake() } })

    @Test fun rejectsOversizedFrameBeforeAllocatingPayload() = peer({ s ->
        read(s)
        val command = ByteBuffer.wrap("CNXN".toByteArray()).order(ByteOrder.LITTLE_ENDIAN).int
        s.getOutputStream().write("CNXN".toByteArray() + word(0) + word(4096) + word(Int.MAX_VALUE) + word(0) + word(command.inv()))
    }, { client -> assertThrows(IOException::class.java) { client.handshake() } })

    @Test fun readsFragmentedShellAndAcknowledgesEveryPacket() = peer({ s ->
        connect(s); assertEquals("shell,raw:echo test\u0000", read(s).data.toString(Charsets.UTF_8))
        send(s, "OKAY", 7, 1)
        for (part in listOf("hel", "lo")) { send(s, "WRTE", 7, 1, part.toByteArray()); assertEquals("OKAY", read(s).id) }
        send(s, "CLSE", 7, 1); assertEquals("CLSE", read(s).id)
    }, { client -> client.handshake(); assertEquals("hello", client.shell("echo test")) })

    @Test fun pushesBinaryHelperUsingSyncWithTransportChunking() {
        val contents = ByteArray(9000) { (it % 251).toByte() }
        peer({ s ->
            connect(s); assertEquals("sync:\u0000", read(s).data.toString(Charsets.UTF_8)); send(s, "OKAY", 7, 1)
            val received = java.io.ByteArrayOutputStream()
            var done = false
            while (!done) {
                val frame = read(s); assertEquals("WRTE", frame.id); assertTrue(frame.data.size <= 4096)
                received.write(frame.data); send(s, "OKAY", 7, 1)
                val all = received.toByteArray()
                var at = 0
                while (at + 8 <= all.size) {
                    val id = String(all, at, 4, Charsets.US_ASCII)
                    val length = ByteBuffer.wrap(all, at + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    if (id == "DONE") { done = true; break }
                    if (at + 8 + length > all.size) break
                    at += 8 + length
                }
            }
            val buffer = ByteBuffer.wrap(received.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
            val uploaded = java.io.ByteArrayOutputStream()
            while (buffer.remaining() >= 8) {
                val id = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
                val count = buffer.int
                if (id == "DONE") break
                val data = ByteArray(count).also { buffer.get(it) }
                if (id == "DATA") uploaded.write(data)
            }
            assertArrayEquals(contents, uploaded.toByteArray())
            send(s, "WRTE", 7, 1, "OKAY".toByteArray() + word(0)); assertEquals("OKAY", read(s).id)
        }, { client -> client.handshake(); client.push("/data/local/tmp/test.dex", contents) })
    }
}
