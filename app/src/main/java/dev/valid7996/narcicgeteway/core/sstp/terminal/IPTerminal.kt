package dev.valid7996.narcicgeteway.core.sstp.terminal

import android.os.ParcelFileDescriptor
import dev.valid7996.narcicgeteway.core.sstp.ControlMessage
import dev.valid7996.narcicgeteway.core.sstp.Result
import dev.valid7996.narcicgeteway.core.sstp.SharedBridge
import dev.valid7996.narcicgeteway.core.sstp.Where
import dev.valid7996.narcicgeteway.core.sstp.extension.toHexByteArray
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

internal class IPTerminal(private val bridge: SharedBridge) {
    private var fd: ParcelFileDescriptor? = null

    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null

    internal suspend fun initialize() {
        try {
            if (bridge.PPP_IPv4_ENABLED) {
                if (bridge.currentIPv4.contentEquals(ByteArray(4))) {
                    bridge.logError("Invalid IPv4 address received from server")
                    bridge.controlMailbox.send(ControlMessage(Where.IPv4, Result.ERR_INVALID_ADDRESS))
                    return
                }

                val clientIp = InetAddress.getByAddress(bridge.currentIPv4)
                bridge.builder.addAddress(clientIp, 32)
                bridge.log("Assigned IPv4 Address: ${clientIp.hostAddress}")

                var dnsAdded = false
                if (!bridge.currentProposedDNS.contentEquals(ByteArray(4))) {
                    runCatching {
                        val dns = InetAddress.getByAddress(bridge.currentProposedDNS)
                        bridge.builder.addDnsServer(dns)
                        bridge.log("Server DNS: ${dns.hostAddress}")
                        dnsAdded = true
                    }
                }

                bridge.fallbackDns.forEach { dnsStr ->
                    runCatching {
                        bridge.builder.addDnsServer(dnsStr)
                        dnsAdded = true
                    }
                }

                if (!dnsAdded) {
                    runCatching { bridge.builder.addDnsServer("1.1.1.1") }
                    runCatching { bridge.builder.addDnsServer("8.8.8.8") }
                }

                bridge.builder.addRoute("0.0.0.0", 0)
            }

            if (bridge.PPP_IPv6_ENABLED) {
                if (!bridge.currentIPv6.contentEquals(ByteArray(8))) {
                    ByteArray(16).also {
                        "FE80".toHexByteArray().copyInto(it)
                        ByteArray(6).copyInto(it, destinationOffset = 2)
                        bridge.currentIPv6.copyInto(it, destinationOffset = 8)
                        runCatching {
                            bridge.builder.addAddress(InetAddress.getByAddress(it), 64)
                            bridge.builder.addRoute("::", 0)
                        }
                    }
                }
            }

            bridge.configureBuilder(bridge.builder)

            bridge.builder.setMtu(bridge.PPP_MTU)
            bridge.builder.setBlocking(true)

            val established = bridge.builder.establish()
            if (established == null) {
                bridge.logError("VpnService.Builder.establish() returned null")
                bridge.controlMailbox.send(ControlMessage(Where.IP, Result.ERR_INVALID_ADDRESS))
                return
            }

            fd = established
            inputStream = FileInputStream(established.fileDescriptor)
            outputStream = FileOutputStream(established.fileDescriptor)

            bridge.log("Android TUN interface established successfully! MTU: ${bridge.PPP_MTU}")
            bridge.controlMailbox.send(ControlMessage(Where.IP, Result.PROCEEDED))
        } catch (e: Exception) {
            bridge.logError("IPTerminal initialization failed: ${e.message}")
            bridge.controlMailbox.send(ControlMessage(Where.IP, Result.ERR_INVALID_ADDRESS, e.message))
        }
    }

    internal fun getFd(): ParcelFileDescriptor? = fd

    internal fun readPacket(buffer: ByteBuffer) {
        val stream = inputStream ?: return
        buffer.clear()
        val read = stream.read(buffer.array(), 0, buffer.capacity())
        if (read > 0) {
            buffer.limit(read)
            buffer.position(0)
            bridge.txBytes.addAndGet(read.toLong())
        } else {
            buffer.limit(0)
        }
    }

    internal fun writePacket(start: Int, length: Int, buffer: ByteBuffer) {
        val stream = outputStream ?: return
        if (length > 0) {
            stream.write(buffer.array(), start, length)
            bridge.rxBytes.addAndGet(length.toLong())
        }
    }

    internal fun writePacket(buffer: ByteBuffer) {
        writePacket(buffer.position(), buffer.remaining(), buffer)
    }

    internal fun close() {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { fd?.close() }
        inputStream = null
        outputStream = null
        fd = null
    }
}
