package app.orchard.tv.tasks

import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque

/** Minimal legacy ADB transport for this TV's loopback adbd, never a remote host. */
internal class LocalAdbTransport internal constructor(private val socket: Socket) : Closeable {
    private val input = DataInputStream(socket.getInputStream())
    private val output = socket.getOutputStream()
    private val deadline = System.nanoTime() + 30_000_000_000L
    private data class Message(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    init { socket.soTimeout = 4000 }

    fun handshake() {
        send("CNXN", 0x01000000, 4096, "host::\u0000".toByteArray())
        val reply = receive()
        if (reply.command == code("AUTH")) throw IOException("Local ADB requires authorization; no settings changed")
        checkIo(reply.command == code("CNXN") && reply.arg1 >= 4096, "Unexpected ADB handshake")
    }

    private fun send(command: String, arg0: Int, arg1: Int, bytes: ByteArray = byteArrayOf()) {
        val cmd = code(command)
        output.write(ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(cmd).putInt(arg0).putInt(arg1).putInt(bytes.size)
            .putInt(bytes.sumOf { it.toInt() and 255 }).putInt(cmd.inv()).array())
        output.write(bytes)
        output.flush()
    }

    private fun receive(): Message {
        checkIo(System.nanoTime() < deadline, "ADB deadline exceeded")
        val header = ByteArray(24).also(input::readFully)
        val words = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = words.int; val a = words.int; val b = words.int
        val size = words.int; val checksum = words.int; val magic = words.int
        checkIo(magic == cmd.inv() && size in 0..1_048_576, "Invalid ADB frame")
        val payload = ByteArray(size).also(input::readFully)
        checkIo(payload.sumOf { it.toInt() and 255 } == checksum, "Invalid ADB checksum")
        return Message(cmd, a, b, payload)
    }

    private inner class Stream(service: String) {
        private val local = 1
        private var remote = 0
        private var closed = false
        private val pending = ArrayDeque<ByteArray>()
        private var current = byteArrayOf()
        private var offset = 0
        init {
            send("OPEN", local, 0, (service + "\u0000").toByteArray())
            val reply = receive()
            checkIo(reply.command == code("OKAY") && reply.arg1 == local, "ADB service rejected")
            remote = reply.arg0
        }
        private fun accept(reply: Message) {
            checkIo(reply.arg1 == local && reply.arg0 == remote, "Unexpected ADB stream")
            when (reply.command) {
                code("WRTE") -> { pending.add(reply.payload); send("OKAY", local, remote) }
                code("CLSE") -> { closed = true; send("CLSE", local, remote) }
                code("OKAY") -> Unit
                else -> throw IOException("Unexpected ADB stream frame")
            }
        }
        fun write(bytes: ByteArray) {
            var at = 0
            while (at < bytes.size) {
                checkIo(!closed, "ADB stream closed")
                val end = minOf(at + 4096, bytes.size)
                send("WRTE", local, remote, bytes.copyOfRange(at, end))
                while (true) {
                    val reply = receive(); accept(reply)
                    checkIo(!closed, "ADB stream closed during write")
                    if (reply.command == code("OKAY")) break
                }
                at = end
            }
        }
        fun readByte(): Int {
            while (offset >= current.size) {
                if (pending.isNotEmpty()) { current = pending.removeFirst(); offset = 0 }
                else if (closed) return -1
                else accept(receive())
            }
            return current[offset++].toInt() and 255
        }
        fun read(count: Int): ByteArray = ByteArray(count) {
            readByte().also { if (it < 0) throw EOFException("ADB stream ended") }.toByte()
        }
    }

    fun shell(command: String): String {
        val stream = Stream("shell,raw:$command")
        val result = java.io.ByteArrayOutputStream()
        while (true) {
            val value = stream.readByte()
            if (value < 0) break
            checkIo(result.size() < 65_536, "ADB shell response too large")
            result.write(value)
        }
        return result.toString("UTF-8")
    }

    fun push(path: String, contents: ByteArray) {
        checkIo(contents.size <= 1_048_576, "Helper asset too large")
        val stream = Stream("sync:")
        fun sync(id: String, length: Int) = id.toByteArray(Charsets.US_ASCII) +
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(length).array()
        val destination = "$path,33152".toByteArray()
        stream.write(sync("SEND", destination.size) + destination)
        contents.asList().chunked(4096).forEach { chunk ->
            val bytes = chunk.toByteArray()
            stream.write(sync("DATA", bytes.size) + bytes)
        }
        stream.write(sync("DONE", (System.currentTimeMillis() / 1000).toInt()))
        val response = stream.read(8)
        val id = String(response, 0, 4, Charsets.US_ASCII)
        checkIo(id == "OKAY", "ADB helper upload rejected")
    }

    override fun close() = socket.close()
    companion object {
        fun connect(): LocalAdbTransport {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 5555), 3000)
                return LocalAdbTransport(socket).also { it.handshake() }
            } catch (e: Exception) { socket.close(); throw e }
        }
        private fun code(text: String) = ByteBuffer.wrap(text.toByteArray(Charsets.US_ASCII)).order(ByteOrder.LITTLE_ENDIAN).int
        private fun checkIo(condition: Boolean, message: String) { if (!condition) throw IOException(message) }
    }
}
